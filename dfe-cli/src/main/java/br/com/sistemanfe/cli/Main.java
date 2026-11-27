package br.com.sistemanfe.cli;

import br.com.sistemanfe.dfe.DfeException;
import br.com.sistemanfe.dfe.certificado.CertificadoA1;
import br.com.sistemanfe.dfe.certificado.TruststoreIcpBrasil;
import br.com.sistemanfe.dfe.comum.Ambiente;
import br.com.sistemanfe.dfe.comum.ModeloDocumento;
import br.com.sistemanfe.dfe.comum.UF;
import br.com.sistemanfe.dfe.servico.ConsultaStatusServico;
import br.com.sistemanfe.dfe.servico.RetornoStatusServico;
import br.com.sistemanfe.dfe.ws.CatalogoWebServices;
import br.com.sistemanfe.dfe.ws.ClienteSefaz;
import br.com.sistemanfe.dfe.ws.ContextoTls;
import br.com.sistemanfe.dfe.ws.ServicoSefaz;

import javax.security.auth.x500.X500Principal;
import java.io.Console;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Linha de comando para testar a comunicação com a Sefaz.
 *
 * <p>A senha do certificado NUNCA é passada como argumento (ficaria visível na lista
 * de processos e no histórico do terminal): vem da variável {@code NFE_PFX_SENHA}
 * ou é digitada.
 */
public final class Main {

    static final int OK = 0;
    static final int ERRO = 1;
    static final int SEFAZ_FORA = 2;
    static final int USO = 64;

    static final String TRUSTSTORE_PADRAO = "icpbrasil.p12";

    private static final String AJUDA = """
            Uso:
              java -jar dfe-cli.jar certificado --pfx <arquivo.pfx>
              java -jar dfe-cli.jar truststore --zip <ACcompactado.zip> [--saida icpbrasil.p12]
              java -jar dfe-cli.jar truststore --pasta <pasta-com-certificados> [--saida icpbrasil.p12]
              java -jar dfe-cli.jar status --pfx <arquivo.pfx> [opções]

            Opções do comando status:
              --ambiente homologacao|producao   (padrão: homologacao)
              --modelo nfe|nfce                 (padrão: nfe)
              --uf RS                           (padrão: RS)
              --truststore <arquivo.p12|.jks>   cadeia ICP-Brasil (padrão: icpbrasil.p12 da pasta atual, se existir)
              --tls 1.2|1.3|auto                versão do TLS (padrão: 1.2; a Sefaz RS não aceita 1.3)
              --verbose                         mostra o XML enviado e recebido

            Senhas:
              NFE_PFX_SENHA         senha do certificado (se ausente, será pedida)
              NFE_TRUSTSTORE_SENHA  senha da truststore (padrão: changeit)

            Diagnóstico do TLS (mostra se o certificado da empresa foi enviado):
              java -Djavax.net.debug=ssl:handshake -jar dfe-cli.jar status --pfx <arquivo.pfx>
            """;

