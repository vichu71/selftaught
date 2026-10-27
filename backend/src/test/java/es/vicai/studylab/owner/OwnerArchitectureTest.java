package es.vicai.studylab.owner;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** AC-2, AC-4, AC-9, AC-10, AC-15 (ADR-87 a 91): reglas sobre el código de {@code owner/} que ningún test funcional ve. */
class OwnerArchitectureTest extends OwnerTestBase {

    private static final Path OWNER = Path.of("src/main/java/es/vicai/studylab/owner");
    private static final Path MAIN = Path.of("src/main/java/es/vicai/studylab");
    /** Las únicas clases que escriben: las dos acciones con su transacción y el límite de frecuencia. */
    private static final Set<String> WRITERS = Set.of("OwnerUserRepository.java", "OwnerRateLimiter.java");
    private static final Pattern WRITE_SQL = Pattern.compile("(?i)\\b(INSERT\\s+INTO|UPDATE\\s+\\w+\\s+SET|DELETE\\s+FROM|TRUNCATE|ALTER\\s+TABLE|DROP\\s+TABLE)\\b");
    /** Columnas de contenido de personas: nunca aparecen en una consulta del panel. */
    private static final Pattern CONTENT = Pattern.compile("(?i)\\b(title|topic|summary|objective|filename|file_name|question|answer|answers|content|markdown|body|prompt|sheet|solution|original|password_hash|token_hash|code_hash)\\b"
            + "|(?<!::)(?<!as )\\btext\\b");

    private static List<Path> sources() throws IOException {
        try (Stream<Path> files = Files.walk(OWNER)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static String all() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Path file : sources()) sb.append(Files.readString(file)).append('\n');
        return sb.toString();
    }

