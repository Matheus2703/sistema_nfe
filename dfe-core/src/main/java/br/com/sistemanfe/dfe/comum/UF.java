package br.com.sistemanfe.dfe.comum;

/** Unidades federativas com o código IBGE usado no XML ({@code cUF}). */
public enum UF {
    RO("11"), AC("12"), AM("13"), RR("14"), PA("15"), AP("16"), TO("17"),
    MA("21"), PI("22"), CE("23"), RN("24"), PB("25"), PE("26"), AL("27"), SE("28"), BA("29"),
    MG("31"), ES("32"), RJ("33"), SP("35"),
    PR("41"), SC("42"), RS("43"),
    MS("50"), MT("51"), GO("52"), DF("53");

    private final String codigoIbge;

    UF(String codigoIbge) {
        this.codigoIbge = codigoIbge;
    }

    public String codigoIbge() {
        return codigoIbge;
    }

    public static UF porCodigoIbge(String codigo) {
        for (UF uf : values()) {
            if (uf.codigoIbge.equals(codigo)) {
                return uf;
            }
        }
        throw new IllegalArgumentException("Código de UF inválido: " + codigo);
    }
}
