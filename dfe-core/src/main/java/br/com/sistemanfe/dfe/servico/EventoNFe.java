package br.com.sistemanfe.dfe.servico;

/**
 * Evento vinculado à NF-e ({@code procEventoNFe}: o evento enviado mais o retorno da Sefaz).
 *
 * @param tpEvento    tipo do evento, ex.: 110111 = cancelamento, 110110 = carta de correção
 * @param nSeqEvento  sequencial do evento para o mesmo tipo
 * @param dhEvento    data e hora do evento informada pelo autor
 * @param cStat       situação do registro do evento, ex.: 135 = registrado e vinculado à NF-e
 * @param xMotivo     descrição da situação do registro
 * @param xEvento     descrição do evento devolvida pela Sefaz (pode ser nula)
 * @param nProt       protocolo de registro do evento (pode ser nulo)
 * @param dhRegEvento data e hora do registro do evento
 * @param xml         o {@code <procEventoNFe>} como veio, no namespace da NF-e
 */
public record EventoNFe(
        String tpEvento,
        String nSeqEvento,
        String dhEvento,
        String cStat,
        String xMotivo,
        String xEvento,
        String nProt,
        String dhRegEvento,
        String xml) {
}
