package es.vicai.studylab.material;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Hilo de fondo que vacía la cola de documentos (ADR-32). Al arrancar recupera lo que quedó a medias. Los tests lo apagan
 * ({@code app.material.worker.enabled=false}) y llaman a {@link MaterialWorker#processPending()} de forma síncrona.
 */
@Component
@ConditionalOnProperty(name = "app.material.worker.enabled", havingValue = "true", matchIfMissing = true)
public class MaterialWorkerLoop {

    private static final Logger log = LoggerFactory.getLogger(MaterialWorkerLoop.class);

    private final MaterialWorker worker;
    private final MaterialProperties props;
    private volatile boolean running;
    private Thread thread;

    public MaterialWorkerLoop(MaterialWorker worker, MaterialProperties props) {
        this.worker = worker;
        this.props = props;
    }

    @PostConstruct
    void start() {
        running = true;
        thread = new Thread(this::loop, "material-worker");
        thread.setDaemon(true);
        thread.start();
    }

    @PreDestroy
    void stop() {
        running = false;
        if (thread != null) thread.interrupt();
    }

    private void loop() {
        try {
            worker.recoverInterrupted();
        } catch (RuntimeException e) {
            log.error("No se pudo recuperar la cola de material al arrancar ({})", e.getClass().getSimpleName());
        }
        while (running) {
            try {
                worker.processPending();
                Thread.sleep(props.worker().pollInterval().toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                log.error("Fallo en el procesado de material ({})", e.getClass().getSimpleName());
                try {
                    Thread.sleep(props.worker().pollInterval().toMillis());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
