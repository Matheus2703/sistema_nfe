package br.com.sistemanfe.dfe.teste;

import br.com.sistemanfe.dfe.xml.Xml;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.crypto.KeySelector;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import java.security.PublicKey;

/**
 * Confere assinaturas XMLDSig nos testes, como a Sefaz faz ao receber o XML.
 *
 * <p>Desde o JDK 17 o Java valida assinaturas no "modo seguro", que recusa RSA-SHA1 e SHA-1. A NF-e ainda usa
 * esses algoritmos, então o modo seguro é desligado só no {@link DOMValidateContext} desta conferência.
 * Assinar não é afetado.
 */
public final class ConferenciaAssinatura {

    public static final String MODO_SEGURO = "org.jcp.xml.dsig.secureValidation";

    private ConferenciaAssinatura() {
    }

    /** {@code true} se a única assinatura do XML confere com a chave pública informada. */
    public static boolean confere(String xml, PublicKey chavePublica) throws Exception {
        return confere(xml, chavePublica, false);
    }

    /**
     * @param modoSeguro {@code true} para validar como o JDK faz por padrão (recusa SHA-1)
     */
    public static boolean confere(String xml, PublicKey chavePublica, boolean modoSeguro) throws Exception {
        Document doc = Xml.ler(xml);
        NodeList assinaturas = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
        if (assinaturas.getLength() != 1) {
            throw new AssertionError("Esperava 1 assinatura, achei " + assinaturas.getLength());
        }
        Element assinatura = (Element) assinaturas.item(0);
        // O elemento assinado é o primeiro filho do pai da assinatura (infNFe, infEvento...).
        Element assinado = (Element) assinatura.getParentNode().getFirstChild();

        DOMValidateContext contexto = new DOMValidateContext(KeySelector.singletonKeySelector(chavePublica),
                assinatura);
        contexto.setIdAttributeNS(assinado, null, "Id");
        contexto.setProperty(MODO_SEGURO, modoSeguro);
        XMLSignature xmlSignature = XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(contexto);
        return xmlSignature.validate(contexto);
    }
}
