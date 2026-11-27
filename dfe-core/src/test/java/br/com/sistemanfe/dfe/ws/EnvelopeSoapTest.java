package br.com.sistemanfe.dfe.ws;

import br.com.sistemanfe.dfe.DfeException;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvelopeSoapTest {

    @Test
    void montaEnvelopeSoap12ComNamespaceDoServico() {
        String env = EnvelopeSoap.montar(ServicoSefaz.STATUS_SERVICO, "<?xml version=\"1.0\"?><consStatServ/>");

        assertThat(env).isEqualTo("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\"><soap12:Body>"
                + "<nfeDadosMsg xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeStatusServico4\">"
                + "<consStatServ/></nfeDadosMsg></soap12:Body></soap12:Envelope>");
    }

    @Test
    void soapActionSegueOPadraoDoWsdl() {
        assertThat(ServicoSefaz.STATUS_SERVICO.soapAction())
                .isEqualTo("http://www.portalfiscal.inf.br/nfe/wsdl/NFeStatusServico4/nfeStatusServicoNF");
        assertThat(ServicoSefaz.AUTORIZACAO.soapAction())
                .isEqualTo("http://www.portalfiscal.inf.br/nfe/wsdl/NFeAutorizacao4/nfeAutorizacaoLote");
    }

    @Test
    void extraiPrimeiroElementoDoNfeResultMsg() {
        Element e = EnvelopeSoap.extrairRetorno(
                br.com.sistemanfe.dfe.teste.RespostasSefaz.STATUS_107);

        assertThat(e.getLocalName()).isEqualTo("retConsStatServ");
    }

    @Test
    void soapFaultViraExcecaoComOTexto() {
        String fault = "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\"><soap:Body>"
                + "<soap:Fault><soap:Code><soap:Value>soap:Receiver</soap:Value></soap:Code>"
                + "<soap:Reason><soap:Text xml:lang=\"pt\">Servidor indisponivel</soap:Text></soap:Reason>"
                + "</soap:Fault></soap:Body></soap:Envelope>";

        assertThatThrownBy(() -> EnvelopeSoap.extrairRetorno(fault))
                .isInstanceOf(SefazComunicacaoException.class)
                .hasMessageContaining("Servidor indisponivel");
    }

    @Test
    void recusaDoctypeParaEvitarXxe() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]>"
                + "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\"><soap:Body>&e;</soap:Body></soap:Envelope>";

        assertThatThrownBy(() -> EnvelopeSoap.extrairRetorno(xxe)).isInstanceOf(DfeException.class);
    }
}
