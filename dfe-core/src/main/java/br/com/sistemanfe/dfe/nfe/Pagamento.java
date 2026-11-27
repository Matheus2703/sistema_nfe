package br.com.sistemanfe.dfe.nfe;

import java.math.BigDecimal;
import java.util.Objects;

/** Uma forma de pagamento da nota ({@code detPag}). */
public record Pagamento(MeioPagamento meio, BigDecimal valor) {

    public Pagamento {
        Objects.requireNonNull(meio, "meio");
        Objects.requireNonNull(valor, "valor");
    }
}
