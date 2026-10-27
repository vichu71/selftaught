package es.vicai.studylab.ai.provider;

import es.vicai.studylab.ai.AiProperties;
import es.vicai.studylab.ai.FinishReason;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * Proveedor falso y determinista: el predeterminado en desarrollo y el único posible en los tests (nunca se llama a una API
 * de pago). Los métodos de abajo son ganchos de prueba: respuesta fija, fallos inyectables, latencia y dimensión de los vectores.
 * No está permitido en producción (lo impide la validación de arranque).
 */
@Component
public class FakeProvider implements AiProvider {

    private final AiProperties props;
    private final List<ChatCall> calls = new CopyOnWriteArrayList<>();
    private final List<EmbedCall> embedCalls = new CopyOnWriteArrayList<>();
    private final AtomicInteger failuresLeft = new AtomicInteger();
    private volatile ProviderErrorKind failKind = ProviderErrorKind.SERVER_ERROR;
    private volatile String failMessage = "fallo simulado";
    private volatile ChatResult response;
    private volatile Duration delay = Duration.ZERO;
    private volatile Integer dimensions;
    private volatile List<String> sequence;
    private final AtomicInteger sequenceIndex = new AtomicInteger();
    private volatile Function<ChatCall, String> responder;

    public FakeProvider(AiProperties props) {
        this.props = props;
        reset();
    }

    @Override
    public String name() {
        return "fake";
    }

    // ---------- ganchos de prueba ----------

    public void reset() {
        calls.clear();
        embedCalls.clear();
        failuresLeft.set(0);
        delay = Duration.ZERO;
        dimensions = null;
        sequence = null;
        responder = null;
        sequenceIndex.set(0);
        response = new ChatResult("respuesta-falsa", 100, 50, false, FinishReason.STOP);
        imageCalls.clear();
        imageFailuresLeft.set(0);
        imageFailKind = ProviderErrorKind.SERVER_ERROR;
        imageFailMessage = "fallo simulado";
        imageModerated = false;
        imageBytes = null;
    }

    public void respondWith(String text, int inputTokens, int outputTokens) {
        sequence = null;
        responder = null;
        response = new ChatResult(text, inputTokens, outputTokens, false, FinishReason.STOP);
    }

    /** Una respuesta distinta por llamada, en orden; cuando se acaban, se repite la última. Para probar reintentos (F04). */
    public void respondWithSequence(String... texts) {
        sequenceIndex.set(0);
        responder = null;
        sequence = List.of(texts);
    }

    /** La respuesta depende de la petición (F05: según la pieza que se pide). Sustituye a las demás hasta el siguiente {@code reset()}. */
    public void respondUsing(Function<ChatCall, String> responder) {
        sequence = null;
        this.responder = responder;
    }

    public void respondWithoutUsage(String text) {
        response = new ChatResult(text, 0, 0, true, FinishReason.STOP);
    }

    public void respondWithFinish(String text, FinishReason finish) {
        response = new ChatResult(text, 100, 50, false, finish);
    }

    public void failNext(ProviderErrorKind kind, int times) {
        failNextWithMessage(kind, times, "fallo simulado");
    }

    public void failNextWithMessage(ProviderErrorKind kind, int times, String message) {
        failKind = kind;
        failMessage = message;
        failuresLeft.set(times);
    }

    public void delay(Duration duration) {
        delay = duration;
    }

    public void embeddingDimensions(int dimensions) {
        this.dimensions = dimensions;
    }

    /** Todas las llamadas de chat recibidas, también las que se hicieron fallar. */
    public List<ChatCall> calls() {
        return new ArrayList<>(calls);
    }

    public List<EmbedCall> embedCalls() {
        return new ArrayList<>(embedCalls);
    }

    // ---------- ganchos de prueba de la imagen (F12) ----------

    private final List<ImageCall> imageCalls = new CopyOnWriteArrayList<>();
    private final AtomicInteger imageFailuresLeft = new AtomicInteger();
    private volatile ProviderErrorKind imageFailKind = ProviderErrorKind.SERVER_ERROR;
    private volatile String imageFailMessage = "fallo simulado";
    private volatile boolean imageModerated;
    private volatile byte[] imageBytes;

