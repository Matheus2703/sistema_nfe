package br.com.sistemanfe.dfe.teste;

import br.com.sistemanfe.dfe.comum.Ambiente;
import br.com.sistemanfe.dfe.comum.UF;
import br.com.sistemanfe.dfe.nfe.DadosNFe;
import br.com.sistemanfe.dfe.nfe.Destinatario;
import br.com.sistemanfe.dfe.nfe.Emitente;
import br.com.sistemanfe.dfe.nfe.Endereco;
import br.com.sistemanfe.dfe.nfe.IndicadorIEDestinatario;
import br.com.sistemanfe.dfe.nfe.IndicadorPresenca;
import br.com.sistemanfe.dfe.nfe.ItemNFe;
import br.com.sistemanfe.dfe.nfe.MeioPagamento;
import br.com.sistemanfe.dfe.nfe.OrigemMercadoria;
import br.com.sistemanfe.dfe.nfe.Pagamento;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Dados fictícios de NF-e para os testes. Nenhum nome, CNPJ ou inscrição estadual é de empresa real:
 * os CNPJ são exemplos clássicos de cálculo de dígito verificador e as inscrições estaduais têm o dígito
 * verificador propositalmente errado.
 */
public final class NotasDeTeste {

    public static final String CNPJ_EMITENTE = "11222333000181";
    public static final String IE_EMITENTE = "1234567890";
    public static final String CNPJ_DESTINATARIO = "11444777000161";
    public static final String IE_DESTINATARIO = "9876543210";
    public static final String CPF_DESTINATARIO = "12345678909";
    public static final String NOME_DESTINATARIO = "CLIENTE FICTICIO DE TESTE LTDA";

    /** Porto Alegre (código IBGE). */
    public static final String MUNICIPIO_RS = "4314902";
    /** Florianópolis (código IBGE). */
    public static final String MUNICIPIO_SC = "4205407";

    public static final OffsetDateTime EMISSAO = OffsetDateTime.of(2026, 11, 27, 10, 30, 0, 0, ZoneOffset.ofHours(-3));

    private NotasDeTeste() {
    }

    public static Endereco enderecoRs() {
        return new Endereco("RUA FICTICIA DE TESTE", "100", null, "CENTRO", MUNICIPIO_RS, "PORTO ALEGRE", UF.RS,
                "90000000", null);
    }

    public static Endereco enderecoSc() {
        return new Endereco("AVENIDA FICTICIA", "200", "SALA 1", "CENTRO", MUNICIPIO_SC, "FLORIANOPOLIS", UF.SC,
                "88000000", "4830000000");
    }

    public static Emitente emitente() {
        return new Emitente(CNPJ_EMITENTE, "EMPRESA FICTICIA DE TESTE LTDA", "LOJA DE TESTE", IE_EMITENTE,
                enderecoRs());
    }

    /** Destinatário contribuinte do ICMS no RS. */
    public static Destinatario destinatarioContribuinte() {
        return new Destinatario(CNPJ_DESTINATARIO, NOME_DESTINATARIO, enderecoRs(),
                IndicadorIEDestinatario.CONTRIBUINTE, IE_DESTINATARIO, "cliente@exemplo.com");
    }

    /** Pessoa física não contribuinte no RS. */
    public static Destinatario destinatarioPessoaFisica() {
        return new Destinatario(CPF_DESTINATARIO, "CONSUMIDOR FICTICIO", enderecoRs(),
                IndicadorIEDestinatario.NAO_CONTRIBUINTE, null, null);
    }

    /** 2 unidades a R$ 10,50: R$ 21,00. */
    public static ItemNFe item() {
        return item("5102", new BigDecimal("2"), new BigDecimal("10.50"));
    }

    public static ItemNFe item(String cfop, BigDecimal quantidade, BigDecimal valorUnitario) {
        return new ItemNFe("P001", "PRODUTO FICTICIO DE TESTE", null, "61091000", cfop, "UN", quantidade,
                valorUnitario, OrigemMercadoria.NACIONAL);
    }

    /** NF-e mínima em homologação: venda no RS para contribuinte, 1 item de R$ 21,00 pago em dinheiro. */
    public static DadosNFe nfeMinima() {
        return nfe(Ambiente.HOMOLOGACAO, destinatarioContribuinte(), List.of(item()),
                List.of(new Pagamento(MeioPagamento.DINHEIRO, new BigDecimal("21.00"))), false);
    }

    public static DadosNFe nfe(Ambiente ambiente, Destinatario destinatario, List<ItemNFe> itens,
                               List<Pagamento> pagamentos, boolean consumidorFinal) {
        return new DadosNFe(ambiente, "VENDA DE MERCADORIA", 1, 42, EMISSAO, emitente(), destinatario, itens,
                pagamentos, consumidorFinal, IndicadorPresenca.PRESENCIAL);
    }
}
