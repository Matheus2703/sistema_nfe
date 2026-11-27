package br.com.sistemanfe.dfe.servico;

/**
 * Protocolo da NF-e ({@code protNFe}) devolvido pela Sefaz.
 *
 * @param chNFe    chave de acesso da nota
 * @param nProt    número do protocolo (vazio em rejeição)
 * @param dhRecbto data e hora do processamento, como veio no XML
 * @param cStat    situação da nota: 100 = autorizada, 110 = uso denegado...
 * @param xMotivo  descrição da situação
 * @param digVal   digest da NF-e processada (Base64), para conferir com a assinatura da nota
 * @param xml      o {@code <protNFe>} como veio, no namespace da NF-e, para montar o {@code nfeProc}
 */
public record ProtocoloNFe(
        String tpAmb,
        String verAplic,
        String chNFe,
        String nProt,
        String dhRecbto,
        String cStat,
        String xMotivo,
        String digVal,
        String xml) {
}
