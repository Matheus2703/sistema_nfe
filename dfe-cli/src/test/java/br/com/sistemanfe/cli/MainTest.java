package br.com.sistemanfe.cli;

import br.com.sistemanfe.dfe.servico.EventoNFe;
import br.com.sistemanfe.dfe.servico.ProtocoloNFe;
import br.com.sistemanfe.dfe.servico.RetornoConsultaProtocolo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MainTest {

    // Chave fictícia (CNPJ 11.222.333/0001-81), com dígito verificador correto.
    private static final String CHAVE = "43261111222333000181550010000001231876543212";

    private final PrintStream saidaOriginal = System.out;
    private final PrintStream erroOriginal = System.err;
    private final ByteArrayOutputStream saida = new ByteArrayOutputStream();
    private final ByteArrayOutputStream erro = new ByteArrayOutputStream();

    @BeforeEach
    void capturar() {
        System.setOut(new PrintStream(saida, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(erro, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restaurar() {
        System.setOut(saidaOriginal);
        System.setErr(erroOriginal);
    }

    @Test
    void ajudaMostraOComandoConsulta() {
        assertThat(Main.executar(new String[]{"--help"})).isEqualTo(Main.OK);

        assertThat(saida()).contains("consulta --pfx <arquivo.pfx> --chave <44 caracteres>");
    }

    @Test
    void consultaSemChaveEErroDeUso() {
        int codigo = Main.executar(new String[]{"consulta", "--pfx", "nao-existe.pfx"});

        assertThat(codigo).isEqualTo(Main.USO);
        assertThat(erro()).contains("Informe a chave de acesso com --chave");
    }

    @Test
    void consultaComDigitoVerificadorErradoEErroDeUso() {
        String errada = CHAVE.substring(0, 43) + "0";

        int codigo = Main.executar(new String[]{"consulta", "--pfx", "nao-existe.pfx", "--chave", errada});

        assertThat(codigo).isEqualTo(Main.USO);
        assertThat(erro()).contains("--chave inválida").contains("Dígito verificador");
    }

    @Test
    void chaveCopiadaDoDanfeComEspacosEAceita() {
        String doDanfe = "4326 1111 2223 3300 0181 5500 1000 0001 2318 7654 3212";

        assertThat(Main.lerChave(doDanfe).valor()).isEqualTo(CHAVE);
    }

    @Test
    void imprimeOProtocoloDaNotaAutorizada() {
        ProtocoloNFe prot = new ProtocoloNFe("2", "RS202611271200", CHAVE, "143260000012345",
                "2026-11-27T14:02:33-03:00", "100", "Autorizado o uso da NF-e", "AAEC", "<protNFe/>");
        RetornoConsultaProtocolo r = new RetornoConsultaProtocolo("100", "Autorizado o uso da NF-e", "2", "43",
                "RS202611271200", "2026-11-27T15:10:00-03:00", CHAVE, prot, List.of(), "<retConsSitNFe/>");

        Main.imprimirConsulta(r);

        assertThat(saida())
                .contains("cStat    : 100")
                .contains("xMotivo  : Autorizado o uso da NF-e")
                .contains("143260000012345")
                .contains("NF-e autorizada");
    }

    @Test
    void imprimeOsEventosDaNota() {
        EventoNFe cancelamento = new EventoNFe("110111", "1", "2026-11-27T15:30:00-03:00", "135",
                "Evento registrado e vinculado a NF-e", "Cancelamento registrado", "143260000012347",
                "2026-11-27T15:30:05-03:00", "<procEventoNFe/>");
        RetornoConsultaProtocolo r = new RetornoConsultaProtocolo("101", "Cancelamento de NF-e homologado", "2",
                "43", "RS202611271200", "2026-11-27T16:00:00-03:00", CHAVE, null, List.of(cancelamento), "");

        Main.imprimirConsulta(r);

        assertThat(saida())
                .contains("110111")
                .contains("Cancelamento registrado")
                .contains("143260000012347")
                .contains("NF-e cancelada");
    }

    @Test
    void explicaQueAChaveNaoConstaNaSefaz() {
        RetornoConsultaProtocolo r = new RetornoConsultaProtocolo("217",
                "Rejeicao: NF-e nao consta na base de dados da SEFAZ", "2", "43", "RS202611271200",
                "2026-11-27T15:12:00-03:00", CHAVE, null, List.of(), "");

        Main.imprimirConsulta(r);

        assertThat(saida())
                .contains("cStat    : 217")
                .contains("não tem nenhuma NF-e com esta chave");
    }

    private String saida() {
        return saida.toString(StandardCharsets.UTF_8);
    }

    private String erro() {
        return erro.toString(StandardCharsets.UTF_8);
    }
}
