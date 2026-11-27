package br.com.sistemanfe.dfe.certificado;

import br.com.sistemanfe.dfe.DfeException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Monta a truststore com a cadeia ICP-Brasil, necessária para o Java confiar nos
 * servidores da Sefaz.
 *
 * <p>Fonte esperada: o "arquivo único compactado" com os certificados das ACs da
 * ICP-Brasil, publicado pelo ITI (gov.br/iti). Aceita o .zip (inclusive com zips
 * dentro) ou uma pasta com arquivos .crt/.cer/.pem (DER ou PEM).
 */
public final class TruststoreIcpBrasil {

    private static final int LIMITE_ARQUIVO = 5 * 1024 * 1024;

    private TruststoreIcpBrasil() {
    }

    /** Lê todos os certificados de um .zip (recursivo em zips internos). */
    public static List<X509Certificate> lerZip(Path zip) {
        Map<String, X509Certificate> unicos = new LinkedHashMap<>();
        try (InputStream in = Files.newInputStream(zip)) {
            lerZip(in, unicos, 0);
        } catch (IOException e) {
            throw new DfeException("Não foi possível ler o arquivo " + zip + ": " + e.getMessage(), e);
        }
        return exigirAlgum(unicos, zip);
    }

    /** Lê todos os certificados .crt/.cer/.pem de uma pasta (recursivo). */
    public static List<X509Certificate> lerPasta(Path pasta) {
        Map<String, X509Certificate> unicos = new LinkedHashMap<>();
        try (Stream<Path> arquivos = Files.walk(pasta)) {
            for (Path p : arquivos.filter(Files::isRegularFile).toList()) {
                String nome = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (nome.endsWith(".zip")) {
                    try (InputStream in = Files.newInputStream(p)) {
                        lerZip(in, unicos, 1);
                    }
                } else if (ehCertificado(nome)) {
                    adicionar(Files.readAllBytes(p), unicos);
                }
            }
        } catch (IOException e) {
            throw new DfeException("Não foi possível ler a pasta " + pasta + ": " + e.getMessage(), e);
        }
        return exigirAlgum(unicos, pasta);
    }

    /** Cria a truststore PKCS12 em memória. */
    public static KeyStore criar(List<X509Certificate> certificados) {
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, null);
            for (X509Certificate c : certificados) {
                ks.setCertificateEntry(alias(c), c);
            }
            return ks;
        } catch (GeneralSecurityException | IOException e) {
            throw new DfeException("Erro ao montar a truststore.", e);
        }
    }

    /** Grava a truststore PKCS12 no disco. */
    public static void gravar(KeyStore ks, Path destino, char[] senha) {
        try (OutputStream out = Files.newOutputStream(destino)) {
            ks.store(out, senha);
        } catch (GeneralSecurityException | IOException e) {
            throw new DfeException("Erro ao gravar a truststore em " + destino + ": " + e.getMessage(), e);
        }
    }

    private static void lerZip(InputStream in, Map<String, X509Certificate> unicos, int profundidade) throws IOException {
        if (profundidade > 3) {
            return;
        }
        ZipInputStream zip = new ZipInputStream(in);
        ZipEntry entrada;
        while ((entrada = zip.getNextEntry()) != null) {
            if (entrada.isDirectory()) {
                continue;
            }
            String nome = entrada.getName().toLowerCase(Locale.ROOT);
            if (nome.endsWith(".zip")) {
                lerZip(new ByteArrayInputStream(lerLimitado(zip)), unicos, profundidade + 1);
            } else if (ehCertificado(nome)) {
                adicionar(lerLimitado(zip), unicos);
            }
        }
    }

    private static byte[] lerLimitado(InputStream in) throws IOException {
        byte[] dados = in.readNBytes(LIMITE_ARQUIVO + 1);
        if (dados.length > LIMITE_ARQUIVO) {
            throw new IOException("arquivo interno grande demais para ser um certificado");
        }
        return dados;
    }

    private static boolean ehCertificado(String nome) {
        return nome.endsWith(".crt") || nome.endsWith(".cer") || nome.endsWith(".pem") || nome.endsWith(".der");
    }

    private static void adicionar(byte[] dados, Map<String, X509Certificate> unicos) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            for (Certificate c : cf.generateCertificates(new ByteArrayInputStream(dados))) {
                if (c instanceof X509Certificate x) {
                    unicos.putIfAbsent(impressaoDigital(x), x);
                }
            }
        } catch (CertificateException e) {
            // Arquivo que não é certificado: ignora.
        }
    }

    private static List<X509Certificate> exigirAlgum(Map<String, X509Certificate> unicos, Path origem) {
        if (unicos.isEmpty()) {
            throw new DfeException("Nenhum certificado encontrado em " + origem
                    + ". Use o arquivo único compactado das ACs da ICP-Brasil (site do ITI).");
        }
        return new ArrayList<>(unicos.values());
    }

    private static String alias(X509Certificate c) {
        String cn = c.getSubjectX500Principal().getName().replaceAll("^.*?CN=([^,]+).*$", "$1");
        return (cn + "-" + impressaoDigital(c).substring(0, 8)).toLowerCase(Locale.ROOT);
    }

    private static String impressaoDigital(X509Certificate c) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(c.getEncoded()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
