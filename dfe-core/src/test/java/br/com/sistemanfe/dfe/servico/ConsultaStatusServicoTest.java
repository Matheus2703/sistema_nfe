package br.com.sistemanfe.dfe.servico;

import br.com.sistemanfe.dfe.comum.Ambiente;
import br.com.sistemanfe.dfe.comum.UF;
import br.com.sistemanfe.dfe.teste.RespostasSefaz;
import br.com.sistemanfe.dfe.ws.EnvelopeSoap;
import br.com.sistemanfe.dfe.xml.ValidadorXsd;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConsultaStatusServicoTest {

    @Test
    void geraConsStatServExatamenteNoFormatoDaSefaz() {
        String xml = ConsultaStatusServico.montarMensagem(UF.RS, Ambiente.HOMOLOGACAO);

        assertThat(xml).isEqualTo(
                "<consStatServ xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">"
                        + "<tpAmb>2</tpAmb><cUF>43</cUF><xServ>STATUS</xServ></consStatServ>");
    }

    @Test
    void mensagemGeradaPassaNoSchemaOficial() {
        String xml = ConsultaStatusServico.montarMensagem(UF.RS, Ambiente.PRODUCAO);

        assertThat(ValidadorXsd.para("consStatServ_v4.00.xsd").validar(xml)).isEmpty();
    }

    @Test
    void schemaRejeitaMensagemInvalida() {
        String invalida = "<consStatServ xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">"
                + "<tpAmb>2</tpAmb><cUF>99</cUF><xServ>STATUS</xServ></consStatServ>";

        assertThat(ValidadorXsd.para("consStatServ_v4.00.xsd").validar(invalida)).isNotEmpty();
    }

    @Test
    void leRetornoDaSefaz() {
        RetornoStatusServico r = ConsultaStatusServico.lerRetorno(EnvelopeSoap.extrairRetorno(RespostasSefaz.STATUS_107));

        assertThat(r.emOperacao()).isTrue();
        assertThat(r.cStat()).isEqualTo("107");
        assertThat(r.xMotivo()).isEqualTo("Servico em Operacao");
        assertThat(r.cUF()).isEqualTo("43");
        assertThat(r.tMedSegundos()).isEqualTo(1);
        assertThat(r.xmlRetorno()).startsWith("<retConsStatServ");
    }
}
