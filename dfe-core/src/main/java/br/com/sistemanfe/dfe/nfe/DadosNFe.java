package br.com.sistemanfe.dfe.nfe;

import br.com.sistemanfe.dfe.comum.Ambiente;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Tudo o que a NF-e (modelo 55) precisa para ser montada. Os dados vêm do cadastro e da venda; o
 * {@link MontadorNFe} transforma em XML.
 *
 * @param numero          número da nota ({@code nNF}), reservado pela numeração da empresa
 * @param consumidorFinal {@code indFinal}; obrigatório quando o destinatário não é contribuinte
 */
public record DadosNFe(Ambiente ambiente, String naturezaOperacao, int serie, int numero,
                       OffsetDateTime dataHoraEmissao, Emitente emitente, Destinatario destinatario,
                       List<ItemNFe> itens, List<Pagamento> pagamentos, boolean consumidorFinal,
                       IndicadorPresenca presenca) {

    public DadosNFe {
        Objects.requireNonNull(ambiente, "ambiente");
        Objects.requireNonNull(naturezaOperacao, "naturezaOperacao");
        Objects.requireNonNull(dataHoraEmissao, "dataHoraEmissao");
        Objects.requireNonNull(emitente, "emitente");
        Objects.requireNonNull(destinatario, "destinatario");
        Objects.requireNonNull(presenca, "presenca");
        itens = List.copyOf(itens);
        pagamentos = List.copyOf(pagamentos);
    }
}