    /** Todas las llamadas de imagen recibidas, también las que se hicieron fallar. */
    public List<ImageCall> imageCalls() {
        return new ArrayList<>(imageCalls);
    }

    /** La imagen que devuelve (por defecto, un PNG de 512×512 generado en memoria). Una respuesta vacía simula un proveedor que cobra y no devuelve nada. */
    public void imageRespondsWith(byte[] bytes) {
        imageBytes = bytes;
    }

    /** Las siguientes {@code times} llamadas de imagen fallan con ese tipo de error (0 lo cancela). */
    public void imageFailsTimes(int times, ProviderErrorKind kind) {
        imageFailKind = kind;
        imageFailuresLeft.set(times);
    }

    public void imageFailMessage(String message) {
        imageFailMessage = message;
    }

    /** Toda llamada de imagen es rechazada por la moderación del proveedor. */
    public void imageIsModerated() {
        imageModerated = true;
    }

    /** Un PNG válido de {@code width}×{@code height} generado en memoria (un degradado: ocupa poco). */
    public static byte[] png(int width, int height) {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) image.setRGB(x, y, ((x * 255 / Math.max(1, width)) << 16) | ((y * 255 / Math.max(1, height)) << 8) | 0x60);
        }
        try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            javax.imageio.ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Un PNG que solo tiene la firma, la cabecera con ese tamaño y el final: sirve para probar que se rechaza una bomba de píxeles sin decodificarla. */
    public static byte[] pngDeclaring(int width, int height) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        java.nio.ByteBuffer header = java.nio.ByteBuffer.allocate(13);
        header.putInt(width).putInt(height).put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0);
        chunk(out, "IHDR", header.array());
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void chunk(java.io.ByteArrayOutputStream out, String type, byte[] data) {
        byte[] name = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(name);
        crc.update(data);
        out.writeBytes(java.nio.ByteBuffer.allocate(4).putInt(data.length).array());
        out.writeBytes(name);
        out.writeBytes(data);
        out.writeBytes(java.nio.ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }

    // ---------- AiProvider ----------

    @Override
    public ImageResult image(ImageCall call) {
        imageCalls.add(call);
        pause();
        if (imageModerated) throw new ProviderException(ProviderErrorKind.MODERATION, imageFailMessage);
        if (imageFailuresLeft.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) throw new ProviderException(imageFailKind, imageFailMessage);
        byte[] bytes = imageBytes;
        return new ImageResult(bytes != null ? bytes : png(512, 512), "image/png");
    }

    @Override
    public ChatResult chat(ChatCall call) {
        calls.add(call);
        pause();
        failIfRequested();
        Function<ChatCall, String> dynamic = responder;
        if (dynamic != null) return new ChatResult(dynamic.apply(call), 100, 50, false, FinishReason.STOP);
        List<String> texts = sequence;
        if (texts != null && !texts.isEmpty()) {
            String next = texts.get(Math.min(sequenceIndex.getAndIncrement(), texts.size() - 1));
            return new ChatResult(next, 100, 50, false, FinishReason.STOP);
        }
        return response;
    }

    @Override
    public EmbedResult embed(EmbedCall call) {
        embedCalls.add(call);
        pause();
        failIfRequested();
        int size = dimensions != null ? dimensions : props.embedding().dimensions();
        List<float[]> vectors = new ArrayList<>();
        int tokens = 0;
        for (String text : call.inputs()) {
            float[] vector = new float[size];
            for (int i = 0; i < size; i++) vector[i] = Math.floorMod(text.hashCode() + i, 100) / 100f;
            vectors.add(vector);
            tokens += text.length() / 4 + 1;
        }
        return new EmbedResult(vectors, tokens);
    }

    private void failIfRequested() {
        if (failuresLeft.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
            throw new ProviderException(failKind, failMessage);
        }
    }

    private void pause() {
        if (delay.isZero()) return;
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String toString() {
        return "FakeProvider";
    }
}
