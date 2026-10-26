package es.vicai.studylab.share;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** AC-12: límite por usuario, contado en la BD, de canjes fallidos y de copias por hora. Contexto propio: baja los dos valores. */
@TestPropertySource(properties = {"app.share.max-failures-per-hour=3", "app.share.max-copies-per-hour=2"})
class ShareLimitsTest extends ShareTestBase {

    @Test
    void ac12_afterThreeFailuresInAnHourEvenAValidCodeIsRefusedUntilItPasses() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = ready("ana@example.com");
        String code = createCode(ana, course);
        for (int i = 0; i < 3; i++) redeem(luis, "AAAA-AAAA-AAAA-AAA" + i).andExpect(status().isNotFound());

        redeem(luis, code).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("CODE_RATE_LIMITED"));

        assertThat(count("SELECT count(*) FROM course WHERE owner_id = ?", idOf("luis@example.com"))).isZero();
        assertThat(count("SELECT uses FROM share_code WHERE course_id = ?", course)).isZero();
        owner.update("UPDATE course_action SET created_at = now() - interval '2 hours' WHERE user_id = ?", idOf("luis@example.com"));
        redeemed(luis, code);
    }

    @Test
    void ac12_aRefusedAttemptDoesNotTouchTheDatabaseBeyondTheLimitAndStaysRefused() throws Exception {
        String luis = logged("luis@example.com");
        for (int i = 0; i < 3; i++) redeem(luis, "AAAA-AAAA-AAAA-AAA" + i).andExpect(status().isNotFound());

        redeem(luis, "BBBB-BBBB-BBBB-BBBB").andExpect(status().isTooManyRequests());
        redeem(luis, "BBBB-BBBB-BBBB-BBBB").andExpect(status().isTooManyRequests());

        assertThat(count("SELECT count(*) FROM course_action WHERE user_id = ? AND kind = 'REDEEM_FAIL'", idOf("luis@example.com"))).isEqualTo(3);
    }

    @Test
    void ac12_theFailuresOfOnePersonNeverBlockAnother() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        String marta = logged("marta@example.com");
        UUID course = ready("ana@example.com");
        String code = createCode(ana, course);
        for (int i = 0; i < 3; i++) redeem(luis, "AAAA-AAAA-AAAA-AAA" + i).andExpect(status().isNotFound());

        redeemed(marta, code);
        redeem(luis, code).andExpect(status().isTooManyRequests());
    }

    @Test
    void ac12_aThirdCopyInTheSameHourIsRefused() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = ready("ana@example.com");
        String code = createCode(ana, course);
        redeemed(luis, code);
        redeemed(luis, code);

        redeem(luis, code).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("CODE_RATE_LIMITED"));

        assertThat(count("SELECT count(*) FROM course WHERE owner_id = ?", idOf("luis@example.com"))).isEqualTo(2);
        assertThat(count("SELECT uses FROM share_code WHERE course_id = ?", course)).isEqualTo(2);
    }

    @Test
    void ac12_failuresOlderThanAnHourNoLongerCount() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = ready("ana@example.com");
        String code = createCode(ana, course);
        for (int i = 0; i < 3; i++) owner.update("INSERT INTO course_action (user_id, kind, created_at) VALUES (?, 'REDEEM_FAIL', now() - interval '3 hours')", idOf("luis@example.com"));

        redeemed(luis, code);
    }
}
