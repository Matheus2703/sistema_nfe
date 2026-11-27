package br.com.sistemanfe.dfe.xml;

import br.com.sistemanfe.dfe.DfeException;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conversões entre objetos JAXB, DOM e texto no formato que a Sefaz aceita.
 *
 * <p>Regras da Sefaz aplicadas aqui:
 * <ul>
 *   <li>Namespace da NF-e sempre como namespace padrão ({@code xmlns="..."}), sem prefixo
 *       ({@code ns2:}), senão a nota é rejeitada.</li>
 *   <li>Nenhuma declaração de namespace sobrando (o JAXB declara as que conhece, mesmo sem uso).</li>
 *   <li>Sem declaração {@code <?xml ...?>} dentro do envelope SOAP e sem quebras de linha/indentação.</li>
 * </ul>
 */
public final class Xml {

    public static final String NS_NFE = "http://www.portalfiscal.inf.br/nfe";
    public static final String NS_XMLDSIG = "http://www.w3.org/2000/09/xmldsig#";

    private static final Map<Class<?>, JAXBContext> CONTEXTOS = new ConcurrentHashMap<>();

    private Xml() {
    }

    /** Converte um objeto JAXB (normalmente um {@link JAXBElement}) em DOM já normalizado. */
    public static Document paraDocumento(Object jaxb) {
        Document doc = novoDocumento();
        try {
            Marshaller m = contexto(jaxb).createMarshaller();
            m.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
            m.marshal(jaxb, doc);
        } catch (JAXBException e) {
            throw new DfeException("Erro ao gerar XML de " + nomeTipo(jaxb), e);
        }
        normalizarNamespaces(doc);
        return doc;
    }

    /** Converte um objeto JAXB em texto pronto para envio. */
    public static String paraTexto(Object jaxb) {
        return paraTexto(paraDocumento(jaxb));
    }

    /** Serializa um nó DOM sem declaração XML e sem indentação. */
    public static String paraTexto(Node no) {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            Transformer t = tf.newTransformer();
            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            t.setOutputProperty(OutputKeys.INDENT, "no");
            StringWriter saida = new StringWriter();
            t.transform(new DOMSource(no), new StreamResult(saida));
            return saida.toString();
        } catch (TransformerException e) {
            throw new DfeException("Erro ao serializar XML.", e);
        }
    }

    /** Lê texto XML em DOM, com proteção contra XXE (DOCTYPE proibido). */
    public static Document ler(String xml) {
        try {
            DocumentBuilder db = fabricaSegura().newDocumentBuilder();
            db.setErrorHandler(null);
            return db.parse(new InputSource(new StringReader(xml)));
        } catch (SAXException | IOException | ParserConfigurationException e) {
            throw new DfeException("XML inválido: " + e.getMessage(), e);
        }
    }

    /** Converte um elemento DOM no tipo JAXB indicado. */
    public static <T> T paraObjeto(Node no, Class<T> tipo) {
        try {
            Unmarshaller u = contexto(tipo).createUnmarshaller();
            JAXBElement<T> el = u.unmarshal(no, tipo);
            return el.getValue();
        } catch (JAXBException e) {
            throw new DfeException("Erro ao ler XML como " + tipo.getSimpleName(), e);
        }
    }

    public static Document novoDocumento() {
        try {
            return fabricaSegura().newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Remove prefixos dos elementos da NF-e e declarações de namespace não usadas,
     * deixando {@code xmlns="http://www.portalfiscal.inf.br/nfe"} na raiz.
     */
    static void normalizarNamespaces(Document doc) {
        Element raiz = doc.getDocumentElement();
        if (raiz == null) {
            return;
        }
        raiz = (Element) renomearSemPrefixo(doc, raiz);
        removerDeclaracoes(raiz);
        if (NS_NFE.equals(raiz.getNamespaceURI())) {
            raiz.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns", NS_NFE);
        }
    }

    private static Node renomearSemPrefixo(Document doc, Element el) {
        Element atual = el;
        if (NS_NFE.equals(el.getNamespaceURI()) && el.getPrefix() != null) {
            atual = (Element) doc.renameNode(el, NS_NFE, el.getLocalName());
        }
        NodeList filhos = atual.getChildNodes();
        for (int i = 0; i < filhos.getLength(); i++) {
            if (filhos.item(i) instanceof Element filho) {
                renomearSemPrefixo(doc, filho);
            }
        }
        return atual;
    }

    private static void removerDeclaracoes(Element el) {
        NamedNodeMap atributos = el.getAttributes();
        List<Attr> remover = new ArrayList<>();
        for (int i = 0; i < atributos.getLength(); i++) {
            Attr a = (Attr) atributos.item(i);
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(a.getNamespaceURI())) {
                remover.add(a);
            }
        }
        remover.forEach(el::removeAttributeNode);
        NodeList filhos = el.getChildNodes();
        for (int i = 0; i < filhos.getLength(); i++) {
            if (filhos.item(i) instanceof Element filho) {
                removerDeclaracoes(filho);
            }
        }
    }

    private static DocumentBuilderFactory fabricaSegura() throws ParserConfigurationException {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        return f;
    }

    private static JAXBContext contexto(Object jaxb) {
        Class<?> tipo = jaxb instanceof JAXBElement<?> el ? el.getDeclaredType() : jaxb.getClass();
        return contexto(tipo);
    }

    private static JAXBContext contexto(Class<?> tipo) {
        return CONTEXTOS.computeIfAbsent(tipo, t -> {
            try {
                return JAXBContext.newInstance(t.getPackageName(), t.getClassLoader());
            } catch (JAXBException e) {
                throw new DfeException("Erro ao preparar JAXB para " + t.getName(), e);
            }
        });
    }

    private static String nomeTipo(Object jaxb) {
        return jaxb instanceof JAXBElement<?> el ? el.getName().getLocalPart() : jaxb.getClass().getSimpleName();
    }
}