    /** Los literales de cadena y los bloques de texto: ahí viven el SQL y los mensajes, no los comentarios. */
    private static List<String> literals(String code) {
        List<String> found = new ArrayList<>();
        Matcher blocks = Pattern.compile("\"\"\"(.*?)\"\"\"", Pattern.DOTALL).matcher(code);
        while (blocks.find()) found.add(blocks.group(1));
        String rest = blocks.replaceAll("");
        Matcher strings = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"").matcher(rest);
        while (strings.find()) found.add(strings.group(1));
        return found;
    }

    @Test
    void thePackageExists() throws IOException {
        assertThat(sources()).isNotEmpty();
        assertThat(sources().stream().map(p -> p.getFileName().toString())).contains("OwnerGuard.java", "OwnerPolicy.java", "OwnerRateLimiter.java", "OwnerStatsRepository.java", "OwnerUserService.java");
    }

    @Test
    void theOwnerPanelNeverTalksToTheModelNorReadsMaterialNorTakesTheGenerationLock() throws IOException {
        assertThat(all()).doesNotContain("AiGateway").doesNotContain("studylab.ai.provider").doesNotContain("MaterialSearch").doesNotContain("GenerationLock").doesNotContain("text/html");
    }

    @Test
    void ac2_everyHandlerOfEveryControllerStartsByAskingTheGuardAndTheRateLimiter() throws IOException {
        int handlers = 0;
        for (Path file : sources()) {
            String name = file.getFileName().toString();
            if (!name.endsWith("Controller.java")) continue;
            String code = Files.readString(file);
            Matcher mapping = Pattern.compile("@(Get|Post|Put|Delete|Patch|Request)Mapping").matcher(code);
            int mappings = 0;
            while (mapping.find()) mappings++;
            handlers += mappings;
            int guards = code.split("guard\\.require\\(", -1).length - 1;
            assertThat(guards).as(name + ": cada endpoint llama a guard.require").isGreaterThanOrEqualTo(mappings);
            assertThat(code).as(name).contains("OwnerGuard").contains("OwnerRateLimiter").contains("@AuthenticationPrincipal AuthUser");
            assertThat(code).as(name + ": nada de @PreAuthorize ni permitAll, el 404 lo da el guard").doesNotContain("@PreAuthorize").doesNotContain("permitAll");
        }
        assertThat(handlers).isGreaterThanOrEqualTo(11); // los cinco informes, la lista y las cinco acciones
    }

    @Test
    void ac2_theGuardComparesTheSessionEmailWithTheConfigurationOnEveryRequestAndNeverReadsARoleFromTheDatabase() throws IOException {
        String guard = Files.readString(OWNER.resolve("OwnerGuard.java")) + Files.readString(OWNER.resolve("OwnerPolicy.java"));

        assertThat(guard).contains("ConfigurationProperties").containsIgnoringCase("equalsIgnoreCase").contains("ADULT").contains("NoResourceFoundException").contains("ACCESS_DENIED");
        assertThat(guard).doesNotContainIgnoringCase("SELECT").doesNotContainIgnoringCase("is_owner").doesNotContain("static volatile").doesNotContain("@Cacheable");
    }

    @Test
    void ac15_onlyTheActionRepositoryAndTheLimiterWriteAndTheStatsAreStrictlyReadOnly() throws IOException {
        for (Path file : sources()) {
            String name = file.getFileName().toString();
            String code = Files.readString(file);
            if (WRITERS.contains(name)) continue;
            assertThat(code).as(name + " no escribe en la BD").doesNotContainPattern(WRITE_SQL);
        }
        assertThat(Files.readString(OWNER.resolve("OwnerStatsRepository.java"))).doesNotContain("@Transactional").doesNotContain("jdbc.update").doesNotContain(".update(");
    }

    @Test
    void ac15_theWritersOnlyTouchTheirOwnTablesAndTheAccountStatusNeverUsageOrAudit() throws IOException {
        String code = Files.readString(OWNER.resolve("OwnerUserRepository.java")) + Files.readString(OWNER.resolve("OwnerRateLimiter.java"));
        Matcher m = WRITE_SQL.matcher(code);
        int found = 0;
        while (m.find()) {
            found++;
            String statement = code.substring(m.start(), Math.min(code.length(), m.start() + 160));
            assertThat(statement).as("escritura sobre una tabla ajena").containsPattern("(?i)(user_limit_override|owner_action|app_user|auth_session)");
        }
        assertThat(found).isPositive();
        assertThat(code).doesNotContainIgnoringCase("DELETE FROM ai_usage").doesNotContainIgnoringCase("audit_event_immutable");
    }

    @Test
    void ac4_noQueryOfThePanelNamesAContentColumnAndPersonalColumnsAreConfinedToTheUserRepository() throws IOException {
        for (Path file : sources()) {
            String name = file.getFileName().toString();
            for (String literal : literals(Files.readString(file))) {
                assertThat(literal).as(name + ": columna de contenido en «" + literal.strip() + "»").doesNotContainPattern(CONTENT);
                if (!name.equals("OwnerUserRepository.java")) {
                    assertThat(literal).as(name + ": datos personales fuera del repositorio de usuarios").doesNotContainPattern("(?i)\\b(display_name|email|login_id)\\b");
                }
            }
        }
    }

    @Test
    void ac4_aMinorsNameAndEmailAreNeverSelectedForTheUserList() throws IOException {
        String repository = Files.readString(OWNER.resolve("OwnerUserRepository.java"));

        // el nombre y el correo solo se leen en una rama condicionada al tipo ADULT
        assertThat(repository).containsPattern("(?is)CASE\\s+WHEN\\s+\\w*\\.?kind\\s*=\\s*'ADULT'\\s+THEN[^,]*display_name");
        assertThat(repository).containsPattern("(?is)CASE\\s+WHEN\\s+\\w*\\.?kind\\s*=\\s*'ADULT'\\s+THEN[^,]*email");
    }

    @Test
    void ac10_everyQueryIsParameterizedAndTheListSortsOnAClosedWhitelist() throws IOException {
        String code = all();

        assertThat(code).doesNotContainPattern("sql\\(\\s*\"[^\"]*\"\\s*\\+").doesNotContainPattern("sql\\(\\s*[a-zA-Z_]+\\s*\\+").doesNotContainPattern("queryForList\\(\\s*\"[^\"]*\"\\s*\\+");
        assertThat(code).contains("spend").contains("activity").contains("created"); // lista cerrada de ordenaciones
        assertThat(code).doesNotContainPattern("ORDER BY\\s*\"\\s*\\+\\s*\\w*[Ss]ort\\b"); // el parámetro no entra en el SQL: se traduce con un switch/enum
    }

    @Test
    void ac10_theRangeIsAClosedSet() throws IOException {
        String range = Files.readString(OWNER.resolve("OwnerRange.java"));

        assertThat(range).contains("7").contains("30").contains("90");
    }

    @Test
    void ac9_theActionsWriteTheirAuditInsideTheTransactionAndNameTheirEvents() throws IOException {
        String service = Files.readString(OWNER.resolve("OwnerUserService.java"));

        assertThat(service).contains("OWNER_USER_DEACTIVATED").contains("OWNER_USER_REACTIVATED").contains("OWNER_SESSIONS_REVOKED").contains("OWNER_LIMIT_SET").contains("OWNER_LIMIT_CLEARED")
                .contains("CANNOT_TARGET_SELF").containsAnyOf("TransactionTemplate", "@Transactional");
        assertThat(Files.readString(OWNER.resolve("OwnerUserRepository.java"))).containsIgnoringCase("FOR UPDATE");
    }

    @Test
    void ac9_noLogStatementNamesAnEmailAnAccessIdOrAName() throws IOException {
        for (Path file : sources()) {
            for (String line : Files.readAllLines(file)) {
                if (line.matches("(?i).*\\blog(ger)?\\.(trace|debug|info|warn|error)\\(.*")) {
                    assertThat(line).as(file.getFileName() + ": «" + line.strip() + "»").doesNotContainPattern("(?i)(email|displayName|loginId|limit)");
                }
            }
        }
    }

    @Test
    void theRestOfTheApplicationOnlyDependsOnTheOwnerPackageThroughMeAndLogin() throws IOException {
        List<String> importers = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (file.startsWith(OWNER)) continue;
                if (Files.readString(file).contains("studylab.owner.")) importers.add(file.getFileName().toString());
            }
        }

        assertThat(importers).isSubsetOf("MeController.java", "MeResponse.java", "AuthController.java");
    }

    @Test
    void ac7_theBudgetServiceAsksForTheOverrideByThePrimaryKeyOfTheUser() throws IOException {
        String usage = Files.readString(MAIN.resolve("ai/budget/UsageRepository.java"));

        assertThat(usage).containsPattern("(?is)user_limit_override[^;]*WHERE\\s+user_id\\s*=\\s*:");
        assertThat(Files.readString(MAIN.resolve("ai/budget/BudgetService.java"))).containsIgnoringCase("override");
    }
}
