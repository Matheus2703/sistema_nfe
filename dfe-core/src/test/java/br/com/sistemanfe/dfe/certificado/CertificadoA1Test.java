package br.com.sistemanfe.dfe.certificado;

import br.com.sistemanfe.dfe.DfeException;
import br.com.sistemanfe.dfe.teste.CertificadosDeTeste;
import org.bouncycastle.asn1.DERPrintableString;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CertificadoA1Test {

    private static final char[] SENHA = "senha-teste".toCharArray();

    @Test
    void carregaPfxELeCnpjDoCampoIcpBrasilEmOctetString() {
        var gerado = CertificadosDeTeste.eCnpj("EMPRESA TESTE LTDA", "12345678000195", null, 365);

        CertificadoA1 cert = CertificadoA1.carregar(gerado.comoPfx(SENHA), SENHA);

        assertThat(cert.cnpj()).contains("12345678000195");
        assertThat(cert.nomeTitular()).isEqualTo("EMPRESA TESTE LTDA");
        assertThat(cert.chavePrivada()).isNotNull();
        assertThat(cert.cadeia()).hasSize(1);
    }

    @Test
    void leCnpjQuandoGravadoComoPrintableString() {
        var gerado = CertificadosDeTeste.eCnpj("EMPRESA B", "11222333000181",
                new DERPrintableString("11222333000181"), 365);

        CertificadoA1 cert = CertificadoA1.carregar(gerado.comoPfx(SENHA), SENHA);

        assertThat(cert.cnpj()).contains("11222333000181");
    }

    @Test
    void usaCnpjDoFinalDoNomeQuandoNaoHaCampoIcpBrasil() {
        var gerado = CertificadosDeTeste.eCnpj("EMPRESA C LTDA:99888777000166", null, null, 365);

        CertificadoA1 cert = CertificadoA1.carregar(gerado.comoPfx(SENHA), SENHA);

        assertThat(cert.cnpj()).contains("99888777000166");
    }

    @Test
    void senhaErradaGeraMensagemClara() {
        byte[] pfx = CertificadosDeTeste.eCnpj("X", "12345678000195", null, 365).comoPfx(SENHA);

        assertThatThrownBy(() -> CertificadoA1.carregar(pfx, "errada".toCharArray()))
                .isInstanceOf(DfeException.class)
                .hasMessageContaining("Senha do certificado incorreta");
    }

    @Test
    void calculaVencimento() {
        CertificadoA1 cert = CertificadoA1.carregar(
                CertificadosDeTeste.eCnpj("X", "12345678000195", null, 10).comoPfx(SENHA), SENHA);
        Clock hoje = Clock.fixed(Instant.now(), ZoneOffset.UTC);
        Clock daqui20Dias = Clock.offset(hoje, Duration.ofDays(20));

        assertThat(cert.vencido(hoje)).isFalse();
        assertThat(cert.diasParaVencer(hoje)).isBetween(9L, 10L);
        assertThat(cert.vencido(daqui20Dias)).isTrue();
    }
}
