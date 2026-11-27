package br.com.sistemanfe.dfe.servico;

import br.com.sistemanfe.dfe.comum.Ambiente;
import br.com.sistemanfe.dfe.nfe.ChaveAcesso;
import br.com.sistemanfe.dfe.ws.CatalogoWebServices;
import br.com.sistemanfe.dfe.ws.ClienteSefaz;
import br.com.sistemanfe.dfe.ws.ServicoSefaz;
import br.com.sistemanfe.dfe.xml.ValidadorXsd;
import br.com.sistemanfe.dfe.xml.Xml;
import br.inf.portalfiscal.nfe.conssit.ObjectFactory;
import br.inf.portalfiscal.nfe.conssit.TConsSitNFe;
import br.inf.portalfiscal.nfe.conssit.TEvento;
import br.inf.portalfiscal.nfe.conssit.TProcEvento;
import br.inf.portalfiscal.nfe.conssit.TProtNFe;
import br.inf.portalfiscal.nfe.conssit.TRetConsSitNFe;
import br.inf.portalfiscal.nfe.conssit.TRetEvento;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Consulta a situação atual da NF-e/NFC-e pela chave de acesso ({@code NFeConsultaProtocolo4}).
 *
 * <p>É o caminho para recuperar uma nota depois de um timeout no envio ou de uma rejeição 204
 * (duplicidade): a nota nunca é reenviada com outro número. O endereço sai da UF e do modelo da chave.
 */
public final class ConsultaProtocoloServico {

    static final String VERSAO = "4.00";
    private static final ObjectFactory FABRICA = new ObjectFactory();

    private final ClienteSefaz cliente;
    private final CatalogoWebServices catalogo;

    public ConsultaProtocoloServico(ClienteSefaz cliente, CatalogoWebServices catalogo) {
        this.cliente = cliente;
        this.catalogo = catalogo;
    }

    public RetornoConsultaProtocolo consultar(ChaveAcesso chave, Ambiente ambiente) {
        String mensagem = montarMensagem(chave, ambiente);
        Element retorno = cliente.enviar(endereco(chave, ambiente), ServicoSefaz.CONSULTA_PROTOCOLO, mensagem);
        return lerRetorno(retorno);
    }

    URI endereco(ChaveAcesso chave, Ambiente ambiente) {
        return catalogo.endereco(chave.uf(), chave.modelo(), ambiente, ServicoSefaz.CONSULTA_PROTOCOLO);
    }

    /** XML {@code <consSitNFe>} já validado contra o schema. */
    static String montarMensagem(ChaveAcesso chave, Ambiente ambiente) {
        TConsSitNFe cons = new TConsSitNFe();
        cons.setVersao(VERSAO);
        cons.setTpAmb(ambiente.codigo());
        cons.setXServ("CONSULTAR");
        cons.setChNFe(chave.valor());
        String xml = Xml.paraTexto(FABRICA.createConsSitNFe(cons));
        ValidadorXsd.para("consSitNFe_v4.00.xsd").validarOuFalhar(xml);
        return xml;
    }

    static RetornoConsultaProtocolo lerRetorno(Element retorno) {
        TRetConsSitNFe r = Xml.paraObjeto(retorno, TRetConsSitNFe.class);

        ProtocoloNFe protocolo = null;
        List<Element> protNFe = filhos(retorno, "protNFe");
        if (r.getProtNFe() != null && !protNFe.isEmpty()) {
            protocolo = protocolo(r.getProtNFe(), Xml.paraTexto(protNFe.get(0)));
        }

        List<Element> procEventos = filhos(retorno, "procEventoNFe");
        List<EventoNFe> eventos = new ArrayList<>();
        for (int i = 0; i < r.getProcEventoNFe().size(); i++) {
            eventos.add(evento(r.getProcEventoNFe().get(i), Xml.paraTexto(procEventos.get(i))));
        }

        return new RetornoConsultaProtocolo(
                r.getCStat(), r.getXMotivo(), r.getTpAmb(), r.getCUF(), r.getVerAplic(), r.getDhRecbto(),
                r.getChNFe(), protocolo, List.copyOf(eventos), Xml.paraTexto(retorno));
    }

    private static ProtocoloNFe protocolo(TProtNFe prot, String xml) {
        TProtNFe.InfProt inf = prot.getInfProt();
        return new ProtocoloNFe(
                inf.getTpAmb(), inf.getVerAplic(), inf.getChNFe(), inf.getNProt(),
                inf.getDhRecbto() == null ? null : inf.getDhRecbto().toXMLFormat(),
                inf.getCStat(), inf.getXMotivo(),
                inf.getDigVal() == null ? null : Base64.getEncoder().encodeToString(inf.getDigVal()),
                xml);
    }

    private static EventoNFe evento(TProcEvento proc, String xml) {
        TEvento.InfEvento ev = proc.getEvento().getInfEvento();
        TRetEvento.InfEvento ret = proc.getRetEvento().getInfEvento();
        return new EventoNFe(
                ev.getTpEvento(), ev.getNSeqEvento(), ev.getDhEvento(),
                ret.getCStat(), ret.getXMotivo(), ret.getXEvento(), ret.getNProt(), ret.getDhRegEvento(),
                xml);
    }

    /** Filhos diretos de {@code pai} no namespace da NF-e com o nome local indicado, na ordem do XML. */
    private static List<Element> filhos(Element pai, String nomeLocal) {
        List<Element> encontrados = new ArrayList<>();
        NodeList nos = pai.getChildNodes();
        for (int i = 0; i < nos.getLength(); i++) {
            Node n = nos.item(i);
            if (n instanceof Element e && Xml.NS_NFE.equals(e.getNamespaceURI()) && nomeLocal.equals(e.getLocalName())) {
                encontrados.add(e);
            }
        }
        return encontrados;
    }
}
