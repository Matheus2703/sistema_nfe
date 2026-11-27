package br.com.sistemanfe.dfe.nfe;

/** Presença do comprador no momento da operação ({@code indPres}). */
public enum IndicadorPresenca {
    /** Nota complementar ou de ajuste. */
    NAO_SE_APLICA("0"),
    PRESENCIAL("1"),
    INTERNET("2"),
    TELEATENDIMENTO("3"),
    PRESENCIAL_FORA_DO_ESTABELECIMENTO("5"),
    OUTROS("9");

    private final String codigo;

    IndicadorPresenca(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
