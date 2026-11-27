package br.com.sistemanfe.dfe.nfe;

import br.com.sistemanfe.dfe.DfeException;
import br.com.sistemanfe.dfe.comum.Ambiente;
import br.com.sistemanfe.dfe.comum.FormaEmissao;
import br.com.sistemanfe.dfe.comum.ModeloDocumento;
import br.com.sistemanfe.dfe.comum.UF;
import br.com.sistemanfe.dfe.teste.NotasDeTeste;
import br.inf.portalfiscal.nfe.TNFe;
import br.inf.portalfiscal.nfe.TUf;
import br.inf.portalfiscal.nfe.TUfEmi;
import jakarta.xml.bind.JAXBElement;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MontadorNFeTest {

    @Test
    void idDaInfNFeEhAChaveDeAcessoComOsDadosDaNota() {
        TNFe.InfNFe inf = MontadorNFe.montar(NotasDeTeste.nfeMinima()).getInfNFe();

        assertThat(inf.getVersao()).isEqualTo("4.00");
        assertThat(inf.getId()).startsWith("NFe");
        ChaveAcesso chave = ChaveAcesso.de(inf.getId().substring(3));
        assertThat(chave.uf()).isEqualTo(UF.RS);
        assertThat(chave.anoMesEmissao()).isEqualTo(YearMonth.of(2026, 11));
        assertThat(chave.cnpjEmitente()).isEqualTo(NotasDeTeste.CNPJ_EMITENTE);
        assertThat(chave.modelo()).isEqualTo(ModeloDocumento.NFE);
        assertThat(chave.serie()).isEqualTo(1);
        assertThat(chave.numero()).isEqualTo(42);
        assertThat(chave.formaEmissao()).isEqualTo(FormaEmissao.NORMAL);
        assertThat(inf.getIde().getCNF()).isEqualTo(chave.codigoNumerico());
        assertThat(inf.getIde().getCDV()).isEqualTo(String.valueOf(chave.digitoVerificador()));
    }

    @Test
    void cadaNotaSorteiaOSeuCodigoNumerico() {
        String id1 = MontadorNFe.montar(NotasDeTeste.nfeMinima()).getInfNFe().getId();
        String id2 = MontadorNFe.montar(NotasDeTeste.nfeMinima()).getInfNFe().getId();

        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void preencheAIdentificacaoDaNFeModelo55() {
        TNFe.InfNFe.Ide ide = MontadorNFe.montar(NotasDeTeste.nfeMinima()).getInfNFe().getIde();

        assertThat(ide.getCUF()).isEqualTo("43");
        assertThat(ide.getNatOp()).isEqualTo("VENDA DE MERCADORIA");
        assertThat(ide.getMod()).isEqualTo("55");
        assertThat(ide.getSerie()).isEqualTo("1");
        assertThat(ide.getNNF()).isEqualTo("42");
        assertThat(ide.getDhEmi()).isEqualTo("2026-11-27T10:30:00-03:00");
        assertThat(ide.getTpNF()).isEqualTo("1");
        assertThat(ide.getIdDest()).isEqualTo("1");
        assertThat(ide.getCMunFG()).isEqualTo(NotasDeTeste.MUNICIPIO_RS);
        assertThat(ide.getTpImp()).isEqualTo("1");
        assertThat(ide.getTpEmis()).isEqualTo("1");
        assertThat(ide.getTpAmb()).isEqualTo("2");
        assertThat(ide.getFinNFe()).isEqualTo("1");
        assertThat(ide.getIndFinal()).isEqualTo("0");
        assertThat(ide.getIndPres()).isEqualTo("1");
        assertThat(ide.getProcEmi()).isEqualTo("0");
        assertThat(ide.getVerProc()).isNotBlank().hasSizeLessThanOrEqualTo(20);
    }

    @Test
    void emitenteEhDoSimplesNacional() {
        TNFe.InfNFe.Emit emit = MontadorNFe.montar(NotasDeTeste.nfeMinima()).getInfNFe().getEmit();

        assertThat(emit.getCNPJ()).isEqualTo(NotasDeTeste.CNPJ_EMITENTE);
        assertThat(emit.getXNome()).isEqualTo("EMPRESA FICTICIA DE TESTE LTDA");
        assertThat(emit.getXFant()).isEqualTo("LOJA DE TESTE");
        assertThat(emit.getIE()).isEqualTo(NotasDeTeste.IE_EMITENTE);
        assertThat(emit.getCRT()).isEqualTo("1");
        assertThat(emit.getEnderEmit().getXLgr()).isEqualTo("RUA FICTICIA DE TESTE");
        assertThat(emit.getEnderEmit().getNro()).isEqualTo("100");
        assertThat(emit.getEnderEmit().getXCpl()).isNull();
        assertThat(emit.getEnderEmit().getXBairro()).isEqualTo("CENTRO");
        assertThat(emit.getEnderEmit().getCMun()).isEqualTo(NotasDeTeste.MUNICIPIO_RS);
        assertThat(emit.getEnderEmit().getXMun()).isEqualTo("PORTO ALEGRE");
        assertThat(emit.getEnderEmit().getUF()).isEqualTo(TUfEmi.RS);
        assertThat(emit.getEnderEmit().getCEP()).isEqualTo("90000000");
        assertThat(emit.getEnderEmit().getFone()).isNull();
    }

    @Test
    void emHomologacaoONomeDoDestinatarioEhOTextoExigidoPelaSefaz() {
        TNFe.InfNFe.Dest dest = MontadorNFe.montar(NotasDeTeste.nfeMinima()).getInfNFe().getDest();

        assertThat(dest.getXNome()).isEqualTo("NF-E EMITIDA EM AMBIENTE DE HOMOLOGACAO - SEM VALOR FISCAL");
    }

    @Test
    void emProducaoONomeDoDestinatarioEhODoCadastro() {
        DadosNFe dados = NotasDeTeste.nfe(Ambiente.PRODUCAO, NotasDeTeste.destinatarioContribuinte(),
                List.of(NotasDeTeste.item()), List.of(dinheiro("21.00")), false);

        TNFe.InfNFe inf = MontadorNFe.montar(dados).getInfNFe();

        assertThat(inf.getIde().getTpAmb()).isEqualTo("1");
        assertThat(inf.getDest().getXNome()).isEqualTo(NotasDeTeste.NOME_DESTINATARIO);
    }

    @Test
    void destinatarioContribuinteComCnpjEInscricaoEstadual() {
        TNFe.InfNFe.Dest dest = MontadorNFe.montar(NotasDeTeste.nfeMinima()).getInfNFe().getDest();

        assertThat(dest.getCNPJ()).isEqualTo(NotasDeTeste.CNPJ_DESTINATARIO);
        assertThat(dest.getCPF()).isNull();
        assertThat(dest.getIndIEDest()).isEqualTo("1");
        assertThat(dest.getIE()).isEqualTo(NotasDeTeste.IE_DESTINATARIO);
        assertThat(dest.getEmail()).isEqualTo("cliente@exemplo.com");
        assertThat(dest.getEnderDest().getXLgr()).isEqualTo("RUA FICTICIA DE TESTE");
        assertThat(dest.getEnderDest().getCMun()).isEqualTo(NotasDeTeste.MUNICIPIO_RS);
        assertThat(dest.getEnderDest().getUF()).isEqualTo(TUf.RS);
        assertThat(dest.getEnderDest().getCEP()).isEqualTo("90000000");
    }

    @Test
    void destinatarioPessoaFisicaNaoContribuinteVaiComCpf() {
        DadosNFe dados = NotasDeTeste.nfe(Ambiente.HOMOLOGACAO, NotasDeTeste.destinatarioPessoaFisica(),
                List.of(NotasDeTeste.item()), List.of(dinheiro("21.00")), true);

        TNFe.InfNFe inf = MontadorNFe.montar(dados).getInfNFe();

        assertThat(inf.getDest().getCPF()).isEqualTo(NotasDeTeste.CPF_DESTINATARIO);
        assertThat(inf.getDest().getCNPJ()).isNull();
        assertThat(inf.getDest().getIndIEDest()).isEqualTo("9");
        assertThat(inf.getDest().getIE()).isNull();
        assertThat(inf.getIde().getIndFinal()).isEqualTo("1");
    }

    @Test
    void naoContribuinteSoComConsumidorFinal() {
        DadosNFe dados = NotasDeTeste.nfe(Ambiente.HOMOLOGACAO, NotasDeTeste.destinatarioPessoaFisica(),
                List.of(NotasDeTeste.item()), List.of(dinheiro("21.00")), false);

        assertThatThrownBy(() -> MontadorNFe.montar(dados))
                .isInstanceOf(DfeException.class)
                .hasMessageContaining("consumidor final");
    }

    @Test
    void contribuinteSemInscricaoEstadualEhRecusado() {
        Destinatario semIe = new Destinatario(NotasDeTeste.CNPJ_DESTINATARIO, NotasDeTeste.NOME_DESTINATARIO,
                NotasDeTeste.enderecoRs(), IndicadorIEDestinatario.CONTRIBUINTE, null, null);
        DadosNFe dados = NotasDeTeste.nfe(Ambiente.HOMOLOGACAO, semIe, List.of(NotasDeTeste.item()),
                List.of(dinheiro("21.00")), false);

        assertThatThrownBy(() -> MontadorNFe.montar(dados))
                .isInstanceOf(DfeException.class)
                .hasMessageContaining("inscrição estadual");
    }

    @Test
    void vendaParaOutroEstadoEhInterestadual() {
        Destinatario emSc = new Destinatario(NotasDeTeste.CNPJ_DESTINATARIO, NotasDeTeste.NOME_DESTINATARIO,
                NotasDeTeste.enderecoSc(), IndicadorIEDestinatario.CONTRIBUINTE, NotasDeTeste.IE_DESTINATARIO, null);
        DadosNFe dados = NotasDeTeste.nfe(Ambiente.HOMOLOGACAO, emSc,
                List.of(NotasDeTeste.item("6102", new BigDecimal("1"), new BigDecimal("5.00"))),
                List.of(dinheiro("5.00")), false);

        TNFe.InfNFe inf = MontadorNFe.montar(dados).getInfNFe();

        assertThat(inf.getIde().getIdDest()).isEqualTo("2");
        assertThat(inf.getDest().getEnderDest().getUF()).isEqualTo(TUf.SC);
        assertThat(inf.getDest().getEnderDest().getXCpl()).isEqualTo("SALA 1");
        assertThat(inf.getDest().getEnderDest().getFone()).isEqualTo("4830000000");
    }

    @Test
    void cfopPrecisaCombinarComODestinoDaOperacao() {
        DadosNFe interestadualComCfopInterno = NotasDeTeste.nfe(Ambiente.HOMOLOGACAO,
                new Destinatario(NotasDeTeste.CNPJ_DESTINATARIO, NotasDeTeste.NOME_DESTINATARIO,
                        NotasDeTeste.enderecoSc(), IndicadorIEDestinatario.CONTRIBUINTE,
                        NotasDeTeste.IE_DESTINATARIO, null),
                List.of(NotasDeTeste.item("5102", BigDecimal.ONE, new BigDecimal("5.00"))),
                List.of(dinheiro("5.00")), false);
        DadosNFe internaComCfopInterestadual = NotasDeTeste.nfe(Ambiente.HOMOLOGACAO,
                NotasDeTeste.destinatarioContribuinte(),
                List.of(NotasDeTeste.item("6102", BigDecimal.ONE, new BigDecimal("5.00"))),
                List.of(dinheiro("5.00")), false);

        assertThatThrownBy(() -> MontadorNFe.montar(interestadualComCfopInterno))
                .isInstanceOf(DfeException.class)
                .hasMessageContaining("CFOP 5102");
        assertThatThrownBy(() -> MontadorNFe.montar(internaComCfopInterestadual))
                .isInstanceOf(DfeException.class)
                .hasMessageContaining("CFOP 6102");
    }

    @Test
    void itemLevaOsDadosDoProdutoComOsValoresNoFormatoDaSefaz() {
        TNFe.InfNFe.Det det = MontadorNFe.montar(NotasDeTeste.nfeMinima()).getInfNFe().getDet().get(0);
        TNFe.InfNFe.Det.Prod prod = det.getProd();

        assertThat(det.getNItem()).isEqualTo("1");
        assertThat(prod.getCProd()).isEqualTo("P001");
        assertThat(prod.getCEAN()).isEqualTo("SEM GTIN");
        assertThat(prod.getXProd()).isEqualTo("PRODUTO FICTICIO DE TESTE");
        assertThat(prod.getNCM()).isEqualTo("61091000");
        assertThat(prod.getCFOP()).isEqualTo("5102");
        assertThat(prod.getUCom()).isEqualTo("UN");
        assertThat(prod.getQCom()).isEqualTo("2.0000");
        assertThat(prod.getVUnCom()).isEqualTo("10.50");
        assertThat(prod.getVProd()).isEqualTo("21.00");
        assertThat(prod.getCEANTrib()).isEqualTo("SEM GTIN");
        assertThat(prod.getUTrib()).isEqualTo("UN");
        assertThat(prod.getQTrib()).isEqualTo("2.0000");
        assertThat(prod.getVUnTrib()).isEqualTo("10.50");
        assertThat(prod.getIndTot()).isEqualTo("1");
    }

    @Test
    void gtinInformadoVaiNosDoisCamposDeCodigoDeBarras() {
        // Prefixo 2: GTIN de circulação restrita, não identifica produto real.
        ItemNFe comGtin = new ItemNFe("P002", "PRODUTO COM GTIN", "2001234567893", "61091000", "5102", "UN",
                BigDecimal.ONE, new BigDecimal("3"), OrigemMercadoria.NACIONAL);
        DadosNFe dados = NotasDeTeste.nfe(Ambiente.HOMOLOGACAO, NotasDeTeste.destinatarioContribuinte(),
                List.of(comGtin), List.of(dinheiro("3.00")), false);

        TNFe.InfNFe.Det.Prod prod = MontadorNFe.montar(dados).getInfNFe().getDet().get(0).getProd();

        assertThat(prod.getCEAN()).isEqualTo("2001234567893");
        assertThat(prod.getCEANTrib()).isEqualTo("2001234567893");
        assertThat(prod.getVUnCom()).isEqualTo("3.00");
    }

    @Test
    void valorDoProdutoEhQuantidadeVezesValorUnitarioArredondadoAoCentavo() {
        ItemNFe item = NotasDeTeste.item("5102", new BigDecimal("3"), new BigDecimal("0.3333"));
        DadosNFe dados = NotasDeTeste.nfe(Ambiente.HOMOLOGACAO, NotasDeTeste.destinatarioContribuinte(),
                List.of(item), List.of(dinheiro("1.00")), false);

        TNFe.InfNFe.Det.Prod prod = MontadorNFe.montar(dados).getInfNFe().getDet().get(0).getProd();

        assertThat(prod.getVUnCom()).isEqualTo("0.3333");
        assertThat(prod.getVProd()).isEqualTo("1.00");
    }

    @Test
    void quantidadeComMaisDeQuatroCasasEhRecusada() {
        ItemNFe item = NotasDeTeste.item("5102", new BigDecimal("1.23456"), BigDecimal.ONE);
        DadosNFe dados = NotasDeTeste.nfe(Ambiente.HOMOLOGACAO, NotasDeTeste.destinatarioContribuinte(),
                List.of(item), List.of(dinheiro("1.23")), false);

        assertThatThrownBy(() -> MontadorNFe.montar(dados))
                .isInstanceOf(DfeException.class)
                .hasMessageContaining("quantidade");
    }

    @Test
    void icmsDoSimplesNacionalComCsosn102() {
        TNFe.InfNFe.Det.Imposto imposto = MontadorNFe.montar(NotasDeTeste.nfeMinima()).getInfNFe().getDet().get(0)
                .getImposto();

        TNFe.InfNFe.Det.Imposto.ICMS icms = grupo(imposto, "ICMS", TNFe.InfNFe.Det.Imposto.ICMS.class);
        assertThat(icms.getICMSSN102().getOrig()).isEqualTo("0");
        assertThat(icms.getICMSSN102().getCSOSN()).isEqualTo("102");
    }

    @Test
    void pisECofinsZeradosPorqueOSimplesRecolheNoDas() {
        TNFe.InfNFe.Det.Imposto imposto = MontadorNFe.montar(NotasDeTeste.nfeMinima()).getInfNFe().getDet().get(0)
                .getImposto();

        TNFe.InfNFe.Det.Imposto.PIS.PISOutr pis =
                grupo(imposto, "PIS", TNFe.InfNFe.Det.Imposto.PIS.class).getPISOutr();
        assertThat(pis.getCST()).isEqualTo("99");
        assertThat(pis.getVBC()).isEqualTo("0.00");
        assertThat(pis.getPPIS()).isEqualTo("0.00");
        assertThat(pis.getVPIS()).isEqualTo("0.00");
        TNFe.InfNFe.Det.Imposto.COFINS.COFINSOutr cofins =
                grupo(imposto, "COFINS", TNFe.InfNFe.Det.Imposto.COFINS.class).getCOFINSOutr();
        assertThat(cofins.getCST()).isEqualTo("99");
        assertThat(cofins.getVBC()).isEqualTo("0.00");
        assertThat(cofins.getPCOFINS()).isEqualTo("0.00");
        assertThat(cofins.getVCOFINS()).isEqualTo("0.00");
    }

    @Test
    void totaisSomamOsItens() {
        DadosNFe dados = NotasDeTeste.nfe(Ambiente.HOMOLOGACAO, NotasDeTeste.destinatarioContribuinte(),
                List.of(NotasDeTeste.item(), NotasDeTeste.item("5102", BigDecimal.ONE, new BigDecimal("5"))),
                List.of(dinheiro("26.00")), false);

        TNFe.InfNFe inf = MontadorNFe.montar(dados).getInfNFe();

        assertThat(inf.getDet()).extracting(TNFe.InfNFe.Det::getNItem).containsExactly("1", "2");
        TNFe.InfNFe.Total.ICMSTot tot = inf.getTotal().getICMSTot();
        assertThat(tot.getVProd()).isEqualTo("26.00");
        assertThat(tot.getVNF()).isEqualTo("26.00");
        assertThat(List.of(tot.getVBC(), tot.getVICMS(), tot.getVICMSDeson(), tot.getVFCP(), tot.getVBCST(),
                tot.getVST(), tot.getVFCPST(), tot.getVFCPSTRet(), tot.getVFrete(), tot.getVSeg(), tot.getVDesc(),
                tot.getVII(), tot.getVIPI(), tot.getVIPIDevol(), tot.getVPIS(), tot.getVCOFINS(), tot.getVOutro()))
                .containsOnly("0.00");
    }

    @Test
    void semTransporte() {
        TNFe.InfNFe inf = MontadorNFe.montar(NotasDeTeste.nfeMinima()).getInfNFe();

        assertThat(inf.getTransp().getModFrete()).isEqualTo("9");
    }

    @Test
    void pagamentosVaoNoGrupoPag() {
        DadosNFe dados = NotasDeTeste.nfe(Ambiente.HOMOLOGACAO, NotasDeTeste.destinatarioContribuinte(),
                List.of(NotasDeTeste.item()),
                List.of(new Pagamento(MeioPagamento.BOLETO_BANCARIO, new BigDecimal("10")), dinheiro("11.00")),
                false);

        TNFe.InfNFe.Pag pag = MontadorNFe.montar(dados).getInfNFe().getPag();

        assertThat(pag.getDetPag()).extracting(TNFe.InfNFe.Pag.DetPag::getTPag).containsExactly("15", "01");
        assertThat(pag.getDetPag()).extracting(TNFe.InfNFe.Pag.DetPag::getVPag).containsExactly("10.00", "11.00");
        assertThat(pag.getVTroco()).isNull();
    }

    @Test
    void pagamentosPrecisamSomarOTotalDaNota() {
        DadosNFe dados = NotasDeTeste.nfe(Ambiente.HOMOLOGACAO, NotasDeTeste.destinatarioContribuinte(),
                List.of(NotasDeTeste.item()), List.of(dinheiro("20.00")), false);

        assertThatThrownBy(() -> MontadorNFe.montar(dados))
                .isInstanceOf(DfeException.class)
                .hasMessageContaining("20.00")
                .hasMessageContaining("21.00");
    }

    @Test
    void notaSemPagamentoEhRecusada() {
        DadosNFe dados = NotasDeTeste.nfe(Ambiente.HOMOLOGACAO, NotasDeTeste.destinatarioContribuinte(),
                List.of(NotasDeTeste.item()), List.of(), false);

        assertThatThrownBy(() -> MontadorNFe.montar(dados))
                .isInstanceOf(DfeException.class)
                .hasMessageContaining("pagamento");
    }

    @Test
    void notaPrecisaTerDeUmA990Itens() {
        DadosNFe semItens = NotasDeTeste.nfe(Ambiente.HOMOLOGACAO, NotasDeTeste.destinatarioContribuinte(),
                List.of(), List.of(dinheiro("0.00")), false);
        DadosNFe itensDemais = NotasDeTeste.nfe(Ambiente.HOMOLOGACAO, NotasDeTeste.destinatarioContribuinte(),
                Collections.nCopies(991, NotasDeTeste.item("5102", BigDecimal.ONE, BigDecimal.ONE)),
                List.of(dinheiro("991.00")), false);

        assertThatThrownBy(() -> MontadorNFe.montar(semItens))
                .isInstanceOf(DfeException.class)
                .hasMessageContaining("tem 0");
        assertThatThrownBy(() -> MontadorNFe.montar(itensDemais))
                .isInstanceOf(DfeException.class)
                .hasMessageContaining("990");
    }

    private static Pagamento dinheiro(String valor) {
        return new Pagamento(MeioPagamento.DINHEIRO, new BigDecimal(valor));
    }

    private static <T> T grupo(TNFe.InfNFe.Det.Imposto imposto, String nome, Class<T> tipo) {
        return imposto.getContent().stream()
                .filter(e -> e.getName().getLocalPart().equals(nome))
                .map(JAXBElement::getValue)
                .map(tipo::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Grupo " + nome + " ausente no imposto"));
    }
}
