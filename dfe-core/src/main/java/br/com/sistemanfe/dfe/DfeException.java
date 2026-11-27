package br.com.sistemanfe.dfe;

/** Erro base do núcleo DF-e. As mensagens são pensadas para chegar ao usuário. */
public class DfeException extends RuntimeException {

    public DfeException(String mensagem) {
        super(mensagem);
    }

    public DfeException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
