package br.com.sistemanfe.dfe.nfe;

import br.com.sistemanfe.dfe.DfeException;
import br.com.sistemanfe.dfe.assinatura.AssinadorXml;
import br.com.sistemanfe.dfe.certificado.CertificadoA1;
import br.com.sistemanfe.dfe.xml.Xml;
import br.inf.portalfiscal.nfe.TNFe;
import jakarta.xml.bind.JAXBElement;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.namespace.QName;

/**
 * NF-e com a {@code infNFe} assinada pelo certificado do emitente: o {@code <NFe>} que vai no lote e, depois da
 * autorização, no {@code nfeProc}.
 *
 * <p>O texto é guardado pronto: a assinatura vale para esses bytes, então a nota não deve ser remontada nem
 * reformatada depois de assinada.
 */
public final class NFeAssinada {

    private static final QName NFE = new QName(Xml.NS_NFE, "NFe");
    private static final int TAMANHO_RAIZ_CNPJ = 8;

    private final ChaveAcesso chave;
    private final String xml;

    private NFeAssinada(ChaveAcesso chave, String xml) {
        this.chave = chave;
        this.xml = xml;
    }

    /**
     * Assina a nota montada pelo {@link MontadorNFe}.
     *
     * @throws DfeException se o certificado não tiver CNPJ ou se a raiz do CNPJ do certificado for diferente da
     *                      do emitente (a Sefaz rejeitaria com o código 213)
     */
    public static NFeAssinada assinar(TNFe nfe, CertificadoA1 certificado) {
        String cnpjEmitente = nfe.getInfNFe().getEmit().getCNPJ();
        String cnpjCertificado = certificado.cnpj().orElseThrow(() -> new DfeException(
                "O certificado de " + certificado.nomeTitular() + " não tem CNPJ: a NF-e precisa ser assinada "
                        + "pelo e-CNPJ do emitente."));
        if (!raiz(cnpjCertificado).equals(raiz(cnpjEmitente))) {
            throw new DfeException("O certificado é do CNPJ " + cnpjCertificado + ", mas o emitente da nota é o "
                    + cnpjEmitente + ". A raiz do CNPJ (8 primeiros caracteres) precisa ser a mesma "
                    + "(rejeição 213).");
        }

        Document doc = Xml.paraDocumento(new JAXBElement<>(NFE, TNFe.class, nfe));
        Element infNFe = (Element) doc.getDocumentElement().getElementsByTagNameNS(Xml.NS_NFE, "infNFe").item(0);
        AssinadorXml.assinar(infNFe, certificado);
        return new NFeAssinada(ChaveAcesso.de(nfe.getInfNFe().getId().substring("NFe".length())),
                Xml.paraTexto(doc));
    }

    private static String raiz(String cnpj) {
        return cnpj.length() < TAMANHO_RAIZ_CNPJ ? cnpj : cnpj.substring(0, TAMANHO_RAIZ_CNPJ);
    }

    public ChaveAcesso chave() {
        return chave;
    }

    /** {@code <NFe xmlns="http://www.portalfiscal.inf.br/nfe">...<Signature>...</Signature></NFe>}. */
    public String xml() {
        return xml;
    }

    @Override
    public String toString() {
        return "NFeAssinada[" + chave + "]";
    }
}
