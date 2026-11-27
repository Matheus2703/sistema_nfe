package br.com.sistemanfe.dfe.nfe;

import java.util.Objects;

/**
 * Empresa que emite a nota. Todas as emitentes do sistema são do Simples Nacional (CRT 1).
 *
 * @param cnpj              14 caracteres, sem pontuação
 * @param nomeFantasia      opcional ({@code null} quando não houver)
 * @param inscricaoEstadual só dígitos
 */
public record Emitente(String cnpj, String razaoSocial, String nomeFantasia, String inscricaoEstadual,
                       Endereco endereco) {

    public Emitente {
        Objects.requireNonNull(cnpj, "cnpj");
        Objects.requireNonNull(razaoSocial, "razaoSocial");
        Objects.requireNonNull(inscricaoEstadual, "inscricaoEstadual");
        Objects.requireNonNull(endereco, "endereco");
    }
}
