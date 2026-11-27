package br.com.sistemanfe.dfe.servico;

import br.com.sistemanfe.dfe.certificado.CertificadoA1;
import br.com.sistemanfe.dfe.comum.Ambiente;
import br.com.sistemanfe.dfe.comum.FormaEmissao;
import br.com.sistemanfe.dfe.comum.ModeloDocumento;
import br.com.sistemanfe.dfe.comum.UF;
import br.com.sistemanfe.dfe.nfe.ChaveAcesso;
import br.com.sistemanfe.dfe.ws.CatalogoWebServices;
import br.com.sistemanfe.dfe.ws.ClienteSefaz;
import br.com.sistemanfe.dfe.ws.ContextoTls;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Path;
import java.security.KeyStore;
import java.time.YearMonth;
import java.time.ZoneId;

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

    private final CertificadoA1 cert = CertificadoA1.carregar(Path.of(System.getenv("NFE_PFX")),
            System.getenv().getOrDefault("NFE_PFX_SENHA", "").toCharArray());

    @Test
    void statusDaNfeNaHomologacaoDoRs() {
        RetornoStatusServico r = new ConsultaStatusServico(cliente(), CatalogoWebServices.padrao())
                .consultar(UF.RS, ModeloDocumento.NFE, Ambiente.HOMOLOGACAO);

        System.out.println("Sefaz RS (homologação): " + r.cStat() + " - " + r.xMotivo());
        assertThat(r.cStat()).as("cStat 107 = serviço em operação").isEqualTo("107");
    }

    /** Chave válida, com o CNPJ do certificado e o mês atual, mas de um número que nunca foi emitido. */
    @Test
    void consultaDeChaveNuncaEmitidaVoltaComRejeicao217() {
        String cnpj = cert.cnpj().orElseThrow(() -> new IllegalStateException("Certificado sem CNPJ."));
        ChaveAcesso chave = ChaveAcesso.gerar(UF.RS, YearMonth.now(ZoneId.of("America/Sao_Paulo")), cnpj,
                ModeloDocumento.NFE, 1, 999_999_999, FormaEmissao.NORMAL);

        RetornoConsultaProtocolo r = new ConsultaProtocoloServico(cliente(), CatalogoWebServices.padrao())
                .consultar(chave, Ambiente.HOMOLOGACAO);

        System.out.println("Consulta " + chave + " (homologação): " + r.cStat() + " - " + r.xMotivo());
        assertThat(r.cStat()).as("cStat 217 = NF-e não consta na base de dados da Sefaz").isEqualTo("217");
        assertThat(r.protocolo()).isNull();
    }

    private ClienteSefaz cliente() {
        ClienteSefaz.Construtor b = ClienteSefaz.para(cert);
        String ts = System.getenv("NFE_TRUSTSTORE");
        if (ts != null && !ts.isBlank()) {
            KeyStore truststore = ContextoTls.lerTruststore(Path.of(ts),
                    System.getenv().getOrDefault("NFE_TRUSTSTORE_SENHA", "").toCharArray());
            b.confiancaExtra(truststore);
        }
        return b.construir();
    }
}
