package br.com.sistemanfe.dfe.assinatura;

import br.com.sistemanfe.dfe.DfeException;
import br.com.sistemanfe.dfe.certificado.CertificadoA1;
import br.com.sistemanfe.dfe.comum.FormaEmissao;
import br.com.sistemanfe.dfe.comum.ModeloDocumento;
import br.com.sistemanfe.dfe.comum.UF;
import br.com.sistemanfe.dfe.nfe.ChaveAcesso;
import br.com.sistemanfe.dfe.teste.CertificadosDeTeste;
import br.com.sistemanfe.dfe.teste.ConferenciaAssinatura;
import br.com.sistemanfe.dfe.teste.NotasDeTeste;
import br.com.sistemanfe.dfe.xml.Xml;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.crypto.MarshalException;
import javax.xml.crypto.dsig.XMLSignature;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssinadorXmlTest {

    private static final String ID = ChaveAcesso.gerar(UF.RS, YearMonth.of(2026, 11), NotasDeTeste.CNPJ_EMITENTE,
            ModeloDocumento.NFE, 1, 42, FormaEmissao.NORMAL, "12345670").idInfNFe();
    private static final String NFE = "<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
            + "<infNFe versao=\"4.00\" Id=\"" + ID + "\"><ide><cUF>43</cUF><natOp>VENDA &amp; TROCA</natOp></ide>"
            + "</infNFe></NFe>";
    private static final char[] SENHA = "teste".toCharArray();

    private static CertificadoA1 certificado;

    @BeforeAll
    static void criarCertificado() {
        CertificadosDeTeste.Gerado gerado = CertificadosDeTeste.eCnpj("EMPRESA FICTICIA DE TESTE LTDA",
                NotasDeTeste.CNPJ_EMITENTE, null, 30);
        certificado = CertificadoA1.carregar(gerado.comoPfx(SENHA), SENHA);
    }

    @Test
    void assinaturaConfereComAChavePublicaDoCertificado() throws Exception {
        String xml = assinar(NFE);

        assertThat(ConferenciaAssinatura.confere(xml, certificado.certificado().getPublicKey())).isTrue();
    }

    @Test
    void alterarOXmlDepoisDeAssinarInvalidaAAssinatura() throws Exception {
        String adulterado = assinar(NFE).replace("<cUF>43</cUF>", "<cUF>42</cUF>");

        assertThat(ConferenciaAssinatura.confere(adulterado, certificado.certificado().getPublicKey())).isFalse();
    }

    @Test
    void assinaturaNaoConfereComOutraChave() throws Exception {
        CertificadosDeTeste.Gerado outro = CertificadosDeTeste.eCnpj("OUTRA EMPRESA FICTICIA",
                NotasDeTeste.CNPJ_DESTINATARIO, null, 30);

        assertThat(ConferenciaAssinatura.confere(assinar(NFE), outro.certificado().getPublicKey())).isFalse();
    }

    @Test
    void noModoSeguroPadraoOJavaRecusaValidarRsaSha1() {
        String xml = assinar(NFE);

        assertThatThrownBy(() -> ConferenciaAssinatura.confere(xml, certificado.certificado().getPublicKey(), true))
                .isInstanceOf(MarshalException.class)
                .hasMessageContaining("rsa-sha1")
                .hasMessageContaining("secure validation");
    }

    @Test
    void referenciaApontaParaOIdDoElementoAssinado() {
        Element reference = unico(Xml.ler(assinar(NFE)), "Reference");

        assertThat(reference.getAttribute("URI")).isEqualTo("#" + ID);
    }

    @Test
    void usaOsAlgoritmosExigidosPelaSefaz() {
        Document doc = Xml.ler(assinar(NFE));

        assertThat(unico(doc, "CanonicalizationMethod").getAttribute("Algorithm"))
                .isEqualTo("http://www.w3.org/TR/2001/REC-xml-c14n-20010315");
        assertThat(unico(doc, "SignatureMethod").getAttribute("Algorithm"))
                .isEqualTo("http://www.w3.org/2000/09/xmldsig#rsa-sha1");
        assertThat(algoritmos(doc, "Transform")).containsExactly(
                "http://www.w3.org/2000/09/xmldsig#enveloped-signature",
                "http://www.w3.org/TR/2001/REC-xml-c14n-20010315");
        assertThat(unico(doc, "DigestMethod").getAttribute("Algorithm"))
                .isEqualTo("http://www.w3.org/2000/09/xmldsig#sha1");
    }

    @Test
    void keyInfoTrazSoOCertificadoDoTitular() throws Exception {
        Document doc = Xml.ler(assinar(NFE));

        Element keyInfo = unico(doc, "KeyInfo");
        assertThat(filhos(keyInfo)).extracting(Node::getLocalName).containsExactly("X509Data");
        Element x509Data = (Element) keyInfo.getFirstChild();
        assertThat(filhos(x509Data)).extracting(Node::getLocalName).containsExactly("X509Certificate");
        byte[] certificadoNoXml = Base64.getDecoder().decode(x509Data.getFirstChild().getTextContent());
        assertThat(certificadoNoXml).isEqualTo(certificado.certificado().getEncoded());
    }

    @Test
    void assinaturaEhOUltimoFilhoDoPaiNoNamespaceDoXmldsigSemPrefixo() {
        String xml = assinar(NFE);
        Element nfe = Xml.ler(xml).getDocumentElement();

        List<Element> filhos = filhos(nfe);
        assertThat(filhos).extracting(Node::getLocalName).containsExactly("infNFe", "Signature");
        Element assinatura = filhos.get(1);
        assertThat(assinatura.getNamespaceURI()).isEqualTo(XMLSignature.XMLNS);
        assertThat(assinatura.getPrefix()).isNull();
        assertThat(xml).contains("</infNFe><Signature xmlns=\"http://www.w3.org/2000/09/xmldsig#\"><SignedInfo>")
                .endsWith("</Signature></NFe>");
    }

    @Test
    void xmlAssinadoNaoTemQuebrasDeLinha() {
        String xml = assinar(NFE);

        assertThat(xml).doesNotContain("\n", "\r", "&#13;", "&#xD;");
    }

    @Test
    void elementoSemIdNaoPodeSerAssinado() {
        Document doc = Xml.ler("<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\"><infNFe versao=\"4.00\"/></NFe>");
        Element infNFe = (Element) doc.getDocumentElement().getFirstChild();

        assertThatThrownBy(() -> AssinadorXml.assinar(infNFe, certificado))
                .isInstanceOf(DfeException.class)
                .hasMessageContaining("Id");
    }

    private static String assinar(String xml) {
        Document doc = Xml.ler(xml);
        Element infNFe = (Element) doc.getDocumentElement().getFirstChild();
        AssinadorXml.assinar(infNFe, certificado);
        return Xml.paraTexto(doc);
    }

    private static Element unico(Document doc, String nome) {
        assertThat(doc.getElementsByTagNameNS(XMLSignature.XMLNS, nome).getLength()).as(nome).isEqualTo(1);
        return (Element) doc.getElementsByTagNameNS(XMLSignature.XMLNS, nome).item(0);
    }

    private static List<String> algoritmos(Document doc, String nome) {
        List<String> algoritmos = new ArrayList<>();
        for (int i = 0; i < doc.getElementsByTagNameNS(XMLSignature.XMLNS, nome).getLength(); i++) {
            algoritmos.add(((Element) doc.getElementsByTagNameNS(XMLSignature.XMLNS, nome).item(i))
                    .getAttribute("Algorithm"));
        }
        return algoritmos;
    }

    private static List<Element> filhos(Element pai) {
        List<Element> filhos = new ArrayList<>();
        for (Node n = pai.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e) {
                filhos.add(e);
            }
        }
        return filhos;
    }
}
