package es.vicai.studylab.guardian;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * AC-8, AC-9 y AC-13 (ADR-59 a 61): reglas sobre el código de {@code guardian/} que ningún test funcional ve. El panel es de solo lectura, cada endpoint comprueba el
 * vínculo, todo SQL es parametrizado y filtra por el alumno (o por el actor en la auditoría) y no se habla con el modelo ni se genera HTML.
 */
class GuardianArchitectureTest extends GuardianTestBase {

    private static final Path GUARDIAN = Path.of("src/main/java/es/vicai/studylab/guardian");
    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

    private static List<Path> sources() throws IOException {
        try (Stream<Path> files = Files.walk(GUARDIAN)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static String all() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Path file : sources()) sb.append(Files.readString(file)).append('\n');
        return sb.toString();
    }

    @Test
    void the_packageExists() throws IOException {
        assertThat(sources()).isNotEmpty();
    }

    @Test
    void ac8_nothingInThePackageMapsAMethodThatWrites() throws IOException {
        for (Path file : sources()) {
            assertThat(Files.readString(file)).as(file.toString()).doesNotContain("@PostMapping").doesNotContain("@PutMapping").doesNotContain("@PatchMapping")
                    .doesNotContain("@DeleteMapping").doesNotContain("@RequestMapping(method");
        }
    }

    @Test
    void ac8_noSqlThatWritesExistsInThePackage() throws IOException {
        for (Path file : sources()) {
            assertThat(Files.readString(file)).as(file.toString()).doesNotContainPattern("(?i)\\b(INSERT\\s+INTO|UPDATE\\s+\\w+\\s+SET|DELETE\\s+FROM|TRUNCATE|ALTER\\s+TABLE|DROP\\s+TABLE)\\b");
        }
    }

    @Test
    void ac8_everyControllerMethodChecksTheLinkWithAccessGuard() throws IOException {
        for (Path file : sources()) {
            String code = Files.readString(file);
            if (!code.contains("@RestController")) continue;
            Matcher mappings = Pattern.compile("@GetMapping").matcher(code);
            int count = 0;
            while (mappings.find()) count++;
            Matcher guards = Pattern.compile("requireStudentOf\\(").matcher(code);
            int checks = 0;
            while (guards.find()) checks++;
            assertThat(count).as("endpoints de " + file).isGreaterThanOrEqualTo(6);
            assertThat(checks).as("comprobaciones del vínculo en " + file).isGreaterThanOrEqualTo(count);
        }
    }

    @Test
    void ac8_noSqlIsBuiltByConcatenatingStrings() throws IOException {
        for (Path file : sources()) {
            assertThat(Files.readString(file)).as(file.toString()).doesNotContainPattern("sql\\(\\s*\"[^\"]*\"\\s*\\+").doesNotContainPattern("sql\\(\\s*[a-zA-Z_]+\\s*\\+");
        }
    }

    @Test
    void ac8_everyQueryOnTheStudentsDataFiltersByOwner() throws IOException {
        for (Path file : sources()) {
            String code = Files.readString(file);
            Matcher m = Pattern.compile("(FROM|JOIN)\\s+(tutor_message|course_piece|exercise_attempt|exam_attempt|course_intake|course_topic|course_chapter|lesson_progress|ai_usage)\\b",
                    Pattern.CASE_INSENSITIVE).matcher(code);
            while (m.find()) {
                String statement = code.substring(m.start(), Math.min(code.length(), m.start() + 600));
                int end = statement.indexOf(".query(");
                String scoped = end > 0 ? statement.substring(0, end) : statement;
                assertThat(scoped).as(file + " consulta " + m.group(2) + " sin filtrar por el alumno").containsIgnoringCase(m.group(2).equals("ai_usage") ? "user_id" : "owner_id");
            }
        }
    }

    @Test
    void ac8_theAuditIsReadByActorAndNeverWithoutAFilter() throws IOException {
        Matcher m = Pattern.compile("FROM\\s+audit_event\\b", Pattern.CASE_INSENSITIVE).matcher(all());
        String code = all();
        while (m.find()) {
            String statement = code.substring(m.start(), Math.min(code.length(), m.start() + 400));
            int end = statement.indexOf(".query(");
            assertThat(end > 0 ? statement.substring(0, end) : statement).containsIgnoringCase("actor_user_id");
        }
    }

    @Test
    void ac13_thePanelNeverTalksToTheModelNorProducesHtml() throws IOException {
        assertThat(all()).doesNotContain("AiGateway").doesNotContain("studylab.ai.provider").doesNotContain("PromptAssembler").doesNotContain("MaterialSearch").doesNotContain("text/html");
    }

    @Test
    void ac9_theViewsAreAuditedAndNothingElseIsWrittenByTheService() throws IOException {
        assertThat(all()).contains("GUARDIAN_VIEW").contains("audit.record(");
    }

    @Test
    void ac8_theMigrationOnlyAddsTheIndexForTheAuditByActor() throws IOException {
        Path file = MIGRATIONS.resolve("V8__guardian.sql");
        assertThat(file).exists();
        String sql = Files.readString(file);

        assertThat(sql).containsIgnoringCase("CREATE INDEX audit_event_actor_idx ON audit_event (actor_user_id, action, occurred_at)");
        assertThat(sql).doesNotContainIgnoringCase("CREATE TABLE").doesNotContainIgnoringCase("DROP ").doesNotContainIgnoringCase("GRANT");
    }
}
