package br.com.sistemanfe.dfe.ws;

import br.com.sistemanfe.dfe.xml.Xml;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Monta e desmonta o envelope SOAP 1.2 usado pelos web services da NF-e 4.00. */
public final class EnvelopeSoap {

    public static final String NS_SOAP12 = "http://www.w3.org/2003/05/soap-envelope";

    private EnvelopeSoap() {
    }

    /**
     * Envolve a mensagem (ex.: {@code <consStatServ>}) no {@code nfeDadosMsg} do serviço.
     * A mensagem não pode ter declaração {@code <?xml ...?>}.
     */
    public static String montar(ServicoSefaz servico, String mensagemXml) {
        String msg = mensagemXml.strip();
        if (msg.startsWith("<?xml")) {
            msg = msg.substring(msg.indexOf("?>") + 2).strip();
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<soap12:Envelope xmlns:soap12=\"" + NS_SOAP12 + "\">"
                + "<soap12:Body>"
                + "<nfeDadosMsg xmlns=\"" + servico.namespaceWsdl() + "\">" + msg + "</nfeDadosMsg>"
                + "</soap12:Body>"
                + "</soap12:Envelope>";
    }

    /**
     * Extrai a mensagem de retorno de dentro do {@code nfeResultMsg}.
     *
     * @throws SefazComunicacaoException se vier um SOAP Fault ou um envelope inesperado
     */
    public static Element extrairRetorno(String respostaSoap) {
        Document doc = Xml.ler(respostaSoap);
        Element body = primeiroFilho(doc.getDocumentElement(), "Body");
        if (body == null) {
            throw new SefazComunicacaoException("Resposta da Sefaz sem SOAP Body.", true);
        }
        Element fault = primeiroFilho(body, "Fault");
        if (fault != null) {
            throw new SefazComunicacaoException("SOAP Fault da Sefaz: " + textoFault(fault), true);
        }
        Element resultado = primeiroFilho(body, "nfeResultMsg");
        if (resultado == null) {
            throw new SefazComunicacaoException("Resposta da Sefaz sem nfeResultMsg.", true);
        }
        Element retorno = primeiroElemento(resultado);
        if (retorno == null) {
            throw new SefazComunicacaoException("nfeResultMsg vazio na resposta da Sefaz.", true);
        }
        return retorno;
    }

    private static String textoFault(Element fault) {
        Element reason = primeiroFilho(fault, "Reason");
        String texto = (reason != null ? reason : fault).getTextContent();
        return texto == null ? "(sem descrição)" : texto.strip().replaceAll("\\s+", " ");
    }

    private static Element primeiroFilho(Element pai, String nomeLocal) {
        NodeList filhos = pai.getChildNodes();
        for (int i = 0; i < filhos.getLength(); i++) {
            Node n = filhos.item(i);
            if (n instanceof Element e && nomeLocal.equals(e.getLocalName())) {
                return e;
            }
        }
        return null;
    }

    private static Element primeiroElemento(Element pai) {
        NodeList filhos = pai.getChildNodes();
        for (int i = 0; i < filhos.getLength(); i++) {
            if (filhos.item(i) instanceof Element e) {
                return e;
            }
        }
        return null;
    }
}
