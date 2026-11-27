package br.com.sistemanfe.dfe.ws;

import br.com.sistemanfe.dfe.DfeException;

/**
 * Falha de comunicação com a Sefaz (rede, TLS, HTTP, SOAP Fault).
 *
 * <p>{@link #talvezProcessada()} indica que a mensagem pode ter chegado à Sefaz
 * (ex.: timeout depois do envio). Nesses casos a nota NUNCA deve ser reenviada com
 * outro número: o correto é consultar pela chave de acesso.
 */
public class SefazComunicacaoException extends DfeException {

    private final boolean talvezProcessada;

    public SefazComunicacaoException(String mensagem, boolean talvezProcessada, Throwable causa) {
        super(mensagem, causa);
        this.talvezProcessada = talvezProcessada;
    }

    public SefazComunicacaoException(String mensagem, boolean talvezProcessada) {
        super(mensagem);
        this.talvezProcessada = talvezProcessada;
    }

    public boolean talvezProcessada() {
        return talvezProcessada;
    }
}
