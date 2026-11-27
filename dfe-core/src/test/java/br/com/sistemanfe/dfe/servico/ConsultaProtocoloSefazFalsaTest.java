package br.com.sistemanfe.dfe.servico;

import br.com.sistemanfe.dfe.certificado.CertificadoA1;
import br.com.sistemanfe.dfe.comum.Ambiente;
import br.com.sistemanfe.dfe.nfe.ChaveAcesso;
import br.com.sistemanfe.dfe.teste.CertificadosDeTeste;
import br.com.sistemanfe.dfe.teste.RespostasSefaz;
import br.com.sistemanfe.dfe.teste.SefazFalsa;
import br.com.sistemanfe.dfe.ws.CatalogoWebServices;
import br.com.sistemanfe.dfe.ws.ClienteSefaz;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Consulta protocolo de ponta a ponta contra uma Sefaz falsa local que exige o certificado do cliente. */
class ConsultaProtocoloSefazFalsaTest {

    private static final char[] SENHA = "teste".toCharArray();
    private static final String CAMINHO = "/ws/NfeConsulta/NfeConsulta4.asmx";

    private final CertificadosDeTeste.Gerado empresa =
            CertificadosDeTeste.eCnpj("EMPRESA TESTE LTDA", "11222333000181", null, 365);
    private final CertificadosDeTeste.Gerado servidor = CertificadosDeTeste.servidorLocalhost();

    @Test
    void consultaNotaAutorizadaPelaChaveComTlsMutuo() {
        try (SefazFalsa sefaz = SefazFalsa.subir(empresa, servidor, CAMINHO, RespostasSefaz.CONSULTA_AUTORIZADA)) {
            RetornoConsultaProtocolo r = servico(sefaz)
                    .consultar(ChaveAcesso.de(RespostasSefaz.CHAVE_AUTORIZADA), Ambiente.HOMOLOGACAO);

            assertThat(r.autorizada()).isTrue();
            assertThat(r.protocolo().nProt()).isEqualTo("143260000012345");
            assertThat(sefaz.clienteApresentado()).contains("CN=EMPRESA TESTE LTDA");
            assertThat(sefaz.contentTypeRecebido())
                    .startsWith("application/soap+xml")
                    .contains("action=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeConsultaProtocolo4/nfeConsultaNF\"");
            assertThat(sefaz.corpoRecebido()).contains(
                    "<nfeDadosMsg xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeConsultaProtocolo4\">"
                            + "<consSitNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">"
                            + "<tpAmb>2</tpAmb><xServ>CONSULTAR</xServ>"
                            + "<chNFe>43261111222333000181550010000001231876543212</chNFe>"
                            + "</consSitNFe></nfeDadosMsg>");
        }
    }

    @Test
    void chaveNuncaEmitidaVoltaComRejeicao217() {
        try (SefazFalsa sefaz = SefazFalsa.subir(empresa, servidor, CAMINHO, RespostasSefaz.CONSULTA_INEXISTENTE)) {
            RetornoConsultaProtocolo r = servico(sefaz)
                    .consultar(ChaveAcesso.de(RespostasSefaz.CHAVE_INEXISTENTE), Ambiente.HOMOLOGACAO);

            assertThat(r.naoConsta()).isTrue();
            assertThat(r.cStat()).isEqualTo("217");
            assertThat(r.protocolo()).isNull();
            assertThat(sefaz.corpoRecebido()).contains("<chNFe>" + RespostasSefaz.CHAVE_INEXISTENTE + "</chNFe>");
        }
    }

    private ConsultaProtocoloServico servico(SefazFalsa sefaz) {
        CertificadoA1 cert = CertificadoA1.carregar(empresa.comoPfx(SENHA), SENHA);
        ClienteSefaz cliente = ClienteSefaz.para(cert)
                .confiancaExtra(servidor.comoTruststore())
                .proxy(null)
                .construir();
        return new ConsultaProtocoloServico(cliente, CatalogoWebServices.fixo(sefaz.endereco()));
    }
}
