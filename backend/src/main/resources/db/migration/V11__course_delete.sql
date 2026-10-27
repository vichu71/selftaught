-- F13: borrar cursos (ADR-84, ADR-85). El resto cae por las FK en cascada; el registro de gasto no tiene FK al curso y se conserva.
GRANT DELETE ON course TO studylab_app;
