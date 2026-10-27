package es.vicai.studylab.illustrations;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * El hilo de fondo de las ilustraciones (ADR-79): descubre los temas sin imagen y los va generando. Es el único sitio de este paquete que crea hilos. Los tests lo
 * apagan ({@code app.illustrations.worker.enabled=false}) y llaman a {@link IllustrationGenerator#processPending()} de forma síncrona.
 */
@Component
@ConditionalOnProperty(name = "app.illustrations.worker.enabled", havingValue = "true", matchIfMissing = true)
public class IllustrationWorkerLoop {

    private static final Logger log = LoggerFactory.getLogger(IllustrationWorkerLoop.class);

    private final IllustrationGenerator generator;
    private final IllustrationProperties props;
    private final List<Thread> threads = new ArrayList<>();
    private volatile boolean running;

    public IllustrationWorkerLoop(IllustrationGenerator generator, IllustrationProperties props) {
        this.generator = generator;
        this.props = props;
    }

    @PostConstruct
    void start() {
        running = true;
        for (int i = 1; i <= Math.max(1, props.worker().threads()); i++) {
            Thread thread = new Thread(this::loop, "illustration-worker-" + i);
            thread.setDaemon(true);
            threads.add(thread);
            thread.start();
        }
    }

    @PreDestroy
    void stop() {
        running = false;
        threads.forEach(Thread::interrupt);
    }

    private void loop() {
        while (running) {
            try {
                generator.processPending();
            } catch (RuntimeException e) {
                log.error("Fallo en la generación de ilustraciones ({})", e.getClass().getSimpleName());
            }
            try {
                Thread.sleep(props.worker().poll().toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
