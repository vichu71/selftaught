package es.vicai.studylab.owner;

import es.vicai.studylab.web.ApiException;

/** Rangos del panel: un conjunto cerrado de 7, 30 o 90 días (ADR-89). Sin parámetro, 30; cualquier otro valor, 400. */
public enum OwnerRange {
    D7(7), D30(30), D90(90);

    private final int days;

    OwnerRange(int days) {
        this.days = days;
    }

    public int days() {
        return days;
    }

    /** Días hacia atrás desde hoy que abarca el rango (hoy incluido). */
    public int back() {
        return days - 1;
    }

    public static OwnerRange parse(String raw) {
        if (raw == null) return D30;
        for (OwnerRange range : values()) {
            if (String.valueOf(range.days).equals(raw.strip())) return range;
        }
        throw ApiException.validation("El rango debe ser 7, 30 o 90 días");
    }
}
