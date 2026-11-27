package br.com.sistemanfe.dfe.comum;

/** Forma de emissão (campo {@code tpEmis}), também usada na chave de acesso. */
public enum FormaEmissao {
    NORMAL("1"),
    /** Formulário de segurança, impressor autônomo. */
    CONTINGENCIA_FS_IA("2"),
    /** Regime especial da Nota Fiscal Fácil (NT 2021.002). */
    REGIME_ESPECIAL_NFF("3"),
    CONTINGENCIA_EPEC("4"),
    /** Formulário de segurança para impressão de documento auxiliar. */
    CONTINGENCIA_FS_DA("5"),
    CONTINGENCIA_SVC_AN("6"),
    CONTINGENCIA_SVC_RS("7"),
    /** Contingência off-line da NFC-e (e da NF-e com DANFE simplificado tipo 2). */
    CONTINGENCIA_OFFLINE_NFCE("9");

    private final String codigo;

    FormaEmissao(String codigo) {
        this.codigo = codigo;
    }

    /** Código usado no XML ({@code tpEmis}). */
    public String codigo() {
        return codigo;
    }

    public static FormaEmissao porCodigo(String codigo) {
        for (FormaEmissao f : values()) {
            if (f.codigo.equals(codigo)) {
                return f;
            }
        }
        throw new IllegalArgumentException("tpEmis inválido: " + codigo);
    }
}
