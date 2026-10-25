package es.vicai.studylab.study;

import es.vicai.studylab.generation.ExamParser;
import java.util.ArrayList;
import java.util.List;

/** Corrige un examen en el servidor, sin IA (ADR-50): compara cada opción elegida con la correcta. Una pregunta en blanco cuenta como fallada. */
public final class ExamGrader {

    private ExamGrader() {}

    public record Question(String text, List<String> options, Integer chosen, int correct, String explanation, boolean ok) {}

    public record Result(int correct, int total, int percent, boolean passed, List<Question> questions) {}

    /** {@code answers} lleva una opción (o {@code null}) por pregunta; quien llama ya comprobó que su longitud y sus índices son válidos. */
    public static Result grade(List<ExamParser.Question> questions, List<Integer> answers, int passPercent) {
        List<Question> detail = new ArrayList<>();
        int correct = 0;
        for (int i = 0; i < questions.size(); i++) {
            ExamParser.Question q = questions.get(i);
            Integer chosen = answers.get(i);
            boolean ok = chosen != null && chosen == q.correct();
            if (ok) correct++;
            detail.add(new Question(q.text(), q.options(), chosen, q.correct(), q.explanation(), ok));
        }
        return new Result(correct, questions.size(), percent(correct, questions.size()), passes(correct, questions.size(), passPercent), List.copyOf(detail));
    }

    public static int percent(int correct, int total) {
        return total == 0 ? 0 : (int) Math.round(correct * 100.0 / total);
    }

    /** Con el 50 % exacto el tema se supera (aritmética entera: sin redondeos). */
    public static boolean passes(int correct, int total, int passPercent) {
        return total > 0 && correct * 100 >= total * passPercent;
    }
}
