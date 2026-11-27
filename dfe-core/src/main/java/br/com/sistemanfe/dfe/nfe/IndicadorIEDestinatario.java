package br.com.sistemanfe.dfe.nfe;

/** Indicador da inscrição estadual do destinatário ({@code indIEDest}). */
public enum IndicadorIEDestinatario {
    /** Contribuinte do ICMS, com inscrição estadual. */
    CONTRIBUINTE("1"),
    /** Contribuinte isento de inscrição estadual. */
    CONTRIBUINTE_ISENTO("2"),
    /** Não contribuinte (por exemplo, pessoa física); a operação tem que ser com consumidor final. */
    NAO_CONTRIBUINTE("9");

    private final String codigo;

    IndicadorIEDestinatario(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
