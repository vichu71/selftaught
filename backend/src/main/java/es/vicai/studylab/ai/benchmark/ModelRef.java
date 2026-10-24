package es.vicai.studylab.ai.benchmark;

/** Un modelo concreto de un proveedor, como "anthropic/modelo". */
public record ModelRef(String provider, String model) {

    public static ModelRef parse(String text) {
        int slash = text.indexOf('/');
        if (slash <= 0 || slash == text.length() - 1) {
            throw new IllegalArgumentException("El modelo debe indicarse como proveedor/modelo");
        }
        return new ModelRef(text.substring(0, slash).trim().toLowerCase(), text.substring(slash + 1).trim());
    }

    @Override
    public String toString() {
        return provider + "/" + model;
    }
}
