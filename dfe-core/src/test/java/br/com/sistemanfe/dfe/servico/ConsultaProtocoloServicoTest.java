package br.com.sistemanfe.dfe.servico;

import br.com.sistemanfe.dfe.comum.Ambiente;
import br.com.sistemanfe.dfe.comum.FormaEmissao;
import br.com.sistemanfe.dfe.comum.ModeloDocumento;
import br.com.sistemanfe.dfe.comum.UF;
import br.com.sistemanfe.dfe.nfe.ChaveAcesso;
import br.com.sistemanfe.dfe.teste.RespostasSefaz;
import br.com.sistemanfe.dfe.ws.CatalogoWebServices;
import br.com.sistemanfe.dfe.ws.EnvelopeSoap;
import br.com.sistemanfe.dfe.xml.ValidadorXsd;
import br.com.sistemanfe.dfe.xml.Xml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Element;

import java.net.URI;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class ConsultaProtocoloServicoTest {

    private static final ChaveAcesso CHAVE = ChaveAcesso.de(RespostasSefaz.CHAVE_AUTORIZADA);

    @Test
    void geraConsSitNFeExatamenteNoFormatoDaSefaz() {
        String xml = ConsultaProtocoloServico.montarMensagem(CHAVE, Ambiente.HOMOLOGACAO);

        assertThat(xml).isEqualTo(
                "<consSitNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">"
                        + "<tpAmb>2</tpAmb><xServ>CONSULTAR</xServ>"
                        + "<chNFe>43261111222333000181550010000001231876543212</chNFe></consSitNFe>");
    }

    @Test
    void mensagemGeradaPassaNoSchemaOficial() {
        String xml = ConsultaProtocoloServico.montarMensagem(CHAVE, Ambiente.PRODUCAO);

        assertThat(ValidadorXsd.para("consSitNFe_v4.00.xsd").validar(xml)).isEmpty();
    }

    @Test
    void chaveComCnpjAlfanumericoPassaNoSchema() {
        ChaveAcesso alfanumerica = ChaveAcesso.gerar(UF.RS, YearMonth.of(2026, 11), "12ABC34501DE35",
                ModeloDocumento.NFE, 1, 1, FormaEmissao.NORMAL, "10293847");

        String xml = ConsultaProtocoloServico.montarMensagem(alfanumerica, Ambiente.HOMOLOGACAO);

        assertThat(xml).contains("<chNFe>43261112ABC34501DE35550010000000011102938472</chNFe>");
        assertThat(ValidadorXsd.para("consSitNFe_v4.00.xsd").validar(xml)).isEmpty();
    }

    @Test
    void schemaRejeitaServicoDiferenteDeConsultar() {
        String invalida = "<consSitNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">"
                + "<tpAmb>2</tpAmb><xServ>STATUS</xServ>"
                + "<chNFe>43261111222333000181550010000001231876543212</chNFe></consSitNFe>";

        assertThat(ValidadorXsd.para("consSitNFe_v4.00.xsd").validar(invalida)).isNotEmpty();
    }

    @Test
    void enderecoSaiDaUfEDoModeloDaChave() {
        ConsultaProtocoloServico servico = new ConsultaProtocoloServico(null, CatalogoWebServices.padrao());
        ChaveAcesso nfce = ChaveAcesso.gerar(UF.RS, YearMonth.of(2026, 11), "11222333000181",
                ModeloDocumento.NFCE, 1, 5, FormaEmissao.NORMAL, "27182818");

        assertThat(servico.endereco(CHAVE, Ambiente.HOMOLOGACAO)).isEqualTo(
                URI.create("https://nfe-homologacao.sefazrs.rs.gov.br/ws/NfeConsulta/NfeConsulta4.asmx"));
        assertThat(servico.endereco(CHAVE, Ambiente.PRODUCAO)).isEqualTo(
                URI.create("https://nfe.sefazrs.rs.gov.br/ws/NfeConsulta/NfeConsulta4.asmx"));
        assertThat(servico.endereco(nfce, Ambiente.HOMOLOGACAO)).isEqualTo(
                URI.create("https://nfce-homologacao.sefazrs.rs.gov.br/ws/NfeConsulta/NfeConsulta4.asmx"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"autorizada", "cancelada", "inexistente"})
    void amostrasDeRetornoPassamNoSchemaOficial(String amostra) {
        String soap = switch (amostra) {
            case "autorizada" -> RespostasSefaz.CONSULTA_AUTORIZADA;
            case "cancelada" -> RespostasSefaz.CONSULTA_CANCELADA;
            default -> RespostasSefaz.CONSULTA_INEXISTENTE;
        };

        String retorno = Xml.paraTexto(EnvelopeSoap.extrairRetorno(soap));

        assertThat(ValidadorXsd.para("retConsSitNFe_v4.00.xsd").validar(retorno)).isEmpty();
    }

    @Test
    void leRetornoDeNotaAutorizadaComProtocolo() {
        RetornoConsultaProtocolo r = ler(RespostasSefaz.CONSULTA_AUTORIZADA);

        assertThat(r.cStat()).isEqualTo("100");
        assertThat(r.xMotivo()).isEqualTo("Autorizado o uso da NF-e");
        assertThat(r.tpAmb()).isEqualTo("2");
        assertThat(r.cUF()).isEqualTo("43");
        assertThat(r.chNFe()).isEqualTo(RespostasSefaz.CHAVE_AUTORIZADA);
        assertThat(r.autorizada()).isTrue();
        assertThat(r.naoConsta()).isFalse();
        assertThat(r.eventos()).isEmpty();
        assertThat(r.xmlRetorno()).startsWith("<retConsSitNFe");

        ProtocoloNFe p = r.protocolo();
        assertThat(p).isNotNull();
        assertThat(p.chNFe()).isEqualTo(RespostasSefaz.CHAVE_AUTORIZADA);
        assertThat(p.nProt()).isEqualTo("143260000012345");
        assertThat(p.dhRecbto()).isEqualTo("2026-11-27T14:02:33-03:00");
        assertThat(p.cStat()).isEqualTo("100");
        assertThat(p.xMotivo()).isEqualTo("Autorizado o uso da NF-e");
        assertThat(p.digVal()).isEqualTo("AAECAwQFBgcICQoLDA0ODxAREhM=");
    }

    /** O {@code protNFe} é guardado como veio, para montar o {@code nfeProc} da nota recuperada. */
    @Test
    void guardaOXmlDoProtNFeNoNamespaceDaNFe() {
        ProtocoloNFe p = ler(RespostasSefaz.CONSULTA_AUTORIZADA).protocolo();

        Element protNFe = Xml.ler(p.xml()).getDocumentElement();
        assertThat(protNFe.getLocalName()).isEqualTo("protNFe");
        assertThat(protNFe.getNamespaceURI()).isEqualTo(Xml.NS_NFE);
        assertThat(protNFe.getAttribute("versao")).isEqualTo("4.00");
        assertThat(p.xml()).contains("<nProt>143260000012345</nProt>");
    }

    @Test
    void leRetornoDeNotaCanceladaComEvento() {
        RetornoConsultaProtocolo r = ler(RespostasSefaz.CONSULTA_CANCELADA);

        assertThat(r.cStat()).isEqualTo("101");
        assertThat(r.cancelada()).isTrue();
        assertThat(r.autorizada()).isFalse();
        assertThat(r.protocolo().nProt()).isEqualTo("143260000012346");
        assertThat(r.eventos()).hasSize(1);

        EventoNFe e = r.eventos().get(0);
        assertThat(e.tpEvento()).isEqualTo("110111");
        assertThat(e.nSeqEvento()).isEqualTo("1");
        assertThat(e.dhEvento()).isEqualTo("2026-11-27T15:30:00-03:00");
        assertThat(e.cStat()).isEqualTo("135");
        assertThat(e.xMotivo()).isEqualTo("Evento registrado e vinculado a NF-e");
        assertThat(e.xEvento()).isEqualTo("Cancelamento registrado");
        assertThat(e.nProt()).isEqualTo("143260000012347");
        assertThat(e.dhRegEvento()).isEqualTo("2026-11-27T15:30:05-03:00");

        Element procEvento = Xml.ler(e.xml()).getDocumentElement();
        assertThat(procEvento.getLocalName()).isEqualTo("procEventoNFe");
        assertThat(procEvento.getNamespaceURI()).isEqualTo(Xml.NS_NFE);
    }

    @Test
    void leRetornoDeNotaQueNaoConstaNaBase() {
        RetornoConsultaProtocolo r = ler(RespostasSefaz.CONSULTA_INEXISTENTE);

        assertThat(r.cStat()).isEqualTo("217");
        assertThat(r.xMotivo()).isEqualTo("Rejeicao: NF-e nao consta na base de dados da SEFAZ");
        assertThat(r.chNFe()).isEqualTo(RespostasSefaz.CHAVE_INEXISTENTE);
        assertThat(r.naoConsta()).isTrue();
        assertThat(r.autorizada()).isFalse();
        assertThat(r.cancelada()).isFalse();
        assertThat(r.denegada()).isFalse();
        assertThat(r.protocolo()).isNull();
        assertThat(r.eventos()).isEmpty();
    }

    @Test
    void usoDenegadoEReconhecido() {
        String denegada = RespostasSefaz.CONSULTA_AUTORIZADA
                .replace("<cStat>100</cStat>", "<cStat>110</cStat>")
                .replace("Autorizado o uso da NF-e", "Uso Denegado");

        RetornoConsultaProtocolo r = ler(denegada);

        assertThat(r.denegada()).isTrue();
        assertThat(r.autorizada()).isFalse();
    }

    @Test
    void autorizacaoForaDePrazoContaComoAutorizada() {
        String foraDePrazo = RespostasSefaz.CONSULTA_AUTORIZADA.replace("<cStat>100</cStat>", "<cStat>150</cStat>");

        assertThat(ler(foraDePrazo).autorizada()).isTrue();
    }

    private static RetornoConsultaProtocolo ler(String soap) {
        return ConsultaProtocoloServico.lerRetorno(EnvelopeSoap.extrairRetorno(soap));
    }
}
