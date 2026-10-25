package es.vicai.studylab.ai.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import es.vicai.studylab.ai.AiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/** ADR-24: el benchmark gasta dinero real; jamás debe poder ejecutarse en el arranque normal de la aplicación. */
class NoBenchmarkInNormalRunTest extends AiTestBase {

    @Autowired ApplicationContext context;

    @Test
    void ac17_withoutTheBenchmarkProfileThereIsNoRunnerAndNoLauncher() {
        assertThat(context.getBeanNamesForType(BenchmarkRunner.class)).isEmpty();
        assertThat(context.getBeanNamesForType(org.springframework.boot.CommandLineRunner.class))
                .noneMatch(name -> name.toLowerCase().contains("benchmark"));
    }
}
