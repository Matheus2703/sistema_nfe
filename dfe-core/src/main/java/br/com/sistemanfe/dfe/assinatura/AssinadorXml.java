package br.com.sistemanfe.dfe.assinatura;

import br.com.sistemanfe.dfe.DfeException;
import br.com.sistemanfe.dfe.certificado.CertificadoA1;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.crypto.MarshalException;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureException;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Assina elementos XML no padrão exigido pela Sefaz para NF-e, NFC-e, eventos e inutilização.
 *
 * <ul>
 *   <li>Assinatura <em>enveloped</em> sobre o elemento com atributo {@code Id} ({@code infNFe},
 *       {@code infEvento}...), com {@code Reference URI="#Id"};</li>
 *   <li>canonicalização C14N (inclusiva, sem comentários), RSA-SHA1 e digest SHA-1;</li>
 *   <li>{@code KeyInfo} só com o {@code X509Certificate} do titular;</li>
 *   <li>{@code <Signature xmlns="http://www.w3.org/2000/09/xmldsig#">} sem prefixo, como último filho do pai do
 *       elemento assinado (na NF-e, depois de {@code infNFe} e do {@code infNFeSupl}).</li>
 * </ul>
 */
public final class AssinadorXml {

    private AssinadorXml() {
    }

    /** Assina o elemento e acrescenta a {@code <Signature>} ao final do elemento pai. */
    public static void assinar(Element elemento, CertificadoA1 certificado) {
        String id = elemento.getAttribute("Id");
        if (id.isEmpty()) {
            throw new DfeException("O elemento " + elemento.getLocalName()
                    + " não tem o atributo Id, que a assinatura referencia.");
        }
        // Sem schema, o DOM não sabe que "Id" é um ID: sem isto a referência "#Id" não é encontrada.
        elemento.setIdAttributeNS(null, "Id", true);

        XMLSignatureFactory fabrica = XMLSignatureFactory.getInstance("DOM");
        try {
            List<Transform> transformacoes = List.of(
                    fabrica.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null),
                    fabrica.newTransform(CanonicalizationMethod.INCLUSIVE, (TransformParameterSpec) null));
            Reference referencia = fabrica.newReference("#" + id, fabrica.newDigestMethod(DigestMethod.SHA1, null),
                    transformacoes, null, null);
            SignedInfo signedInfo = fabrica.newSignedInfo(
                    fabrica.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE,
                            (C14NMethodParameterSpec) null),
                    fabrica.newSignatureMethod(SignatureMethod.RSA_SHA1, null),
                    List.of(referencia));

            KeyInfoFactory fabricaKeyInfo = fabrica.getKeyInfoFactory();
            KeyInfo keyInfo = fabricaKeyInfo.newKeyInfo(
                    List.of(fabricaKeyInfo.newX509Data(List.of(certificado.certificado()))));

            DOMSignContext contexto = new DOMSignContext(certificado.chavePrivada(), elemento.getParentNode());
            fabrica.newXMLSignature(signedInfo, keyInfo).sign(contexto);
        } catch (GeneralSecurityException | MarshalException | XMLSignatureException e) {
            throw new DfeException("Erro ao assinar o XML: " + e.getMessage(), e);
        }
        removerQuebrasDeLinha((Element) elemento.getParentNode().getLastChild());
    }

    /**
     * O JDK quebra o Base64 a cada 76 caracteres com CR LF, e o CR vira {@code &#13;} no texto, que a Sefaz
     * recusa. {@code SignatureValue} e {@code X509Certificate} ficam fora do {@code SignedInfo}: tirar as
     * quebras não muda o que foi assinado.
     */
    private static void removerQuebrasDeLinha(Element assinatura) {
        for (String nome : List.of("SignatureValue", "X509Certificate")) {
            NodeList nos = assinatura.getElementsByTagNameNS(XMLSignature.XMLNS, nome);
            for (int i = 0; i < nos.getLength(); i++) {
                Node no = nos.item(i);
                no.setTextContent(no.getTextContent().replaceAll("\\s", ""));
            }
        }
    }
}
