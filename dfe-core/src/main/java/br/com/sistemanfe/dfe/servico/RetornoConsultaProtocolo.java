package br.com.sistemanfe.dfe.servico;

import java.util.List;

/**
 * Retorno da consulta da situação da NF-e pela chave de acesso ({@code retConsSitNFe}).
 *
 * @param cStat      situação atual da nota: 100 = autorizada, 101 = cancelada, 110 = uso denegado,
 *                   217 = não consta na base da Sefaz; outros códigos são rejeições da própria consulta
 * @param xMotivo    descrição da situação
 * @param chNFe      chave de acesso consultada
 * @param protocolo  protocolo de autorização ou de denegação; {@code null} quando a Sefaz não o devolve
 * @param eventos    eventos vinculados à nota (cancelamento, carta de correção...), na ordem do XML
 * @param xmlRetorno XML original devolvido pela Sefaz (para auditoria)
 */
public record RetornoConsultaProtocolo(
        String cStat,
        String xMotivo,
        String tpAmb,
        String cUF,
        String verAplic,
        String dhRecbto,
        String chNFe,
        ProtocoloNFe protocolo,
        List<EventoNFe> eventos,
        String xmlRetorno) {

    public static final String AUTORIZADA = "100";
    public static final String CANCELADA = "101";
    public static final String DENEGADA = "110";
    public static final String AUTORIZADA_FORA_DE_PRAZO = "150";
    public static final String CANCELADA_FORA_DE_PRAZO = "151";
    /** "Rejeição: NF-e não consta na base de dados da SEFAZ" (MOC, regra J03 da consulta protocolo). */
    public static final String NAO_CONSTA = "217";

    /** Autorizada, dentro ou fora do prazo (cStat 100 ou 150). */
    public boolean autorizada() {
        return AUTORIZADA.equals(cStat) || AUTORIZADA_FORA_DE_PRAZO.equals(cStat);
    }

    /** Cancelada, dentro ou fora do prazo (cStat 101 ou 151). */
    public boolean cancelada() {
        return CANCELADA.equals(cStat) || CANCELADA_FORA_DE_PRAZO.equals(cStat);
    }

    /** Uso denegado (cStat 110): o número foi consumido e a nota não pode ser usada. */
    public boolean denegada() {
        return DENEGADA.equals(cStat);
    }

    /** A Sefaz não tem nenhuma NF-e com esta chave (cStat 217). */
    public boolean naoConsta() {
        return NAO_CONSTA.equals(cStat);
    }
}
