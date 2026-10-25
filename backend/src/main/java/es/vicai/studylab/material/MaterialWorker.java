package es.vicai.studylab.material;

import es.vicai.studylab.ai.AiException;
import es.vicai.studylab.ai.AiErrorKind;
import es.vicai.studylab.ai.AiGateway;
import es.vicai.studylab.ai.AiProperties;
import es.vicai.studylab.ai.AiTask;
import es.vicai.studylab.audit.AuditService;
import es.vicai.studylab.material.chunk.Chunk;
import es.vicai.studylab.material.chunk.Chunker;
import es.vicai.studylab.material.extract.DocxTextExtractor;
import es.vicai.studylab.material.extract.ExtractedText;
import es.vicai.studylab.material.extract.ExtractionError;
import es.vicai.studylab.material.extract.ExtractionException;
import es.vicai.studylab.material.extract.PdfTextExtractor;
import es.vicai.studylab.material.extract.PlainTextExtractor;
import es.vicai.studylab.material.extract.TextExtractor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Procesa la cola de documentos (ADR-32): extrae el texto, lo trocea, pide los embeddings por lotes y guarda todos los fragmentos en una
 * sola transacción. {@link #processPending()} es síncrono (lo usan los tests y el hilo de fondo). Nunca se loguea ni se audita contenido,
 * nombres ni mensajes de librerías: solo ids y un motivo genérico.
 */
@Component
public class MaterialWorker {

    private static final Logger log = LoggerFactory.getLogger(MaterialWorker.class);

    private final MaterialRepository documents;
    private final MaterialProperties props;
    private final AiProperties ai;
    private final AiGateway gateway;
    private final Chunker chunker;
    private final AuditService audit;
    private final TransactionTemplate transaction;
    private final PdfTextExtractor pdf;
    private final DocxTextExtractor docx;
    private final PlainTextExtractor plain;
    private final ExecutorService extraction = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "material-extract");
        thread.setDaemon(true);
        return thread;
    });

    public MaterialWorker(MaterialRepository documents, MaterialProperties props, AiProperties ai, AiGateway gateway, Chunker chunker,
                          AuditService audit, TransactionTemplate transaction, PdfTextExtractor pdf, DocxTextExtractor docx,
                          PlainTextExtractor plain) {
        this.documents = documents;
        this.props = props;
        this.ai = ai;
        this.gateway = gateway;
        this.chunker = chunker;
        this.audit = audit;
        this.transaction = transaction;
        this.pdf = pdf;
        this.docx = docx;
        this.plain = plain;
    }

    /** Procesa todos los documentos pendientes, uno a uno, y devuelve cuántos reclamó. */
    public int processPending() {
        int processed = 0;
        while (processOne()) processed++;
        return processed;
    }

    /** Al arrancar: lo que quedó a medias vuelve a la cola, o acaba en ERROR si ya se intentó demasiadas veces. */
    public void recoverInterrupted() {
        for (UUID id : documents.recoverInterrupted()) {
            log.warn("Documento {} descartado tras varios intentos fallidos", id);
            documents.find(id).ifPresent(d -> audit.record("DOCUMENT_FAILED", AuditService.FAILED, null, d.ownerId(), null,
                    Map.of("documentId", id.toString(), "errorKind", "INDEXING_FAILED")));
        }
    }

    private boolean processOne() {
        MaterialRepository.Claimed claimed = documents.claimNext().orElse(null);
        if (claimed == null) return false;
        try {
            index(claimed);
        } catch (ExtractionException e) {
            fail(claimed, e.kind().name());
        } catch (AiException e) {
            boolean unavailable = e.kind() == AiErrorKind.PROVIDER_UNAVAILABLE || e.kind() == AiErrorKind.PROVIDER_QUOTA;
            fail(claimed, unavailable ? "INDEXING_UNAVAILABLE" : "INDEXING_FAILED");
        } catch (RuntimeException e) {
            // Solo el tipo: el mensaje puede contener texto del documento
            log.error("Fallo inesperado al indexar el documento {} ({})", claimed.id(), e.getClass().getSimpleName());
            if (documents.requeueOrFail(claimed.id())) failAudit(claimed, "INDEXING_FAILED");
        }
        return true;
    }

    private void index(MaterialRepository.Claimed claimed) {
        if (claimed.content() == null) throw new ExtractionException(ExtractionError.CORRUPT);
        ExtractedText extracted = extractWithTimeout(claimed);
        List<Chunk> chunks = chunker.split(extracted);
        if (chunks.isEmpty()) throw new ExtractionException(ExtractionError.NO_TEXT);

        String model = ai.task(AiTask.EMBEDDING).model().trim();
        List<float[]> vectors = new ArrayList<>(chunks.size());
        int batch = Math.max(1, props.embedBatch());
        for (int from = 0; from < chunks.size(); from += batch) {
            List<String> texts = chunks.subList(from, Math.min(chunks.size(), from + batch)).stream().map(Chunk::text).toList();
            vectors.addAll(gateway.embed(claimed.ownerId(), texts).vectors());
        }

        // Una sola transacción: si el documento se borró mientras tanto, no se guarda nada
        Boolean saved = transaction.execute(status -> {
            if (!documents.lockProcessing(claimed.id())) return false;
            for (int i = 0; i < chunks.size(); i++) {
                documents.insertChunk(claimed.id(), claimed.ownerId(), chunks.get(i), vectors.get(i), model);
            }
            documents.markReady(claimed.id(), chunks.size(), extracted.pageCount());
            return true;
        });
        if (Boolean.TRUE.equals(saved)) {
            audit.record("DOCUMENT_READY", AuditService.OK, null, claimed.ownerId(), null,
                    Map.of("documentId", claimed.id().toString(), "mediaType", claimed.type().name(), "chunks", chunks.size()));
        }
    }

    private ExtractedText extractWithTimeout(MaterialRepository.Claimed claimed) {
        TextExtractor extractor = switch (claimed.type()) {
            case PDF -> pdf;
            case DOCX -> docx;
            case TEXT -> plain;
        };
        Future<ExtractedText> task = extraction.submit(() -> extractor.extract(claimed.content()));
        try {
            return task.get(props.extractionTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            task.cancel(true);
            throw new ExtractionException(ExtractionError.TOO_LARGE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            task.cancel(true);
            throw new ExtractionException(ExtractionError.CORRUPT);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ExtractionException extraction) throw extraction;
            throw new ExtractionException(ExtractionError.CORRUPT);
        }
    }

    private void fail(MaterialRepository.Claimed claimed, String errorKind) {
        if (documents.markFailed(claimed.id(), errorKind)) failAudit(claimed, errorKind);
    }

    private void failAudit(MaterialRepository.Claimed claimed, String errorKind) {
        audit.record("DOCUMENT_FAILED", AuditService.FAILED, null, claimed.ownerId(), null,
                Map.of("documentId", claimed.id().toString(), "mediaType", claimed.type().name(), "errorKind", errorKind));
    }
}
