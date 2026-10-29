package es.vicai.studylab.material;

import es.vicai.studylab.ai.AiException;
import es.vicai.studylab.ai.AiErrorKind;
import es.vicai.studylab.ai.AiGateway;
import es.vicai.studylab.ai.AiProperties;
import es.vicai.studylab.ai.AiReadRequest;
import es.vicai.studylab.ai.AiReadResponse;
import es.vicai.studylab.ai.AiTask;
import es.vicai.studylab.ai.lock.GenerationBusyException;
import es.vicai.studylab.ai.lock.GenerationLock;
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
import es.vicai.studylab.material.image.ImageReadParser;
import es.vicai.studylab.material.image.PhotoProcessor;
import es.vicai.studylab.material.image.PhotoRejectedException;
import es.vicai.studylab.safety.ContentGuard;
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
    private final PhotoProcessor photos;
    private final GenerationLock locks;
    private final ContentGuard guard;
    private final ExecutorService extraction = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "material-extract");
        thread.setDaemon(true);
        return thread;
    });

    public MaterialWorker(MaterialRepository documents, MaterialProperties props, AiProperties ai, AiGateway gateway, Chunker chunker,
                          AuditService audit, TransactionTemplate transaction, PdfTextExtractor pdf, DocxTextExtractor docx,
                          PlainTextExtractor plain, PhotoProcessor photos, GenerationLock locks, ContentGuard guard) {
        this.photos = photos;
        this.locks = locks;
        this.guard = guard;
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
        java.util.Set<UUID> waiting = new java.util.HashSet<>(); // fotos con el cerrojo de su usuario ocupado: esperan a la siguiente ronda
        while (processOne(waiting)) processed++;
        return processed;
    }

    /** Al arrancar: lo que quedó a medias vuelve a la cola, o acaba en ERROR si ya se intentó demasiadas veces. */
    public void recoverInterrupted() {
        for (UUID id : documents.recoverInterrupted()) {
            log.warn("Documento {} descartado tras un reinicio o varios intentos fallidos", id);
            documents.find(id).ifPresent(d -> audit.record("DOCUMENT_FAILED", AuditService.FAILED, null, d.ownerId(), null,
                    Map.of("documentId", id.toString(), "mediaType", d.type().name(), "errorKind", d.errorKind() == null ? "INDEXING_FAILED" : d.errorKind())));
        }
    }

    private boolean processOne(java.util.Set<UUID> waiting) {
        MaterialRepository.Claimed claimed = documents.claimNext(waiting).orElse(null);
        if (claimed == null) return false;
        try {
            if (claimed.type() == MaterialType.IMAGE) processPhoto(claimed, waiting);
            else index(claimed);
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
        save(claimed, chunks, extracted.pageCount());
    }

    /** Embeddings por lotes y todos los fragmentos en una sola transacción: si el documento se borró mientras tanto, no se guarda nada. */
    private void save(MaterialRepository.Claimed claimed, List<Chunk> chunks, Integer pageCount) {
        String model = ai.task(AiTask.EMBEDDING).model().trim();
        List<float[]> vectors = new ArrayList<>(chunks.size());
        int batch = Math.max(1, props.embedBatch());
        for (int from = 0; from < chunks.size(); from += batch) {
            List<String> texts = chunks.subList(from, Math.min(chunks.size(), from + batch)).stream().map(Chunk::text).toList();
            vectors.addAll(gateway.embed(claimed.ownerId(), texts).vectors());
        }

        Boolean saved = transaction.execute(status -> {
            if (!documents.lockProcessing(claimed.id())) return false;
            for (int i = 0; i < chunks.size(); i++) {
                documents.insertChunk(claimed.id(), claimed.ownerId(), chunks.get(i), vectors.get(i), model);
            }
            documents.markReady(claimed.id(), chunks.size(), pageCount);
            return true;
        });
        if (Boolean.TRUE.equals(saved)) {
            audit.record("DOCUMENT_READY", AuditService.OK, null, claimed.ownerId(), null,
                    Map.of("documentId", claimed.id().toString(), "mediaType", claimed.type().name(), "chunks", chunks.size()));
        }
    }

    // ---------- fotos (F19, ADR-100 a 104) ----------

    /**
     * Una foto: se re-codifica, se toma el cerrojo del usuario (una llamada de IA a la vez), se lee con el modelo de visión UNA sola vez y el texto leído sigue el mismo camino
     * que cualquier otro (fragmentos, embeddings, una transacción). La lectura es de pago: ningún fallo posterior a ella la repite (ADR-102) y el texto no se guarda entre intentos.
     */
    private void processPhoto(MaterialRepository.Claimed claimed, java.util.Set<UUID> waiting) {
        if (claimed.content() == null) throw new ExtractionException(ExtractionError.CORRUPT);
        if (!gateway.readEnabled()) { // las fotos se desactivaron con esta pendiente
            fail(claimed, "READ_FAILED");
            return;
        }
        PhotoProcessor.Prepared prepared;
        try {
            prepared = photos.prepare(claimed.content());
        } catch (PhotoRejectedException e) {
            fail(claimed, e.kind());
            return;
        }
        boolean minor = documents.isMinor(claimed.ownerId());

        AiReadResponse response;
        try (GenerationLock.Lease lease = locks.acquire(claimed.ownerId())) {
            if (!lease.renew()) { // el cerrojo ya no es nuestro: no se llama ni se escribe nada
                documents.releaseBusy(claimed.id());
                waiting.add(claimed.id());
                return;
            }
            response = gateway.readImage(AiReadRequest.of(claimed.ownerId(), prepared.bytes(), "image/jpeg", minor ? "menor" : "adulto"));
        } catch (GenerationBusyException e) {
            documents.releaseBusy(claimed.id()); // espera su turno sin gastar el intento
            waiting.add(claimed.id());
            return;
        } catch (AiException e) {
            boolean budget = e.kind() == AiErrorKind.USER_LIMIT || e.kind() == AiErrorKind.BUDGET_BLOCKED;
            fail(claimed, budget ? "BUDGET" : "READ_FAILED");
            return;
        }

        var read = ImageReadParser.parse(response.raw(), props.maxTextChars());
        if (read.isEmpty()) { // la llamada se cobró pero el sobre no se entiende
            fail(claimed, "READ_FAILED");
            return;
        }
        String text = read.get().text();
        if (minor && !read.get().appropriate()) {
            block(claimed);
            return;
        }
        if (text.isBlank()) {
            fail(claimed, ExtractionError.NO_TEXT.name());
            return;
        }
        if (minor && guard.localCheck(text).isPresent()) {
            block(claimed);
            return;
        }

        List<Chunk> chunks = chunker.split(new ExtractedText(text, null, null));
        if (chunks.isEmpty()) {
            fail(claimed, ExtractionError.NO_TEXT.name());
            return;
        }
        try {
            save(claimed, chunks, null);
        } catch (AiException e) {
            fail(claimed, e.kind() == AiErrorKind.PROVIDER_UNAVAILABLE || e.kind() == AiErrorKind.PROVIDER_QUOTA ? "INDEXING_UNAVAILABLE" : "INDEXING_FAILED");
        }
    }

    /** Foto de un menor que no pasa el filtro: nada se indexa; el aviso al tutor lleva solo ids (ADR-104). El motivo nunca llega al menor. */
    private void block(MaterialRepository.Claimed claimed) {
        if (documents.markFailed(claimed.id(), "BLOCKED")) {
            audit.record("DOCUMENT_BLOCKED", AuditService.DENIED, claimed.ownerId(), claimed.ownerId(), null,
                    Map.of("documentId", claimed.id().toString(), "mediaType", claimed.type().name()));
        }
    }

    private ExtractedText extractWithTimeout(MaterialRepository.Claimed claimed) {
        TextExtractor extractor = switch (claimed.type()) {
            case PDF -> pdf;
            case DOCX -> docx;
            case TEXT -> plain;
            case IMAGE -> throw new IllegalStateException("Una foto no pasa por un extractor de texto"); // la rama de fotos es processPhoto
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
