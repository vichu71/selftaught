package es.vicai.studylab.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.Address;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Base de los tests de integración. Contrato HTTP que fijan estos tests (ART-003):
 * - la sesión viaja en la cookie {@value #SESSION_COOKIE}; el login también entrega XSRF-TOKEN;
 * - las peticiones autenticadas que modifican datos exigen CSRF; los endpoints públicos de /api/auth no;
 * - los correos llevan enlaces {public-url}/verify-email?token=... y {public-url}/reset-password?token=...
 * Los tests nunca envían correo real: SMTP es GreenMail en memoria.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    public static final String PASSWORD = "Correct-Horse-9";
    public static final String SESSION_COOKIE = "STUDYLAB_SESSION";
    protected static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @RegisterExtension
    protected static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    @DynamicPropertySource
    static void smtp(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> ServerSetupTest.SMTP.getPort());
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    /** Conexión con el rol de la aplicación (mínimos privilegios). */
    @Autowired protected JdbcTemplate app;
    /** Conexión con el rol propietario: limpia datos y lee tablas para comprobar resultados. */
    protected JdbcTemplate owner;

    @BeforeEach
    void cleanState() throws Exception {
        owner = new JdbcTemplate(new DriverManagerDataSource(
                System.getenv("TEST_DB_URL"),
                System.getenv("TEST_DB_OWNER_USER"),
                System.getenv("TEST_DB_OWNER_PASSWORD")));
        owner.execute("TRUNCATE audit_event, login_attempt, auth_session, email_token, guardianship, app_user"
                + " RESTART IDENTITY CASCADE");
        greenMail.purgeEmailFromAllMailboxes();
    }

    // ---------- peticiones ----------

    protected ResultActions postPublic(String path, Object body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    protected ResultActions postPublicFrom(String ip, String path, Object body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)).with(req -> { req.setRemoteAddr(ip); return req; }));
    }

    protected ResultActions postAs(String session, String path, Object body) throws Exception {
        MockHttpServletRequestBuilder req = post(path).with(csrf()).cookie(new Cookie(SESSION_COOKIE, session));
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(req);
    }

    protected ResultActions postAsWithoutCsrf(String session, String path, Object body) throws Exception {
        MockHttpServletRequestBuilder req = post(path).cookie(new Cookie(SESSION_COOKIE, session));
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(req);
    }

    protected ResultActions getAs(String session, String path) throws Exception {
        return mvc.perform(get(path).cookie(new Cookie(SESSION_COOKIE, session)));
    }

    // ---------- flujos frecuentes ----------

    protected Map<String, Object> registrationBody(String email, String displayName) {
        return Map.of("email", email, "password", PASSWORD, "displayName", displayName);
    }

    /** Registra un adulto, verifica su correo y devuelve la sesión iniciada. */
    protected String adultSession(String email) throws Exception {
        registerAndVerify(email, "Adulto " + email.substring(0, email.indexOf('@')));
        return login(email, PASSWORD);
    }

    protected void registerAndVerify(String email, String displayName) throws Exception {
        postPublic("/api/auth/register", registrationBody(email, displayName)).andExpect(status().isAccepted());
        postPublic("/api/auth/verify-email", Map.of("token", latestToken(email))).andExpect(status().isNoContent());
    }

    protected ResultActions tryLogin(String identifier, String password) throws Exception {
        return postPublic("/api/auth/login", Map.of("identifier", identifier, "password", password));
    }

    /** Inicia sesión esperando éxito y devuelve el valor de la cookie de sesión. */
    protected String login(String identifier, String password) throws Exception {
        Cookie cookie = tryLogin(identifier, password).andExpect(status().isOk())
                .andReturn().getResponse().getCookie(SESSION_COOKIE);
        if (cookie == null) throw new AssertionError("El login no devolvió la cookie " + SESSION_COOKIE);
        return cookie.getValue();
    }

    protected Map<String, Object> studentBody(String displayName, String loginHint) {
        return Map.of("displayName", displayName, "loginHint", loginHint, "password", PASSWORD);
    }

    /** Crea un menor y devuelve su id. */
    protected String createStudent(String adultSession, String displayName, String loginHint) throws Exception {
        String body = postAs(adultSession, "/api/tutor/students", studentBody(displayName, loginHint))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    protected String loginIdOf(String studentId) {
        return owner.queryForObject("SELECT login_id FROM app_user WHERE id = ?::uuid", String.class, studentId);
    }

    // ---------- correo ----------

    protected List<MimeMessage> mailsTo(String email) throws Exception {
        List<MimeMessage> found = new ArrayList<>();
        for (MimeMessage m : greenMail.getReceivedMessages()) {
            for (Address a : m.getAllRecipients()) {
                if (a.toString().toLowerCase().contains(email.toLowerCase())) found.add(m);
            }
        }
        return found;
    }

    protected String bodyOf(MimeMessage message) throws Exception {
        Object content = message.getContent();
        if (content instanceof String s) return s;
        if (content instanceof Multipart mp) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mp.getCount(); i++) sb.append(mp.getBodyPart(i).getContent());
            return sb.toString();
        }
        return String.valueOf(content);
    }

    /** Token del último correo recibido por ese destinatario. */
    protected String latestToken(String email) throws Exception {
        List<MimeMessage> mails = mailsTo(email);
        if (mails.isEmpty()) throw new AssertionError("No hay correos para " + email);
        Matcher m = TOKEN.matcher(bodyOf(mails.get(mails.size() - 1)));
        if (!m.find()) throw new AssertionError("El correo no contiene un enlace con token");
        return m.group(1);
    }
}
