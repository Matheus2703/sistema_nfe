package br.com.sistemanfe.dfe.servico;

import br.com.sistemanfe.dfe.certificado.CertificadoA1;
import br.com.sistemanfe.dfe.comum.Ambiente;
import br.com.sistemanfe.dfe.comum.ModeloDocumento;
import br.com.sistemanfe.dfe.comum.UF;
import br.com.sistemanfe.dfe.ws.CatalogoWebServices;
import br.com.sistemanfe.dfe.ws.ClienteSefaz;
import br.com.sistemanfe.dfe.ws.ContextoTls;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Path;
import java.security.KeyStore;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste contra a homologação REAL da Sefaz RS. Só roda se as variáveis existirem:
 * <pre>
 *   NFE_PFX=/caminho/certificado.pfx
 *   NFE_PFX_SENHA=...
 *   NFE_TRUSTSTORE=/caminho/icpbrasil.p12      (opcional)
 *   NFE_TRUSTSTORE_SENHA=...                   (opcional)
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "NFE_PFX", matches = ".+")
class SefazHomologacaoRealTest {

    @Test
    void statusDaNfeNaHomologacaoDoRs() {
        CertificadoA1 cert = CertificadoA1.carregar(Path.of(System.getenv("NFE_PFX")),
                System.getenv().getOrDefault("NFE_PFX_SENHA", "").toCharArray());
        ClienteSefaz.Construtor b = ClienteSefaz.para(cert);
        String ts = System.getenv("NFE_TRUSTSTORE");
        if (ts != null && !ts.isBlank()) {
            KeyStore truststore = ContextoTls.lerTruststore(Path.of(ts),
                    System.getenv().getOrDefault("NFE_TRUSTSTORE_SENHA", "").toCharArray());
            b.confiancaExtra(truststore);
        }

        RetornoStatusServico r = new ConsultaStatusServico(b.construir(), CatalogoWebServices.padrao())
                .consultar(UF.RS, ModeloDocumento.NFE, Ambiente.HOMOLOGACAO);

        System.out.println("Sefaz RS (homologação): " + r.cStat() + " - " + r.xMotivo());
        assertThat(r.cStat()).as("cStat 107 = serviço em operação").isEqualTo("107");
    }
}