    private static final String DICA_403 = """
            Dica: rode de novo com java -Djavax.net.debug=ssl:handshake -jar dfe-cli.jar status ...
            e procure "Produced client Certificate handshake message": se vier "<empty list>",
            nenhum certificado foi enviado. Confira também se a "Cadeia enviada" acima termina
            numa raiz ICP-Brasil.""";

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(executar(args));
    }

    static int executar(String[] args) {
        if (args.length == 0 || args[0].equals("-h") || args[0].equals("--help") || args[0].equals("ajuda")) {
            System.out.println(AJUDA);
            return args.length == 0 ? USO : OK;
        }
        Map<String, String> opcoes;
        try {
            opcoes = lerOpcoes(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println(AJUDA);
            return USO;
        }
        try {
            return switch (args[0]) {
                case "certificado" -> certificado(opcoes);
                case "status" -> status(opcoes);
                case "truststore" -> truststore(opcoes);
                default -> {
                    System.err.println("Comando desconhecido: " + args[0]);
                    System.err.println(AJUDA);
                    yield USO;
                }
            };
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return USO;
        } catch (DfeException e) {
            System.err.println("ERRO: " + e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("HTTP 403")) {
                System.err.println(DICA_403);
            }
            if (opcoes.containsKey("verbose") && e.getCause() != null) {
                e.printStackTrace();
            }
            return ERRO;
        }
    }

    private static int certificado(Map<String, String> op) {
        CertificadoA1 cert = abrirCertificado(op);
        imprimirCertificado(cert);
        return OK;
    }

    private static int status(Map<String, String> op) {
        Ambiente ambiente = switch (op.getOrDefault("ambiente", "homologacao").toLowerCase()) {
            case "homologacao", "2" -> Ambiente.HOMOLOGACAO;
            case "producao", "1" -> Ambiente.PRODUCAO;
            default -> throw new IllegalArgumentException("--ambiente deve ser homologacao ou producao");
        };
        ModeloDocumento modelo = switch (op.getOrDefault("modelo", "nfe").toLowerCase()) {
            case "nfe", "55" -> ModeloDocumento.NFE;
            case "nfce", "65" -> ModeloDocumento.NFCE;
            default -> throw new IllegalArgumentException("--modelo deve ser nfe ou nfce");
        };
        UF uf;
        try {
            uf = UF.valueOf(op.getOrDefault("uf", "RS").toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("--uf inválida: " + op.get("uf"));
        }

        CertificadoA1 cert = abrirCertificado(op);
        imprimirCertificado(cert);

        if (cert.vencido(Clock.systemDefaultZone())) {
            System.out.println("\nATENÇÃO: o certificado está VENCIDO. A Sefaz vai recusar a conexão;"
                    + " use um certificado A1 válido.");
        }

        ClienteSefaz.Construtor construtor = ClienteSefaz.para(cert);
        Path ts = op.containsKey("truststore") ? Path.of(op.get("truststore")) : Path.of(TRUSTSTORE_PADRAO);
        if (op.containsKey("truststore") && !Files.isRegularFile(ts)) {
            throw new IllegalArgumentException("Truststore não encontrada: " + ts.toAbsolutePath());
        }
        KeyStore truststore = null;
        if (Files.isRegularFile(ts)) {
            System.out.println("Truststore: " + ts.toAbsolutePath());
            truststore = ContextoTls.lerTruststore(ts, senhaTruststore());
            construtor.confiancaExtra(truststore);
        } else {
            System.out.println("Truststore: (nenhuma; usando só a confiança padrão do Java)");
        }
        int noPfx = cert.cadeia().size();
        List<X509Certificate> enviada = ContextoTls.completarCadeia(cert.cadeia(), truststore);
        System.out.println("Cadeia   : " + noPfx + " certificado(s) no .pfx"
                + (enviada.size() > noPfx ? ", " + enviada.size() + " enviados (completada pela truststore)" : ""));
        System.out.println("Cadeia enviada:");
        for (int i = 0; i < enviada.size(); i++) {
            X509Certificate c = enviada.get(i);
            System.out.println("  " + (i + 1) + ". " + nomeComum(c.getSubjectX500Principal())
                    + "  (emitido por " + nomeComum(c.getIssuerX500Principal()) + ")");
        }

        String tls = op.getOrDefault("tls", "1.2");
        switch (tls) {
            case "1.2" -> construtor.protocolosTls("TLSv1.2");
            case "1.3" -> construtor.protocolosTls("TLSv1.3");
            case "auto" -> construtor.protocolosTls("TLSv1.3", "TLSv1.2");
            default -> throw new IllegalArgumentException("--tls deve ser 1.2, 1.3 ou auto");
        }
        System.out.println("TLS      : " + tls);
        if (op.containsKey("verbose")) {
            construtor.ouvinte(new ClienteSefaz.OuvinteMensagens() {
                @Override
                public void enviando(ServicoSefaz servico, URI endereco, String envelope) {
                    System.out.println("\n>>> " + servico + " " + endereco + "\n" + envelope);
                }

                @Override
                public void recebido(ServicoSefaz servico, URI endereco, int statusHttp, String corpo) {
                    System.out.println("\n<<< HTTP " + statusHttp + "\n" + corpo + "\n");
                }
            });
        }

        System.out.printf("%nConsultando status: %s / %s / %s ...%n", uf, nome(modelo), nome(ambiente));
        RetornoStatusServico r = new ConsultaStatusServico(construtor.construir(), CatalogoWebServices.padrao())
                .consultar(uf, modelo, ambiente);

        System.out.println("cStat    : " + r.cStat());
        System.out.println("xMotivo  : " + r.xMotivo());
        System.out.println("dhRecbto : " + r.dhRecbto());
        if (r.tMedSegundos() != null) {
            System.out.println("tMed     : " + r.tMedSegundos() + " s");
        }
        if (r.xObs() != null) {
            System.out.println("xObs     : " + r.xObs());
        }
        if (r.emOperacao()) {
            System.out.println("\nOK: serviço em operação.");
            return OK;
        }
        System.out.println("\nA Sefaz respondeu, mas o serviço não está em operação.");
        return SEFAZ_FORA;
    }

    private static int truststore(Map<String, String> op) {
        List<X509Certificate> certs;
        if (op.containsKey("zip")) {
            certs = TruststoreIcpBrasil.lerZip(arquivoExistente(op.get("zip")));
        } else if (op.containsKey("pasta")) {
            Path pasta = Path.of(op.get("pasta"));
            if (!Files.isDirectory(pasta)) {
                throw new IllegalArgumentException("Pasta não encontrada: " + pasta.toAbsolutePath());
            }
            certs = TruststoreIcpBrasil.lerPasta(pasta);
        } else {
            throw new IllegalArgumentException("Informe --zip <ACcompactado.zip> ou --pasta <pasta>");
        }
        Path saida = Path.of(op.getOrDefault("saida", TRUSTSTORE_PADRAO));
        KeyStore ks = TruststoreIcpBrasil.criar(certs);
        TruststoreIcpBrasil.gravar(ks, saida, senhaTruststore());
        System.out.println(certs.size() + " certificados gravados em " + saida.toAbsolutePath());
        System.out.println("Rode o comando status a partir desta pasta para usá-la automaticamente.");
        return OK;
    }

    private static Path arquivoExistente(String caminho) {
        Path p = Path.of(caminho);
        if (!Files.isRegularFile(p)) {
            throw new IllegalArgumentException("Arquivo não encontrado: " + p.toAbsolutePath());
        }
        return p;
    }

    private static char[] senhaTruststore() {
        return System.getenv().getOrDefault("NFE_TRUSTSTORE_SENHA", "changeit").toCharArray();
    }

    private static CertificadoA1 abrirCertificado(Map<String, String> op) {
        String pfx = op.get("pfx");
        if (pfx == null) {
            throw new IllegalArgumentException("Informe o certificado com --pfx <arquivo.pfx>");
        }
        Path caminho = Path.of(pfx);
        if (!Files.isRegularFile(caminho)) {
            throw new IllegalArgumentException("Arquivo não encontrado: " + caminho.toAbsolutePath());
        }
        return CertificadoA1.carregar(caminho, senhaCertificado());
    }

    private static char[] senhaCertificado() {
        String env = System.getenv("NFE_PFX_SENHA");
        if (env != null) {
            return env.toCharArray();
        }
        Console c = System.console();
        if (c == null) {
            throw new IllegalArgumentException(
                    "Sem terminal para digitar a senha. Defina a variável de ambiente NFE_PFX_SENHA.");
        }
        char[] senha = c.readPassword("Senha do certificado: ");
        return senha == null ? new char[0] : senha;
    }

    private static void imprimirCertificado(CertificadoA1 cert) {
        Clock relogio = Clock.systemDefaultZone();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());
        System.out.println("Certificado");
        System.out.println("  Titular  : " + cert.nomeTitular());
        System.out.println("  CNPJ     : " + cert.cnpj().map(Main::formatarCnpj).orElse("(não encontrado)"));
        System.out.println("  Emissor  : " + cert.certificado().getIssuerX500Principal().getName());
        System.out.println("  Validade : " + fmt.format(cert.validoDesde()) + " a " + fmt.format(cert.validoAte())
                + (cert.vencido(relogio) ? "  ** VENCIDO **" : "  (" + cert.diasParaVencer(relogio) + " dias restantes)"));
    }

    /** CN de um nome X.500, ex.: {@code Autoridade Certificadora Raiz Brasileira v5}. */
    static String nomeComum(X500Principal nome) {
        String dn = nome.getName(X500Principal.RFC2253);
        for (String parte : dn.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")) {
            if (parte.regionMatches(true, 0, "CN=", 0, 3)) {
                return parte.substring(3).replace("\\", "");
            }
        }
        return dn;
    }

    static String formatarCnpj(String c) {
        return c.length() != 14 ? c
                : c.substring(0, 2) + "." + c.substring(2, 5) + "." + c.substring(5, 8) + "/"
                + c.substring(8, 12) + "-" + c.substring(12);
    }

    private static String nome(Ambiente a) {
        return a == Ambiente.HOMOLOGACAO ? "homologação" : "produção";
    }

    private static String nome(ModeloDocumento m) {
        return m == ModeloDocumento.NFE ? "NF-e (55)" : "NFC-e (65)";
    }

    static Map<String, String> lerOpcoes(String[] args) {
        Map<String, String> op = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (!a.startsWith("--")) {
                throw new IllegalArgumentException("Argumento inesperado: " + a);
            }
            String chave = a.substring(2);
            if (chave.equals("verbose")) {
                op.put(chave, "true");
                continue;
            }
            if (chave.equals("senha")) {
                throw new IllegalArgumentException(
                        "Por segurança, a senha não é aceita como argumento. Use a variável NFE_PFX_SENHA.");
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("Falta o valor de " + a);
            }
            op.put(chave, args[++i]);
        }
        return op;
    }
}
