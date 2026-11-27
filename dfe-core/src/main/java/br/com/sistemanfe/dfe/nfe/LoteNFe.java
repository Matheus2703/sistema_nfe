package br.com.sistemanfe.dfe.nfe;

import br.com.sistemanfe.dfe.xml.ValidadorXsd;
import br.com.sistemanfe.dfe.xml.Xml;
import br.inf.portalfiscal.nfe.ObjectFactory;
import br.inf.portalfiscal.nfe.TEnviNFe;

/**
 * Lote {@code <enviNFe>} do serviço {@code NFeAutorizacao4}, já validado contra {@code enviNFe_v4.00.xsd}.
 *
 * <p>Usa sempre o processamento síncrono ({@code indSinc=1}), que aceita uma única NF-e por lote: a resposta já
 * traz o protocolo, sem consulta de recibo.
 */
public final class LoteNFe {

    static final String VERSAO = "4.00";
    static final String SCHEMA = "enviNFe_v4.00.xsd";
    private static final String SINCRONO = "1";
    private static final String FIM = "</enviNFe>";
    private static final ObjectFactory FABRICA = new ObjectFactory();

    private LoteNFe() {
    }

    /**
     * @param idLote número do lote, até 15 dígitos, controlado pelo emitente
     * @return o XML do lote, sem declaração {@code <?xml?>}, pronto para o corpo do SOAP
     * @throws br.com.sistemanfe.dfe.DfeException se o lote não passar no schema
     */
    public static String montar(String idLote, NFeAssinada nfe) {
        TEnviNFe envi = new TEnviNFe();
        envi.setVersao(VERSAO);
        envi.setIdLote(idLote);
        envi.setIndSinc(SINCRONO);
        String semNota = Xml.paraTexto(FABRICA.createEnviNFe(envi));
        if (!semNota.endsWith(FIM)) {
            throw new IllegalStateException("Cabeçalho do enviNFe inesperado: " + semNota);
        }

        // A NF-e entra com os mesmos bytes que foram assinados. Passar de novo por DOM ou JAXB pode mudar o texto
        // (o serializador do JDK, por exemplo, tira o xmlns repetido do <NFe>).
        String xml = semNota.substring(0, semNota.length() - FIM.length()) + nfe.xml() + FIM;
        ValidadorXsd.para(SCHEMA).validarOuFalhar(xml);
        return xml;
    }
}
