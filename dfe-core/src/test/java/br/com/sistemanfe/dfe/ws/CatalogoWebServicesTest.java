package br.com.sistemanfe.dfe.ws;

import br.com.sistemanfe.dfe.DfeException;
import br.com.sistemanfe.dfe.comum.Ambiente;
import br.com.sistemanfe.dfe.comum.ModeloDocumento;
import br.com.sistemanfe.dfe.comum.UF;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogoWebServicesTest {

    private final CatalogoWebServices catalogo = CatalogoWebServices.padrao();

    @Test
    void enderecosDoRsConformePortalNacional() {
        assertThat(catalogo.endereco(UF.RS, ModeloDocumento.NFE, Ambiente.HOMOLOGACAO, ServicoSefaz.STATUS_SERVICO))
                .isEqualTo(URI.create("https://nfe-homologacao.sefazrs.rs.gov.br/ws/NfeStatusServico/NfeStatusServico4.asmx"));
        assertThat(catalogo.endereco(UF.RS, ModeloDocumento.NFE, Ambiente.PRODUCAO, ServicoSefaz.AUTORIZACAO))
                .isEqualTo(URI.create("https://nfe.sefazrs.rs.gov.br/ws/NfeAutorizacao/NFeAutorizacao4.asmx"));
        assertThat(catalogo.endereco(UF.RS, ModeloDocumento.NFCE, Ambiente.HOMOLOGACAO, ServicoSefaz.AUTORIZACAO))
                .isEqualTo(URI.create("https://nfce-homologacao.sefazrs.rs.gov.br/ws/NfeAutorizacao/NFeAutorizacao4.asmx"));
    }

    @Test
    void todosOsServicosDoRsEstaoCadastrados() {
        for (ModeloDocumento m : ModeloDocumento.values()) {
            for (Ambiente a : Ambiente.values()) {
                for (ServicoSefaz s : ServicoSefaz.values()) {
                    assertThat(catalogo.endereco(UF.RS, m, a, s).getScheme()).isEqualTo("https");
                }
            }
        }
    }

    @Test
    void ufNaoCadastradaGeraErroClaro() {
        assertThatThrownBy(() -> catalogo.endereco(UF.SP, ModeloDocumento.NFE, Ambiente.HOMOLOGACAO,
                ServicoSefaz.STATUS_SERVICO))
                .isInstanceOf(DfeException.class)
                .hasMessageContaining("não cadastrado");
    }
}
