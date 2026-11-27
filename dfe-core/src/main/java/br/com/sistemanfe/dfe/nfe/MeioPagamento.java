package br.com.sistemanfe.dfe.nfe;

/**
 * Meio de pagamento ({@code tPag}). Só os que não exigem grupos extras; cartão e PIX entram com a NFC-e.
 */
public enum MeioPagamento {
    DINHEIRO("01"),
    CHEQUE("02"),
    BOLETO_BANCARIO("15"),
    DEPOSITO_BANCARIO("16"),
    TRANSFERENCIA_BANCARIA("18");

    private final String codigo;

    MeioPagamento(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
