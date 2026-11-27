package br.com.sistemanfe.dfe.ws;

import br.com.sistemanfe.dfe.certificado.TruststoreIcpBrasil;
import br.com.sistemanfe.dfe.teste.CertificadosDeTeste;
import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;
import java.security.KeyStore;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContextoTlsTest {

    private final CertificadosDeTeste.Gerado raiz = CertificadosDeTeste.acRaiz("AC RAIZ TESTE");
    private final CertificadosDeTeste.Gerado intermediaria = CertificadosDeTeste.emitidoPor(raiz, "AC RFB TESTE");
    private final CertificadosDeTeste.Gerado ac = CertificadosDeTeste.emitidoPor(intermediaria, "AC EMISSORA TESTE");
    private final CertificadosDeTeste.Gerado empresa = CertificadosDeTeste.emitidoPor(ac, "EMPRESA:12345678000195");

    @Test
    void completaCadeiaComIntermediariasSemEnviarARaiz() {
        KeyStore ts = TruststoreIcpBrasil.criar(List.of(
                raiz.certificado(), intermediaria.certificado(), ac.certificado()));

        List<X509Certificate> cadeia = ContextoTls.completarCadeia(List.of(empresa.certificado()), ts);

        assertThat(cadeia).containsExactly(empresa.certificado(), ac.certificado(), intermediaria.certificado());
    }

    @Test
    void ignoraAcComMesmoNomeMasOutraChave() {
        CertificadosDeTeste.Gerado impostora = CertificadosDeTeste.emitidoPor(raiz, "AC EMISSORA TESTE");
        KeyStore ts = TruststoreIcpBrasil.criar(List.of(impostora.certificado()));

        assertThat(ContextoTls.completarCadeia(List.of(empresa.certificado()), ts))
                .containsExactly(empresa.certificado());
    }

    @Test
    void semTruststoreMantemCadeiaOriginal() {
        assertThat(ContextoTls.completarCadeia(List.of(empresa.certificado()), null))
                .containsExactly(empresa.certificado());
    }

    @Test
    void apresentaOCertificadoSoParaOTipoDeChaveDele() {
        var gerente = new ContextoTls.CertificadoDaEmpresa(empresa.chaves().getPrivate(),
                List.of(empresa.certificado(), ac.certificado()));
        Principal[] acNaoRelacionada = {new X500Principal("CN=Outra AC, O=Outra, C=BR")};

        assertThat(gerente.chooseEngineClientAlias(new String[]{"RSA"}, acNaoRelacionada, null))
                .isEqualTo(ContextoTls.CertificadoDaEmpresa.ALIAS);
        assertThat(gerente.chooseEngineClientAlias(new String[]{"EC"}, acNaoRelacionada, null)).isNull();
        assertThat(gerente.getCertificateChain(ContextoTls.CertificadoDaEmpresa.ALIAS))
                .containsExactly(empresa.certificado(), ac.certificado());
    }

    @Test
    void resumoTiraHtmlDaPaginaDeErroDoIis() {
        String html = "<html><head><title>403 - Forbidden</title><style>body{x}</style></head>"
                + "<body><h2>403.7 - Forbidden: Client certificate required.</h2></body></html>";

        assertThat(ClienteSefaz.resumo(html))
                .isEqualTo(" Resposta: 403 - Forbidden 403.7 - Forbidden: Client certificate required.");
    }
}
