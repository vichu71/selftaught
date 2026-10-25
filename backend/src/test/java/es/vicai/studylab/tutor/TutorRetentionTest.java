package es.vicai.studylab.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * AC-9 (ADR-55): solo se conservan los últimos mensajes de cada lección. Contexto propio: el tope baja a 4. Los límites por hora y por día se cuentan sobre lo
 * conservado, así que aquí van aparte, con sus valores por defecto (el tope de conservación nunca debe bajar de 2 × el límite del día).
 */
@TestPropertySource(properties = "app.tutor.max-stored=4")
class TutorRetentionTest extends TutorTestBase {

    @Test
    void ac9_onlyTheLatestMessagesOfALessonAreKeptAndOtherLessonsAreUntouched() throws Exception {
        String ana = logged("ana@example.com");
        UUID anaId = idOf("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID lesson = lesson(course, 0);
        UUID other = lesson(course, 1);
        insertMessage(anaId, course, other, "USER", "OK", "de otra lección 1", "5 hours");
        insertMessage(anaId, course, other, "TUTOR", "OK", "de otra lección 2", "5 hours");
        insertMessage(anaId, course, other, "USER", "OK", "de otra lección 3", "5 hours");
        insertMessage(anaId, course, other, "TUTOR", "OK", "de otra lección 4", "5 hours");
        insertMessage(anaId, course, other, "USER", "OK", "de otra lección 5", "5 hours");
        tutorAnswers(ANSWER);
        for (int i = 1; i <= 3; i++) ask(ana, course, lesson, "pregunta " + i).andExpect(status().isCreated());

        assertThat(owner.queryForObject("SELECT count(*) FROM tutor_message WHERE piece_id = ?", Integer.class, lesson)).isEqualTo(4); // 3 intercambios = 6 → quedan 4
        assertThat(owner.queryForObject("SELECT count(*) FROM tutor_message WHERE piece_id = ?", Integer.class, other)).isEqualTo(5); // las otras lecciones no se tocan
        thread(ana, course, lesson).andExpect(jsonPath("$.messages[3].role").value("TUTOR"));
    }
}
