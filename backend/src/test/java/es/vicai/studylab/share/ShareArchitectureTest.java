package es.vicai.studylab.share;

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
 * AC-8 a AC-15 (ADR-63 a 66): reglas sobre el código de {@code courseedit/} y {@code share/} que ningún test funcional ve. La edición nunca habla con el modelo (lo nuevo lo
 * genera la cola de F05), el código se genera con una fuente segura y solo se guarda su hash, la copia nunca toca lo que no debe viajar y todo SQL es parametrizado y por propietario.
 */
class ShareArchitectureTest extends ShareTestBase {

    private static final Path EDIT = Path.of("src/main/java/es/vicai/studylab/courseedit");
    private static final Path SHARE = Path.of("src/main/java/es/vicai/studylab/share");
    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

    private static List<Path> sources(Path dir) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static String all(Path dir) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Path file : sources(dir)) sb.append(Files.readString(file)).append('\n');
        return sb.toString();
    }

    @Test
    void thePackagesExist() throws IOException {
        assertThat(sources(EDIT)).isNotEmpty();
        assertThat(sources(SHARE)).isNotEmpty();
    }

    @Test
    void ac2_theEditorNeverTalksToTheModelNorTakesTheGenerationLockItJustQueuesTheCourse() throws IOException {
        for (Path dir : List.of(EDIT, SHARE)) {
            assertThat(all(dir)).as(dir.toString()).doesNotContain("AiGateway").doesNotContain("studylab.ai.provider").doesNotContain("PromptAssembler").doesNotContain("MaterialSearch")
                    .doesNotContain("GenerationLock").doesNotContain("text/html");
        }
    }

    @Test
    void ac6_everyChangeOfTheOutlineLocksTheCourseRowAndChecksItsState() throws IOException {
        String code = all(EDIT);

        assertThat(code).containsIgnoringCase("FOR UPDATE").contains("COURSE_BUSY").contains("PENDING_GENERATION").contains("GENERATING");
    }

    @Test
    void ac13_whatTheUserWritesPassesThroughTheMinorFilter() throws IOException {
        assertThat(all(EDIT)).contains("ContentGuard").contains("localCheck").contains("COURSE_EDIT_BLOCKED");
        assertThat(all(SHARE)).contains("ContentGuard").contains("localCheck").contains("COURSE_COPY_BLOCKED");
    }

    @Test
    void ac8_theCodeComesFromASecureSourceIsLongEnoughAndOnlyItsHashIsStored() throws IOException {
        String code = all(SHARE);

        assertThat(code).contains("SecureRandom").contains("SHA-256").doesNotContain("java.util.Random").doesNotContain("Math.random").doesNotContain("ThreadLocalRandom");
        assertThat(code).doesNotContainPattern("(?i)INSERT\\s+INTO\\s+share_code\\s*\\([^)]*\\bcode\\b[^_]");
        assertThat(code).doesNotContainPattern("(?i)SELECT[^;]*\\bshare_code\\.code\\b");
    }

    @Test
    void ac12_theOnlyReadOfAForeignCourseIsTheRedemptionByHashAndItChecksExpiryAndRevocationInTheSameStatement() throws IOException {
        String code = all(SHARE);
        Matcher m = Pattern.compile("FROM\\s+share_code\\b", Pattern.CASE_INSENSITIVE).matcher(code);
        int byHash = 0;
        while (m.find()) {
            String statement = code.substring(m.start(), Math.min(code.length(), m.start() + 500));
            int end = statement.indexOf(".query(");
            String scoped = end > 0 ? statement.substring(0, end) : statement;
            if (scoped.contains("code_hash")) {
                byHash++;
                assertThat(scoped).containsIgnoringCase("expires_at").containsIgnoringCase("revoked_at");
            } else {
                assertThat(scoped).as("consulta de códigos sin hash ni propietario").containsIgnoringCase("owner_id");
            }
        }
        assertThat(byHash).isEqualTo(1);
    }

    @Test
    void ac11_theCopyNeverReadsOrWritesWhatMustNotTravel() throws IOException {
        String copier = Files.readString(findCopier());

        for (String forbidden : new String[] {"lesson_progress", "exercise_attempt", "exam_attempt", "tutor_message", "ai_usage", "course_document", "source_document", "source_chunk",
                "course_intake WHERE", "FROM audit_event"}) {
            assertThat(copier).as("el copiador no debe tocar " + forbidden).doesNotContain(forbidden);
        }
        assertThat(copier).contains("origin_id").contains("origin_version");
    }

    private static Path findCopier() throws IOException {
        return sources(SHARE).stream().filter(p -> p.getFileName().toString().equals("CourseCopier.java")).findFirst().orElseThrow();
    }

    @Test
    void ac14_everyQueryOnTheUsersCourseDataFiltersByOwner() throws IOException {
        for (Path dir : List.of(EDIT, SHARE)) {
            for (Path file : sources(dir)) {
                String code = Files.readString(file);
                Matcher m = Pattern.compile("(FROM|JOIN|UPDATE|INTO)\\s+(course_topic|course_chapter|course_piece|course_action|lesson_progress|exercise_attempt|exam_attempt|tutor_message)\\b",
                        Pattern.CASE_INSENSITIVE).matcher(code);
                while (m.find()) {
                    String statement = code.substring(m.start(), Math.min(code.length(), m.start() + 600));
                    int end = statement.indexOf(".update(") >= 0 && (statement.indexOf(".query(") < 0 || statement.indexOf(".update(") < statement.indexOf(".query("))
                            ? statement.indexOf(".update(") : statement.indexOf(".query(");
                    String scoped = end > 0 ? statement.substring(0, end) : statement;
                    String column = m.group(2).equals("course_action") ? "user_id" : "owner_id";
                    assertThat(scoped).as(file + " usa " + m.group(2) + " sin filtrar por " + column).containsIgnoringCase(column);
                }
            }
        }
    }

    @Test
    void ac14_noSqlIsBuiltByConcatenatingStrings() throws IOException {
        for (Path dir : List.of(EDIT, SHARE)) {
            for (Path file : sources(dir)) {
                assertThat(Files.readString(file)).as(file.toString()).doesNotContainPattern("sql\\(\\s*\"[^\"]*\"\\s*\\+").doesNotContainPattern("sql\\(\\s*[a-zA-Z_]+\\s*\\+");
            }
        }
    }

    @Test
    void ac14_everyControllerMethodThatChangesDataIsAMutationAndNoneIsAGetThatWrites() throws IOException {
        for (Path file : sources(EDIT)) {
            String code = Files.readString(file);
            if (code.contains("@RestController")) assertThat(code).doesNotContain("@GetMapping");
        }
        String share = all(SHARE);
        assertThat(share).contains("@PostMapping").contains("@DeleteMapping").contains("@GetMapping");
    }

    @Test
    void ac8_theMigrationCreatesTheTablesColumnsAndGrantsOfTheProposal() throws IOException {
        Path file = MIGRATIONS.resolve("V9__share_edit.sql");
        assertThat(file).exists();
        String sql = Files.readString(file).toLowerCase();

        assertThat(sql).contains("create table share_code").contains("code_hash").contains("unique").contains("create table course_action").contains("version")
                .contains("origin_id").contains("origin_version").contains("outdated").contains("on delete set null").contains("grant");
        assertThat(sql).doesNotContain("drop table");
    }
}
