package br.com.sistemanfe.dfe.nfe;

import br.com.sistemanfe.dfe.DfeException;
import br.com.sistemanfe.dfe.certificado.CertificadoA1;
import br.com.sistemanfe.dfe.comum.Ambiente;
import br.com.sistemanfe.dfe.teste.CertificadosDeTeste;
import br.com.sistemanfe.dfe.teste.ConferenciaAssinatura;
import br.com.sistemanfe.dfe.teste.NotasDeTeste;
import br.com.sistemanfe.dfe.xml.ValidadorXsd;
import br.inf.portalfiscal.nfe.TNFe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoteNFeTest {

    private static final char[] SENHA = "teste".toCharArray();

    private static CertificadoA1 certificado;

    @BeforeAll
    static void criarCertificado() {
        CertificadosDeTeste.Gerado gerado = CertificadosDeTeste.eCnpj("EMPRESA FICTICIA DE TESTE LTDA",
                NotasDeTeste.CNPJ_EMITENTE, null, 30);
        certificado = CertificadoA1.carregar(gerado.comoPfx(SENHA), SENHA);
    }

    @Test
    void loteComANFeMinimaAssinadaPassaNoSchemaOficial() {
        String lote = LoteNFe.montar("1", assinar(MontadorNFe.montar(NotasDeTeste.nfeMinima())));

        assertThat(ValidadorXsd.para("enviNFe_v4.00.xsd").validar(lote)).isEmpty();
    }

    @Test
    void vendaInterestadualParaPessoaFisicaTambemPassaNoSchema() {
        Destinatario pessoaFisicaEmSc = new Destinatario(NotasDeTeste.CPF_DESTINATARIO, "CONSUMIDOR FICTICIO",
                NotasDeTeste.enderecoSc(), IndicadorIEDestinatario.NAO_CONTRIBUINTE, null, null);
        DadosNFe dados = NotasDeTeste.nfe(Ambiente.PRODUCAO, pessoaFisicaEmSc,
                List.of(NotasDeTeste.item("6102", new BigDecimal("1.5"), new BigDecimal("19.9"))),
                List.of(new Pagamento(MeioPagamento.TRANSFERENCIA_BANCARIA, new BigDecimal("29.85"))), true);

        String lote = LoteNFe.montar("1", assinar(MontadorNFe.montar(dados)));

        assertThat(ValidadorXsd.para("enviNFe_v4.00.xsd").validar(lote)).isEmpty();
        assertThat(lote).contains("<CPF>" + NotasDeTeste.CPF_DESTINATARIO + "</CPF>", "<idDest>2</idDest>",
                "<xNome>CONSUMIDOR FICTICIO</xNome>");
    }

    @Test
    void loteSincronoComUmaNFeNoFormatoDaSefaz() {
        NFeAssinada nfe = assinar(MontadorNFe.montar(NotasDeTeste.nfeMinima()));

        String lote = LoteNFe.montar("202611270001", nfe);

        assertThat(lote)
                .startsWith("<enviNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">"
                        + "<idLote>202611270001</idLote><indSinc>1</indSinc>"
                        + "<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\"><infNFe ")
                .endsWith("</Signature></NFe></enviNFe>")
                .contains(nfe.xml())
                .doesNotContain("<?xml", "ns2:", "xmlns:ns");
    }

    @Test
    void assinaturaContinuaValidaDentroDoLote() throws Exception {
        String lote = LoteNFe.montar("1", assinar(MontadorNFe.montar(NotasDeTeste.nfeMinima())));

        assertThat(ConferenciaAssinatura.confere(lote, certificado.certificado().getPublicKey())).isTrue();
    }

    @Test
    void idLoteForaDoPadraoEhRecusado() {
        NFeAssinada nfe = assinar(MontadorNFe.montar(NotasDeTeste.nfeMinima()));

        assertThatThrownBy(() -> LoteNFe.montar("LOTE1", nfe))
                .isInstanceOf(DfeException.class)
                .hasMessageContaining("enviNFe_v4.00.xsd");
    }

    @Test
    void notaForaDoSchemaEhRecusadaAntesDeIrParaASefaz() {
        TNFe nfe = MontadorNFe.montar(NotasDeTeste.nfeMinima());
        nfe.getInfNFe().getIde().setNatOp("X".repeat(61));

        assertThatThrownBy(() -> LoteNFe.montar("1", assinar(nfe)))
                .isInstanceOf(DfeException.class)
                .hasMessageContaining("enviNFe_v4.00.xsd")
                .hasMessageContaining("61");
    }

    private static NFeAssinada assinar(TNFe nfe) {
        return NFeAssinada.assinar(nfe, certificado);
    }
}
