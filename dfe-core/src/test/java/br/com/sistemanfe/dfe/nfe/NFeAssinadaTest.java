package br.com.sistemanfe.dfe.nfe;

import br.com.sistemanfe.dfe.DfeException;
import br.com.sistemanfe.dfe.certificado.CertificadoA1;
import br.com.sistemanfe.dfe.comum.Ambiente;
import br.com.sistemanfe.dfe.teste.CertificadosDeTeste;
import br.com.sistemanfe.dfe.teste.ConferenciaAssinatura;
import br.com.sistemanfe.dfe.teste.NotasDeTeste;
import br.inf.portalfiscal.nfe.TNFe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NFeAssinadaTest {

    private static final char[] SENHA = "teste".toCharArray();

    private static CertificadoA1 certificadoDoEmitente;

    @BeforeAll
    static void criarCertificado() {
        certificadoDoEmitente = certificado(NotasDeTeste.CNPJ_EMITENTE);
    }

    @Test
    void assinaANotaMontadaEGuardaAChave() throws Exception {
        TNFe nfe = MontadorNFe.montar(NotasDeTeste.nfeMinima());

        NFeAssinada assinada = NFeAssinada.assinar(nfe, certificadoDoEmitente);

        assertThat(assinada.chave().idInfNFe()).isEqualTo(nfe.getInfNFe().getId());
        assertThat(assinada.xml())
                .startsWith("<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\"><infNFe ")
                .endsWith("</Signature></NFe>")
                .doesNotContain("ns2:", "xmlns:ns");
        assertThat(ConferenciaAssinatura.confere(assinada.xml(),
                certificadoDoEmitente.certificado().getPublicKey())).isTrue();
    }

    @Test
    void certificadoDeOutraEmpresaEhRecusado() {
        TNFe nfe = MontadorNFe.montar(NotasDeTeste.nfeMinima());
        CertificadoA1 deOutraEmpresa = certificado(NotasDeTeste.CNPJ_DESTINATARIO);

        assertThatThrownBy(() -> NFeAssinada.assinar(nfe, deOutraEmpresa))
                .isInstanceOf(DfeException.class)
                .hasMessageContaining(NotasDeTeste.CNPJ_DESTINATARIO)
                .hasMessageContaining(NotasDeTeste.CNPJ_EMITENTE)
                .hasMessageContaining("213");
    }

    @Test
    void certificadoDaMatrizAssinaNotaDaFilial() {
        Emitente matriz = NotasDeTeste.emitente();
        Emitente filial = new Emitente("11222333000262", matriz.razaoSocial(), matriz.nomeFantasia(),
                matriz.inscricaoEstadual(), matriz.endereco());
        DadosNFe minima = NotasDeTeste.nfeMinima();
        DadosNFe daFilial = new DadosNFe(Ambiente.HOMOLOGACAO, minima.naturezaOperacao(), minima.serie(),
                minima.numero(), minima.dataHoraEmissao(), filial, minima.destinatario(), minima.itens(),
                minima.pagamentos(), minima.consumidorFinal(), minima.presenca());

        NFeAssinada assinada = NFeAssinada.assinar(MontadorNFe.montar(daFilial), certificadoDoEmitente);

        assertThat(assinada.chave().cnpjEmitente()).isEqualTo("11222333000262");
    }

    @Test
    void certificadoSemCnpjEhRecusado() {
        CertificadoA1 semCnpj = CertificadoA1.carregar(
                CertificadosDeTeste.eCnpj("PESSOA FICTICIA", null, null, 30).comoPfx(SENHA), SENHA);
        TNFe nfe = MontadorNFe.montar(NotasDeTeste.nfeMinima());

        assertThatThrownBy(() -> NFeAssinada.assinar(nfe, semCnpj))
                .isInstanceOf(DfeException.class)
                .hasMessageContaining("CNPJ");
    }

    @Test
    void notaComVariosItensTambemConfere() throws Exception {
        DadosNFe dados = NotasDeTeste.nfe(Ambiente.HOMOLOGACAO, NotasDeTeste.destinatarioContribuinte(),
                List.of(NotasDeTeste.item(), NotasDeTeste.item()),
                List.of(new Pagamento(MeioPagamento.DINHEIRO, new BigDecimal("42.00"))), false);

        NFeAssinada assinada = NFeAssinada.assinar(MontadorNFe.montar(dados), certificadoDoEmitente);

        assertThat(ConferenciaAssinatura.confere(assinada.xml(),
                certificadoDoEmitente.certificado().getPublicKey())).isTrue();
    }

    private static CertificadoA1 certificado(String cnpj) {
        CertificadosDeTeste.Gerado gerado = CertificadosDeTeste.eCnpj("EMPRESA FICTICIA:" + cnpj, cnpj, null, 30);
        return CertificadoA1.carregar(gerado.comoPfx(SENHA), SENHA);
    }
}
