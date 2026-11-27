package br.com.sistemanfe.dfe.servico;

import br.com.sistemanfe.dfe.comum.Ambiente;
import br.com.sistemanfe.dfe.comum.ModeloDocumento;
import br.com.sistemanfe.dfe.comum.UF;
import br.com.sistemanfe.dfe.ws.CatalogoWebServices;
import br.com.sistemanfe.dfe.ws.ClienteSefaz;
import br.com.sistemanfe.dfe.ws.ServicoSefaz;
import br.com.sistemanfe.dfe.xml.ValidadorXsd;
import br.com.sistemanfe.dfe.xml.Xml;
import br.inf.portalfiscal.nfe.ObjectFactory;
import br.inf.portalfiscal.nfe.TConsStatServ;
import br.inf.portalfiscal.nfe.TRetConsStatServ;
import org.w3c.dom.Element;

import java.net.URI;

/** Consulta se o web service da Sefaz está em operação ({@code NFeStatusServico4}). */
public final class ConsultaStatusServico {

    static final String VERSAO = "4.00";
    private static final ObjectFactory FABRICA = new ObjectFactory();

    private final ClienteSefaz cliente;
    private final CatalogoWebServices catalogo;

    public ConsultaStatusServico(ClienteSefaz cliente, CatalogoWebServices catalogo) {
        this.cliente = cliente;
        this.catalogo = catalogo;
    }

    public RetornoStatusServico consultar(UF uf, ModeloDocumento modelo, Ambiente ambiente) {
        String mensagem = montarMensagem(uf, ambiente);
        URI endereco = catalogo.endereco(uf, modelo, ambiente, ServicoSefaz.STATUS_SERVICO);
        Element retorno = cliente.enviar(endereco, ServicoSefaz.STATUS_SERVICO, mensagem);
        return lerRetorno(retorno);
    }

    /** XML {@code <consStatServ>} já validado contra o schema. */
    static String montarMensagem(UF uf, Ambiente ambiente) {
        TConsStatServ cons = new TConsStatServ();
        cons.setVersao(VERSAO);
        cons.setTpAmb(ambiente.codigo());
        cons.setCUF(uf.codigoIbge());
        cons.setXServ("STATUS");
        String xml = Xml.paraTexto(FABRICA.createConsStatServ(cons));
        ValidadorXsd.para("consStatServ_v4.00.xsd").validarOuFalhar(xml);
        return xml;
    }

    static RetornoStatusServico lerRetorno(Element retorno) {
        TRetConsStatServ r = Xml.paraObjeto(retorno, TRetConsStatServ.class);
        Integer tMed = null;
        if (r.getTMed() != null && !r.getTMed().isBlank()) {
            tMed = Integer.valueOf(r.getTMed().trim());
        }
        return new RetornoStatusServico(
                r.getCStat(), r.getXMotivo(), r.getTpAmb(), r.getCUF(), r.getVerAplic(),
                r.getDhRecbto(), tMed, r.getXObs(), Xml.paraTexto(retorno));
    }
}
