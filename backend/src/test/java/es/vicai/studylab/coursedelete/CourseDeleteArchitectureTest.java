package es.vicai.studylab.coursedelete;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** AC-2, AC-4, AC-8 (ADR-84 a 86): reglas sobre el código de {@code coursedelete/} y su migración que ningún test funcional ve. */
class CourseDeleteArchitectureTest extends CourseDeleteTestBase {

    private static final Path DELETE = Path.of("src/main/java/es/vicai/studylab/coursedelete");
    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

    private static String all() throws IOException {
        StringBuilder sb = new StringBuilder();
        try (Stream<Path> files = Files.walk(DELETE)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) sb.append(Files.readString(file)).append('\n');
        }
        return sb.toString();
    }

    @Test
    void thePackageExists() throws IOException {
        assertThat(all()).isNotBlank();
    }

    @Test
    void theDeleterNeverTalksToTheModelNorTakesTheGenerationLock() throws IOException {
        assertThat(all()).doesNotContain("AiGateway").doesNotContain("studylab.ai.provider").doesNotContain("MaterialSearch").doesNotContain("GenerationLock").doesNotContain("text/html");
    }

    @Test
    void ac4_itLocksTheRowsInIdOrderAndChecksTheState() throws IOException {
        assertThat(all()).containsIgnoringCase("FOR UPDATE").containsIgnoringCase("ORDER BY id").contains("COURSE_BUSY").contains("PENDING_GENERATION").contains("GENERATING")
                .contains("CHILD_CANNOT_DELETE").contains("COURSE_DELETED");
    }

    @Test
    void ac2_everyQueryOnCoursesOrIntakesFiltersByOwnerAndIsParameterized() throws IOException {
        String code = all();
        Matcher m = Pattern.compile("(FROM|UPDATE)\\s+(course|course_intake)\\b", Pattern.CASE_INSENSITIVE).matcher(code);
        int found = 0;
        while (m.find()) {
            found++;
            String statement = code.substring(m.start(), Math.min(code.length(), m.start() + 400));
            int end = statement.indexOf(".query(");
            String scoped = end > 0 ? statement.substring(0, end) : statement;
            assertThat(scoped).as("consulta sobre " + m.group(2) + " sin propietario").containsIgnoringCase("owner_id");
        }
        assertThat(found).isPositive();
        assertThat(code).doesNotContainPattern("sql\\(\\s*\"[^\"]*\"\\s*\\+").doesNotContainPattern("sql\\(\\s*[a-zA-Z_]+\\s*\\+");
        assertThat(code).doesNotContainIgnoringCase("DELETE FROM ai_usage");
    }

    @Test
    void theMigrationOnlyAddsTheDeleteGrantOnCourse() throws IOException {
        Path migration = MIGRATIONS.resolve("V11__course_delete.sql");
        assertThat(migration).exists();
        String sql = Files.readString(migration);

        assertThat(sql).containsPattern("(?i)GRANT\\s+DELETE\\s+ON\\s+course\\s+TO\\s+studylab_app");
        assertThat(sql).doesNotContainIgnoringCase("ai_usage").doesNotContainIgnoringCase("DROP ").doesNotContainIgnoringCase("ALTER TABLE");
    }

    @Test
    void ac8_logsNeverContainTitlesOrTopicsOfTheDeletedCourses() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        Level previous = root.getLevel();
        root.setLevel(Level.DEBUG);
        try {
            String ana = logged("ana@example.com");
            UUID one = titled("ana@example.com", UNIQUE_TITLE);
            UUID two = titled("ana@example.com", UNIQUE_TITLE + "-2");
            deleteMany(ana, List.of(one, two)).andReturn();
            deleteOne(ana, one).andReturn(); // el segundo intento (404) tampoco debe volcar nada
        } finally {
            root.detachAppender(appender);
            root.setLevel(previous);
        }

        String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
        assertThat(logs).doesNotContain(UNIQUE_TITLE);
    }
}
