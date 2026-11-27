package br.com.sistemanfe.dfe.servico;

/**
 * Retorno do serviço de status ({@code retConsStatServ}).
 *
 * @param cStat       código do status; 107 = serviço em operação
 * @param xMotivo     descrição do status
 * @param tMedSegundos tempo médio de resposta informado pela Sefaz (pode ser nulo)
 * @param xmlRetorno  XML original devolvido pela Sefaz (para auditoria)
 */
public record RetornoStatusServico(
        String cStat,
        String xMotivo,
        String tpAmb,
        String cUF,
        String verAplic,
        String dhRecbto,
        Integer tMedSegundos,
        String xObs,
        String xmlRetorno) {

    public static final String EM_OPERACAO = "107";

    public boolean emOperacao() {
        return EM_OPERACAO.equals(cStat);
    }
}
