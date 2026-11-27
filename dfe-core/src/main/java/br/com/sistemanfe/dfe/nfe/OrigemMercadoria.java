package br.com.sistemanfe.dfe.nfe;

/** Origem da mercadoria ({@code orig}), tabela A do ICMS. */
public enum OrigemMercadoria {
    NACIONAL("0"),
    ESTRANGEIRA_IMPORTACAO_DIRETA("1"),
    ESTRANGEIRA_ADQUIRIDA_NO_MERCADO_INTERNO("2"),
    NACIONAL_CONTEUDO_IMPORTADO_ACIMA_DE_40("3"),
    NACIONAL_PROCESSO_PRODUTIVO_BASICO("4"),
    NACIONAL_CONTEUDO_IMPORTADO_ATE_40("5"),
    ESTRANGEIRA_IMPORTACAO_DIRETA_SEM_SIMILAR("6"),
    ESTRANGEIRA_MERCADO_INTERNO_SEM_SIMILAR("7"),
    NACIONAL_CONTEUDO_IMPORTADO_ACIMA_DE_70("8");

    private final String codigo;

    OrigemMercadoria(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
