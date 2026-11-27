package br.com.sistemanfe.dfe.nfe;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Produto vendido. Nesta fase todo item sai com CSOSN 102 (Simples Nacional sem permissão de crédito); o motor
 * fiscal da fase 5 vai escolher a tributação.
 *
 * @param gtin          código de barras (GTIN-8, 12, 13 ou 14); {@code null} para "SEM GTIN"
 * @param ncm           8 dígitos
 * @param cfop          4 dígitos; começa por 5 na venda dentro do estado e por 6 na interestadual
 * @param quantidade    até 4 casas decimais
 * @param valorUnitario até 10 casas decimais
 */
public record ItemNFe(String codigo, String descricao, String gtin, String ncm, String cfop, String unidade,
                      BigDecimal quantidade, BigDecimal valorUnitario, OrigemMercadoria origem) {

    public ItemNFe {
        Objects.requireNonNull(codigo, "codigo");
        Objects.requireNonNull(descricao, "descricao");
        Objects.requireNonNull(ncm, "ncm");
        Objects.requireNonNull(cfop, "cfop");
        Objects.requireNonNull(unidade, "unidade");
        Objects.requireNonNull(quantidade, "quantidade");
        Objects.requireNonNull(valorUnitario, "valorUnitario");
        Objects.requireNonNull(origem, "origem");
    }
}
