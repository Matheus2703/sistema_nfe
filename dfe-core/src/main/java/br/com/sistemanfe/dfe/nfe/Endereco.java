package br.com.sistemanfe.dfe.nfe;

import br.com.sistemanfe.dfe.comum.UF;

import java.util.Objects;

/**
 * Endereço do emitente ou do destinatário, como vai na NF-e.
 *
 * @param complemento     opcional ({@code null} quando não houver)
 * @param codigoMunicipio código IBGE do município, 7 dígitos
 * @param cep             8 dígitos, sem pontuação
 * @param telefone        opcional, só dígitos com DDD ({@code null} quando não houver)
 */
public record Endereco(String logradouro, String numero, String complemento, String bairro,
                       String codigoMunicipio, String municipio, UF uf, String cep, String telefone) {

    public Endereco {
        Objects.requireNonNull(logradouro, "logradouro");
        Objects.requireNonNull(numero, "numero");
        Objects.requireNonNull(bairro, "bairro");
        Objects.requireNonNull(codigoMunicipio, "codigoMunicipio");
        Objects.requireNonNull(municipio, "municipio");
        Objects.requireNonNull(uf, "uf");
        Objects.requireNonNull(cep, "cep");
    }
}
