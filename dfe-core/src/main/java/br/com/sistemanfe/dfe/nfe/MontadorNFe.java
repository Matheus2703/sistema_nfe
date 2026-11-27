package br.com.sistemanfe.dfe.nfe;

import br.com.sistemanfe.dfe.DfeException;
import br.com.sistemanfe.dfe.comum.Ambiente;
import br.com.sistemanfe.dfe.comum.FormaEmissao;
import br.com.sistemanfe.dfe.comum.ModeloDocumento;
import br.inf.portalfiscal.nfe.ObjectFactory;
import br.inf.portalfiscal.nfe.TEnderEmi;
import br.inf.portalfiscal.nfe.TEndereco;
import br.inf.portalfiscal.nfe.TNFe;
import br.inf.portalfiscal.nfe.TUf;
import br.inf.portalfiscal.nfe.TUfEmi;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Monta a NF-e (modelo 55) a partir dos {@link DadosNFe}, ainda sem assinatura.
 *
 * <p>Nesta fase a nota é a mínima de venda: saída, finalidade normal, emissão normal, DANFE retrato, sem
 * transporte, emitente do Simples Nacional (CRT 1), itens com CSOSN 102 e PIS/COFINS com CST 99 zerados (o
 * Simples recolhe os dois no DAS). A tributação de verdade vem do motor fiscal da fase 5.
 *
 * <p>Regras de homologação aplicadas automaticamente quando {@code tpAmb=2}: o nome do destinatário vira
 * {@value #NOME_DESTINATARIO_HOMOLOGACAO}.
 */
public final class MontadorNFe {

    static final String VERSAO = "4.00";
    /** {@code verProc}: versão do aplicativo emissor (até 20 caracteres). */
    static final String VERSAO_APLICATIVO = "sistema-nfe 0.1.0";

    /** Nome do destinatário obrigatório na NF-e de homologação (rejeição 598). */
    public static final String NOME_DESTINATARIO_HOMOLOGACAO =
            "NF-E EMITIDA EM AMBIENTE DE HOMOLOGACAO - SEM VALOR FISCAL";

    private static final int MAXIMO_ITENS = 990;

    /** {@code dhEmi} no formato UTC da Sefaz: {@code AAAA-MM-DDThh:mm:ss-03:00}, sem frações de segundo. */
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx");

    private static final String DESTINO_INTERNO = "1";
    private static final String DESTINO_INTERESTADUAL = "2";
    private static final String SAIDA = "1";
    private static final String DANFE_RETRATO = "1";
    private static final String FINALIDADE_NORMAL = "1";
    private static final String EMISSAO_APLICATIVO_CONTRIBUINTE = "0";
    private static final String SIMPLES_NACIONAL = "1";
    private static final String SEM_GTIN = "SEM GTIN";
    private static final String COMPOE_TOTAL = "1";
    private static final String CSOSN_SEM_PERMISSAO_DE_CREDITO = "102";
    private static final String CST_PIS_COFINS_OUTRAS_OPERACOES = "99";
    private static final String SEM_OCORRENCIA_DE_TRANSPORTE = "9";
    private static final String ZERO = "0.00";

    private static final ObjectFactory FABRICA = new ObjectFactory();

    private MontadorNFe() {
    }

    public static TNFe montar(DadosNFe dados) {
        Emitente emitente = dados.emitente();
        boolean interestadual = emitente.endereco().uf() != dados.destinatario().endereco().uf();
        validar(dados, interestadual);
        ChaveAcesso chave = ChaveAcesso.gerar(emitente.endereco().uf(), YearMonth.from(dados.dataHoraEmissao()),
                emitente.cnpj(), ModeloDocumento.NFE, dados.serie(), dados.numero(), FormaEmissao.NORMAL);

        TNFe.InfNFe inf = new TNFe.InfNFe();
        inf.setVersao(VERSAO);
        inf.setId(chave.idInfNFe());
        inf.setIde(ide(dados, chave, interestadual));
        inf.setEmit(emit(emitente));
        inf.setDest(dest(dados.destinatario(), dados.ambiente()));

        BigDecimal totalProdutos = BigDecimal.ZERO.setScale(2);
        for (int i = 0; i < dados.itens().size(); i++) {
            TNFe.InfNFe.Det det = det(i + 1, dados.itens().get(i));
            totalProdutos = totalProdutos.add(new BigDecimal(det.getProd().getVProd()));
            inf.getDet().add(det);
        }
        inf.setTotal(total(totalProdutos));
        inf.setTransp(semTransporte());
        inf.setPag(pag(dados.pagamentos(), totalProdutos));

        TNFe nfe = new TNFe();
        nfe.setInfNFe(inf);
        return nfe;
    }

    /** Regras de validação da Sefaz que dá para conferir antes de enviar. */
    private static void validar(DadosNFe dados, boolean interestadual) {
        Destinatario dest = dados.destinatario();
        if (dest.indicadorIE() == IndicadorIEDestinatario.NAO_CONTRIBUINTE && !dados.consumidorFinal()) {
            throw new DfeException("Destinatário não contribuinte exige operação com consumidor final "
                    + "(rejeição 696).");
        }
        if (dest.indicadorIE() == IndicadorIEDestinatario.CONTRIBUINTE
                && (dest.inscricaoEstadual() == null || dest.inscricaoEstadual().isBlank())) {
            throw new DfeException("Destinatário contribuinte do ICMS sem inscrição estadual.");
        }
        if (dados.itens().isEmpty() || dados.itens().size() > MAXIMO_ITENS) {
            throw new DfeException("A NF-e precisa ter de 1 a " + MAXIMO_ITENS + " itens; tem "
                    + dados.itens().size() + ".");
        }
        if (dados.pagamentos().isEmpty()) {
            throw new DfeException("A NF-e precisa de pelo menos uma forma de pagamento.");
        }
        char inicioCfop = interestadual ? '6' : '5';
        for (int i = 0; i < dados.itens().size(); i++) {
            String cfop = dados.itens().get(i).cfop();
            if (cfop.isEmpty() || cfop.charAt(0) != inicioCfop) {
                throw new DfeException("Item " + (i + 1) + ": o CFOP " + cfop + " não serve para operação "
                        + (interestadual ? "interestadual" : "dentro do estado")
                        + "; use um CFOP de saída iniciado por " + inicioCfop + ".");
            }
        }
    }

    private static TNFe.InfNFe.Ide ide(DadosNFe dados, ChaveAcesso chave, boolean interestadual) {
        TNFe.InfNFe.Ide ide = new TNFe.InfNFe.Ide();
        ide.setCUF(chave.uf().codigoIbge());
        ide.setCNF(chave.codigoNumerico());
        ide.setNatOp(dados.naturezaOperacao());
        ide.setMod(chave.modelo().codigo());
        ide.setSerie(String.valueOf(chave.serie()));
        ide.setNNF(String.valueOf(chave.numero()));
        ide.setDhEmi(DATA_HORA.format(dados.dataHoraEmissao()));
        ide.setTpNF(SAIDA);
        ide.setIdDest(interestadual ? DESTINO_INTERESTADUAL : DESTINO_INTERNO);
        ide.setCMunFG(dados.emitente().endereco().codigoMunicipio());
        ide.setTpImp(DANFE_RETRATO);
        ide.setTpEmis(chave.formaEmissao().codigo());
        ide.setCDV(String.valueOf(chave.digitoVerificador()));
        ide.setTpAmb(dados.ambiente().codigo());
        ide.setFinNFe(FINALIDADE_NORMAL);
        ide.setIndFinal(dados.consumidorFinal() ? "1" : "0");
        ide.setIndPres(dados.presenca().codigo());
        ide.setProcEmi(EMISSAO_APLICATIVO_CONTRIBUINTE);
        ide.setVerProc(VERSAO_APLICATIVO);
        return ide;
    }

    private static TNFe.InfNFe.Emit emit(Emitente emitente) {
        Endereco e = emitente.endereco();
        TEnderEmi ender = new TEnderEmi();
        ender.setXLgr(e.logradouro());
        ender.setNro(e.numero());
        ender.setXCpl(e.complemento());
        ender.setXBairro(e.bairro());
        ender.setCMun(e.codigoMunicipio());
        ender.setXMun(e.municipio());
        ender.setUF(TUfEmi.fromValue(e.uf().name()));
        ender.setCEP(e.cep());
        ender.setFone(e.telefone());

        TNFe.InfNFe.Emit emit = new TNFe.InfNFe.Emit();
        emit.setCNPJ(emitente.cnpj());
        emit.setXNome(emitente.razaoSocial());
        emit.setXFant(emitente.nomeFantasia());
        emit.setEnderEmit(ender);
        emit.setIE(emitente.inscricaoEstadual());
        emit.setCRT(SIMPLES_NACIONAL);
        return emit;
    }

    private static TNFe.InfNFe.Dest dest(Destinatario destinatario, Ambiente ambiente) {
        TNFe.InfNFe.Dest dest = new TNFe.InfNFe.Dest();
        String documento = destinatario.documento();
        if (documento.length() == 14) {
            dest.setCNPJ(documento);
        } else if (documento.length() == 11) {
            dest.setCPF(documento);
        } else {
            throw new DfeException("Documento do destinatário deve ser um CNPJ (14 caracteres) ou um CPF "
                    + "(11 dígitos), sem pontuação: " + documento + ".");
        }
        dest.setXNome(ambiente == Ambiente.HOMOLOGACAO ? NOME_DESTINATARIO_HOMOLOGACAO : destinatario.nome());

        Endereco e = destinatario.endereco();
        TEndereco ender = new TEndereco();
        ender.setXLgr(e.logradouro());
        ender.setNro(e.numero());
        ender.setXCpl(e.complemento());
        ender.setXBairro(e.bairro());
        ender.setCMun(e.codigoMunicipio());
        ender.setXMun(e.municipio());
        ender.setUF(TUf.fromValue(e.uf().name()));
        ender.setCEP(e.cep());
        ender.setFone(e.telefone());
        dest.setEnderDest(ender);

        dest.setIndIEDest(destinatario.indicadorIE().codigo());
        dest.setIE(destinatario.inscricaoEstadual());
        dest.setEmail(destinatario.email());
        return dest;
    }

    private static TNFe.InfNFe.Det det(int numeroItem, ItemNFe item) {
        String quantidade = quantidade(item.quantidade(), numeroItem);
        String valorUnitario = valorUnitario(item.valorUnitario(), numeroItem);
        String valorProduto = item.quantidade().multiply(item.valorUnitario())
                .setScale(2, RoundingMode.HALF_UP).toPlainString();
        String gtin = item.gtin() == null ? SEM_GTIN : item.gtin();

        TNFe.InfNFe.Det.Prod prod = new TNFe.InfNFe.Det.Prod();
        prod.setCProd(item.codigo());
        prod.setCEAN(gtin);
        prod.setXProd(item.descricao());
        prod.setNCM(item.ncm());
        prod.setCFOP(item.cfop());
        prod.setUCom(item.unidade());
        prod.setQCom(quantidade);
        prod.setVUnCom(valorUnitario);
        prod.setVProd(valorProduto);
        prod.setCEANTrib(gtin);
        prod.setUTrib(item.unidade());
        prod.setQTrib(quantidade);
        prod.setVUnTrib(valorUnitario);
        prod.setIndTot(COMPOE_TOTAL);

        TNFe.InfNFe.Det det = new TNFe.InfNFe.Det();
        det.setNItem(String.valueOf(numeroItem));
        det.setProd(prod);
        det.setImposto(impostoSimplesNacional(item));
        return det;
    }

    /** ICMS com CSOSN 102 e PIS/COFINS com CST 99 zerados, na ordem do schema. */
    private static TNFe.InfNFe.Det.Imposto impostoSimplesNacional(ItemNFe item) {
        TNFe.InfNFe.Det.Imposto.ICMS.ICMSSN102 sn102 = new TNFe.InfNFe.Det.Imposto.ICMS.ICMSSN102();
        sn102.setOrig(item.origem().codigo());
        sn102.setCSOSN(CSOSN_SEM_PERMISSAO_DE_CREDITO);
        TNFe.InfNFe.Det.Imposto.ICMS icms = new TNFe.InfNFe.Det.Imposto.ICMS();
        icms.setICMSSN102(sn102);

        TNFe.InfNFe.Det.Imposto.PIS.PISOutr pisOutr = new TNFe.InfNFe.Det.Imposto.PIS.PISOutr();
        pisOutr.setCST(CST_PIS_COFINS_OUTRAS_OPERACOES);
        pisOutr.setVBC(ZERO);
        pisOutr.setPPIS(ZERO);
        pisOutr.setVPIS(ZERO);
        TNFe.InfNFe.Det.Imposto.PIS pis = new TNFe.InfNFe.Det.Imposto.PIS();
        pis.setPISOutr(pisOutr);

        TNFe.InfNFe.Det.Imposto.COFINS.COFINSOutr cofinsOutr = new TNFe.InfNFe.Det.Imposto.COFINS.COFINSOutr();
        cofinsOutr.setCST(CST_PIS_COFINS_OUTRAS_OPERACOES);
        cofinsOutr.setVBC(ZERO);
        cofinsOutr.setPCOFINS(ZERO);
        cofinsOutr.setVCOFINS(ZERO);
        TNFe.InfNFe.Det.Imposto.COFINS cofins = new TNFe.InfNFe.Det.Imposto.COFINS();
        cofins.setCOFINSOutr(cofinsOutr);

        TNFe.InfNFe.Det.Imposto imposto = new TNFe.InfNFe.Det.Imposto();
        imposto.getContent().add(FABRICA.createTNFeInfNFeDetImpostoICMS(icms));
        imposto.getContent().add(FABRICA.createTNFeInfNFeDetImpostoPIS(pis));
        imposto.getContent().add(FABRICA.createTNFeInfNFeDetImpostoCOFINS(cofins));
        return imposto;
    }

    private static TNFe.InfNFe.Total total(BigDecimal totalProdutos) {
        String valor = totalProdutos.toPlainString();
        TNFe.InfNFe.Total.ICMSTot tot = new TNFe.InfNFe.Total.ICMSTot();
        tot.setVBC(ZERO);
        tot.setVICMS(ZERO);
        tot.setVICMSDeson(ZERO);
        tot.setVFCP(ZERO);
        tot.setVBCST(ZERO);
        tot.setVST(ZERO);
        tot.setVFCPST(ZERO);
        tot.setVFCPSTRet(ZERO);
        tot.setVProd(valor);
        tot.setVFrete(ZERO);
        tot.setVSeg(ZERO);
        tot.setVDesc(ZERO);
        tot.setVII(ZERO);
        tot.setVIPI(ZERO);
        tot.setVIPIDevol(ZERO);
        tot.setVPIS(ZERO);
        tot.setVCOFINS(ZERO);
        tot.setVOutro(ZERO);
        // Sem desconto, frete, seguro, outras despesas nem impostos fora do Simples: o total é o dos produtos.
        tot.setVNF(valor);

        TNFe.InfNFe.Total total = new TNFe.InfNFe.Total();
        total.setICMSTot(tot);
        return total;
    }

    private static TNFe.InfNFe.Transp semTransporte() {
        TNFe.InfNFe.Transp transp = new TNFe.InfNFe.Transp();
        transp.setModFrete(SEM_OCORRENCIA_DE_TRANSPORTE);
        return transp;
    }

    private static TNFe.InfNFe.Pag pag(List<Pagamento> pagamentos, BigDecimal totalNota) {
        TNFe.InfNFe.Pag pag = new TNFe.InfNFe.Pag();
        BigDecimal soma = BigDecimal.ZERO.setScale(2);
        for (Pagamento p : pagamentos) {
            BigDecimal valor = centavos(p.valor(), "Valor do pagamento");
            TNFe.InfNFe.Pag.DetPag det = new TNFe.InfNFe.Pag.DetPag();
            det.setTPag(p.meio().codigo());
            det.setVPag(valor.toPlainString());
            pag.getDetPag().add(det);
            soma = soma.add(valor);
        }
        if (soma.compareTo(totalNota) != 0) {
            throw new DfeException("Os pagamentos somam R$ " + soma.toPlainString() + ", mas o total da nota é R$ "
                    + totalNota.toPlainString() + ".");
        }
        return pag;
    }

    private static String quantidade(BigDecimal quantidade, int numeroItem) {
        try {
            return quantidade.setScale(4, RoundingMode.UNNECESSARY).toPlainString();
        } catch (ArithmeticException e) {
            throw new DfeException("Item " + numeroItem + ": a quantidade " + quantidade.toPlainString()
                    + " tem mais de 4 casas decimais.", e);
        }
    }

    private static String valorUnitario(BigDecimal valor, int numeroItem) {
        BigDecimal semZeros = valor.stripTrailingZeros();
        if (semZeros.scale() > 10) {
            throw new DfeException("Item " + numeroItem + ": o valor unitário " + valor.toPlainString()
                    + " tem mais de 10 casas decimais.");
        }
        int casas = Math.max(2, semZeros.scale());
        return valor.setScale(casas, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static BigDecimal centavos(BigDecimal valor, String descricao) {
        try {
            return valor.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new DfeException(descricao + " com mais de 2 casas decimais: " + valor.toPlainString() + ".", e);
        }
    }
}
