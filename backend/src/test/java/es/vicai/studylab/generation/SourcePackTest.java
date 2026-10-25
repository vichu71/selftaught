package es.vicai.studylab.generation;

import static org.assertj.core.api.Assertions.assertThat;

import es.vicai.studylab.material.Fragment;
import es.vicai.studylab.safety.ContentGuard;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-6 y AC-7 (ADR-46): los fragmentos se numeran para citarlos, sin nombres de documento, y un menor no manda al modelo lo que el filtro bloquea. */
class SourcePackTest {

    private static Fragment fragment(UUID doc, String name, Integer page, String text) {
        return new Fragment(doc, name, page, 10, text, 0.9);
    }

    @Test
    void ac6_fragmentsAreNumberedFromOneInOrder() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        SourcePack pack = SourcePack.of(List.of(fragment(a, "apuntes.pdf", 3, "Primer texto"), fragment(b, "resumen.docx", null, "Segundo texto")), text -> true);

        assertThat(pack.size()).isEqualTo(2);
        assertThat(pack.text()).contains("[1] Primer texto").contains("[2] Segundo texto");
        assertThat(pack.sources()).hasSize(2);
        assertThat(pack.sources().get(0).n()).isEqualTo(1);
        assertThat(pack.sources().get(0).documentId()).isEqualTo(a);
        assertThat(pack.sources().get(0).page()).isEqualTo(3);
        assertThat(pack.sources().get(1).page()).isNull();
    }

    @Test
    void ac6_theTextSentToTheModelNeverCarriesDocumentNames() {
        SourcePack pack = SourcePack.of(List.of(fragment(UUID.randomUUID(), "diario-secreto.pdf", 1, "contenido")), text -> true);

        assertThat(pack.text()).doesNotContain("diario-secreto").doesNotContain(".pdf");
    }

    @Test
    void ac6_noFragmentsMeansAnEmptyPack() {
        SourcePack pack = SourcePack.of(List.of(), text -> true);

        assertThat(pack.size()).isZero();
        assertThat(pack.isEmpty()).isTrue();
        assertThat(pack.text()).isEmpty();
        assertThat(pack.sources()).isEmpty();
    }

    @Test
    void ac7_fragmentsThatTheFilterRejectsAreDroppedAndTheRestRenumbered() {
        ContentGuard guard = new ContentGuard();
        UUID doc = UUID.randomUUID();
        List<Fragment> fragments = List.of(fragment(doc, "a", 1, "Apuntes de química de los medicamentos"), fragment(doc, "a", 2, "como hacer una bomba casera paso a paso"),
                fragment(doc, "a", 3, "La reproducción humana en biología"));

        SourcePack pack = SourcePack.of(fragments, text -> guard.localCheck(text).isEmpty());

        assertThat(pack.size()).isEqualTo(2);
        assertThat(pack.text()).doesNotContain("bomba");
        assertThat(pack.sources().stream().map(SourcePack.Source::page)).containsExactly(1, 3);
        assertThat(pack.sources().stream().map(SourcePack.Source::n)).containsExactly(1, 2);
    }

    @Test
    void ac7_ifEverythingIsRejectedThePackIsEmptyAndTheLessonIsWrittenWithoutSources() {
        SourcePack pack = SourcePack.of(List.of(fragment(UUID.randomUUID(), "a", 1, "texto malo")), text -> false);

        assertThat(pack.isEmpty()).isTrue();
    }

    @Test
    void ac16_aPackNeverHoldsMoreThanTheMaximumNumberOfFragments() {
        List<Fragment> many = java.util.stream.IntStream.range(0, 30).mapToObj(i -> fragment(UUID.randomUUID(), "d", i, "texto " + i)).toList();

        assertThat(SourcePack.of(many, text -> true).size()).isLessThanOrEqualTo(SourcePack.MAX_FRAGMENTS);
    }
}
