package es.vicai.studylab.ai.provider;

import es.vicai.studylab.ai.AiProperties;
import es.vicai.studylab.ai.FinishReason;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
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
        response = new ChatResult("respuesta-falsa", 100, 50, false, FinishReason.STOP);
    }

    public void respondWith(String text, int inputTokens, int outputTokens) {
        response = new ChatResult(text, inputTokens, outputTokens, false, FinishReason.STOP);
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

    // ---------- AiProvider ----------

    @Override
    public ChatResult chat(ChatCall call) {
        calls.add(call);
        pause();
        failIfRequested();
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
