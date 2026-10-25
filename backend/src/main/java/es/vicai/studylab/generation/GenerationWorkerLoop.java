package es.vicai.studylab.generation;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Hilos de fondo que vacían la cola de cursos (ADR-42). Al arrancar, lo que quedó generándose vuelve a la cola. Cada curso lo reclama un solo hilo
 * (reclamo con {@code SKIP LOCKED} y cerrojo por usuario). Los tests lo apagan ({@code app.generation.worker.enabled=false}) y llaman a
 * {@link CourseGenerator#processPending()} de forma síncrona.
 */
@Component
@ConditionalOnProperty(name = "app.generation.worker.enabled", havingValue = "true", matchIfMissing = true)
public class GenerationWorkerLoop {

    private static final Logger log = LoggerFactory.getLogger(GenerationWorkerLoop.class);

    private final CourseGenerator generator;
    private final GenerationProperties props;
    private final List<Thread> threads = new ArrayList<>();
    private volatile boolean running;

    public GenerationWorkerLoop(CourseGenerator generator, GenerationProperties props) {
        this.generator = generator;
        this.props = props;
    }

    @PostConstruct
    void start() {
        try {
            generator.recoverInterrupted();
        } catch (RuntimeException e) {
            log.error("No se pudo recuperar la cola de cursos al arrancar ({})", e.getClass().getSimpleName());
        }
        running = true;
        for (int i = 1; i <= Math.max(1, props.worker().threads()); i++) {
            Thread thread = new Thread(this::loop, "generation-worker-" + i);
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
                log.error("Fallo en la generación de cursos ({})", e.getClass().getSimpleName());
            }
            try {
                Thread.sleep(props.worker().pollInterval().toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
