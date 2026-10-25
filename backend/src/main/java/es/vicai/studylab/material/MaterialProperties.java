package es.vicai.studylab.material;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Límites y parámetros del material propio. Los dos primeros llegan por entorno (MATERIAL_MAX_FILE_MB, MATERIAL_MAX_DOCUMENTS);
 * el resto son valores de ingeniería (extracción acotada, troceado, lotes de embeddings) que solo cambian los tests.
 */
@ConfigurationProperties(prefix = "app.material")
public record MaterialProperties(
        @DefaultValue("10") int maxFileMb,
        @DefaultValue("20") int maxDocuments,
        @DefaultValue("500") int maxPages,
        @DefaultValue("1500000") int maxTextChars,
        @DefaultValue("1000") int maxDocxEntries,
        @DefaultValue("52428800") long maxDocxUncompressedBytes,
        @DefaultValue("1000") int chunkChars,
        @DefaultValue("150") int chunkOverlap,
        @DefaultValue("16") int embedBatch,
        @DefaultValue("60s") Duration extractionTimeout,
        @DefaultValue Worker worker) {

    /** El hilo de fondo que procesa la cola; los tests lo apagan y procesan de forma síncrona. */
    public record Worker(@DefaultValue("true") boolean enabled, @DefaultValue("2s") Duration pollInterval) {}

    public static MaterialProperties defaults() {
        return new MaterialProperties(10, 20, 500, 1_500_000, 1000, 50L * 1024 * 1024, 1000, 150, 16, Duration.ofSeconds(60),
                new Worker(true, Duration.ofSeconds(2)));
    }

    public long maxFileBytes() {
        return maxFileMb * 1024L * 1024L;
    }

    public MaterialProperties withMaxPages(int value) {
        return new MaterialProperties(maxFileMb, maxDocuments, value, maxTextChars, maxDocxEntries, maxDocxUncompressedBytes,
                chunkChars, chunkOverlap, embedBatch, extractionTimeout, worker);
    }

    public MaterialProperties withMaxTextChars(int value) {
        return new MaterialProperties(maxFileMb, maxDocuments, maxPages, value, maxDocxEntries, maxDocxUncompressedBytes,
                chunkChars, chunkOverlap, embedBatch, extractionTimeout, worker);
    }

    public MaterialProperties withMaxDocxEntries(int value) {
        return new MaterialProperties(maxFileMb, maxDocuments, maxPages, maxTextChars, value, maxDocxUncompressedBytes,
                chunkChars, chunkOverlap, embedBatch, extractionTimeout, worker);
    }

    public MaterialProperties withMaxDocxUncompressedBytes(long value) {
        return new MaterialProperties(maxFileMb, maxDocuments, maxPages, maxTextChars, maxDocxEntries, value,
                chunkChars, chunkOverlap, embedBatch, extractionTimeout, worker);
    }

    public MaterialProperties withChunking(int chars, int overlap) {
        return new MaterialProperties(maxFileMb, maxDocuments, maxPages, maxTextChars, maxDocxEntries, maxDocxUncompressedBytes,
                chars, overlap, embedBatch, extractionTimeout, worker);
    }
}
