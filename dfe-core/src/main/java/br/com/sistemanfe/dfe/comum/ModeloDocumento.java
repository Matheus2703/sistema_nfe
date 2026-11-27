package br.com.sistemanfe.dfe.comum;

/** Modelo do documento fiscal (campo {@code mod}). */
public enum ModeloDocumento {
    NFE("55"),
    NFCE("65");

    private final String codigo;

    ModeloDocumento(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    public static ModeloDocumento porCodigo(String codigo) {
        for (ModeloDocumento m : values()) {
            if (m.codigo.equals(codigo)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Modelo inválido: " + codigo);
    }
}
