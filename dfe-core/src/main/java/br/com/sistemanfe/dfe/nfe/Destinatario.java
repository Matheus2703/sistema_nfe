package br.com.sistemanfe.dfe.nfe;

import java.util.Objects;

/**
 * Quem recebe a nota.
 *
 * @param documento         CNPJ (14 caracteres) ou CPF (11 dígitos), sem pontuação
 * @param nome              razão social ou nome; em homologação o montador troca pelo texto exigido pela Sefaz
 * @param inscricaoEstadual obrigatória para contribuinte; {@code null} quando não houver
 * @param email             opcional ({@code null} quando não houver)
 */
public record Destinatario(String documento, String nome, Endereco endereco, IndicadorIEDestinatario indicadorIE,
                           String inscricaoEstadual, String email) {

    public Destinatario {
        Objects.requireNonNull(documento, "documento");
        Objects.requireNonNull(nome, "nome");
        Objects.requireNonNull(endereco, "endereco");
        Objects.requireNonNull(indicadorIE, "indicadorIE");
    }
}
