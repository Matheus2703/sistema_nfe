package br.com.sistemanfe.dfe.ws;

/**
 * Web services da NF-e/NFC-e versão 4.00.
 *
 * <p>{@code chave} é o nome usado no catálogo de endereços ({@code webservices.properties});
 * {@code operacao} e {@code metodo} formam o namespace do WSDL e a SOAP action.
 */
public enum ServicoSefaz {
    STATUS_SERVICO("NfeStatusServico", "NFeStatusServico4", "nfeStatusServicoNF"),
    AUTORIZACAO("NfeAutorizacao", "NFeAutorizacao4", "nfeAutorizacaoLote"),
    RET_AUTORIZACAO("NfeRetAutorizacao", "NFeRetAutorizacao4", "nfeRetAutorizacaoLote"),
    CONSULTA_PROTOCOLO("NfeConsultaProtocolo", "NFeConsultaProtocolo4", "nfeConsultaNF"),
    INUTILIZACAO("NfeInutilizacao", "NFeInutilizacao4", "nfeInutilizacaoNF"),
    RECEPCAO_EVENTO("RecepcaoEvento", "NFeRecepcaoEvento4", "nfeRecepcaoEvento");

    private static final String BASE_WSDL = "http://www.portalfiscal.inf.br/nfe/wsdl/";

    private final String chave;
    private final String operacao;
    private final String metodo;

    ServicoSefaz(String chave, String operacao, String metodo) {
        this.chave = chave;
        this.operacao = operacao;
        this.metodo = metodo;
    }

    public String chave() {
        return chave;
    }

    /** Namespace do elemento {@code nfeDadosMsg}, ex.: {@code .../wsdl/NFeStatusServico4}. */
    public String namespaceWsdl() {
        return BASE_WSDL + operacao;
    }

    /** Valor do parâmetro {@code action} do Content-Type SOAP 1.2. */
    public String soapAction() {
        return namespaceWsdl() + "/" + metodo;
    }
}
