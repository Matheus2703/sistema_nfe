package br.com.sistemanfe.dfe.comum;

/** Ambiente da Sefaz (campo {@code tpAmb}). */
public enum Ambiente {
    PRODUCAO("1"),
    HOMOLOGACAO("2");

    private final String codigo;

    Ambiente(String codigo) {
        this.codigo = codigo;
    }

    /** Código usado no XML ({@code tpAmb}). */
    public String codigo() {
        return codigo;
    }

    public static Ambiente porCodigo(String codigo) {
        for (Ambiente a : values()) {
            if (a.codigo.equals(codigo)) {
                return a;
            }
        }
        throw new IllegalArgumentException("tpAmb inválido: " + codigo);
    }
}
