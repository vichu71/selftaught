package es.vicai.studylab.ai.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import es.vicai.studylab.ai.AiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/** F12 ADR-83: el benchmark de imágenes gasta dinero real; jamás debe poder ejecutarse en el arranque normal ni desde un test sin su perfil. */
class NoImageBenchmarkInNormalRunTest extends AiTestBase {

    @Autowired ApplicationContext context;

    @Test
    void ac17_withoutTheImageBenchmarkProfileThereIsNoRunnerAndNoLauncher() {
        assertThat(context.getBeanNamesForType(ImageBenchmarkRunner.class)).isEmpty();
        assertThat(context.getBeanNamesForType(org.springframework.boot.CommandLineRunner.class)).noneMatch(name -> name.toLowerCase().contains("imagebenchmark"));
    }
}
