package br.com.sistemanfe.dfe.ws;

import br.com.sistemanfe.dfe.DfeException;
import br.com.sistemanfe.dfe.certificado.CertificadoA1;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/**
 * Monta o {@link SSLContext} de TLS mútuo com a Sefaz para UM certificado (uma empresa).
 *
 * <p>Confiança no servidor: aceita o que o Java já confia <b>ou</b> o que estiver na
 * truststore extra (normalmente a cadeia ICP-Brasil, que não vem no Java).
 */
public final class ContextoTls {

    static final String MENSAGEM_SERVIDOR_NAO_CONFIAVEL = "Certificado do servidor da Sefaz não é confiável";

    private ContextoTls() {
    }

    public static SSLContext criar(CertificadoA1 certificado, KeyStore confiancaExtra) {
        try {
            // KeyStore temporária só em memória, com senha aleatória, para o KeyManager.
            byte[] aleatorio = new byte[16];
            new SecureRandom().nextBytes(aleatorio);
            char[] senhaInterna = HexFormat.of().formatHex(aleatorio).toCharArray();
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, null);
            ks.setKeyEntry("certificado", certificado.chavePrivada(), senhaInterna,
                    completarCadeia(certificado.cadeia(), confiancaExtra).toArray(new X509Certificate[0]));
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, senhaInterna);
            Arrays.fill(senhaInterna, '\0');

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kmf.getKeyManagers(), new TrustManager[]{confianca(confiancaExtra)}, new SecureRandom());
            return ctx;
        } catch (GeneralSecurityException | IOException e) {
            throw new DfeException("Não foi possível preparar a conexão segura com o certificado.", e);
        }
    }

    /**
     * Completa a cadeia do certificado com as ACs intermediárias encontradas na truststore.
     *
     * <p>Muitos .pfx trazem só o certificado da empresa. O servidor da Sefaz precisa da
     * cadeia (ex.: AC SAFEWEB RFB → AC Secretaria da Receita Federal → AC Raiz) para
     * aceitar o certificado; sem ela, a conexão pode ser recusada com HTTP 403.
     * A raiz autoassinada não é enviada (o servidor já a tem).
     */
    public static List<X509Certificate> completarCadeia(List<X509Certificate> cadeia, KeyStore confianca) {
        List<X509Certificate> resultado = new ArrayList<>(cadeia);
        if (confianca == null || resultado.isEmpty()) {
            return resultado;
        }
        List<X509Certificate> disponiveis = new ArrayList<>();
        try {
            for (String alias : java.util.Collections.list(confianca.aliases())) {
                if (confianca.getCertificate(alias) instanceof X509Certificate x) {
                    disponiveis.add(x);
                }
            }
        } catch (GeneralSecurityException e) {
            return resultado;
        }
        for (int i = 0; i < 10; i++) {
            X509Certificate ultimo = resultado.get(resultado.size() - 1);
            if (autoassinado(ultimo)) {
                break;
            }
            X509Certificate emissor = acharEmissor(ultimo, disponiveis);
            if (emissor == null || autoassinado(emissor) || resultado.contains(emissor)) {
                break;
            }
            resultado.add(emissor);
        }
        return resultado;
    }

    private static X509Certificate acharEmissor(X509Certificate cert, List<X509Certificate> candidatos) {
        for (X509Certificate c : candidatos) {
            if (c.getSubjectX500Principal().equals(cert.getIssuerX500Principal())) {
                try {
                    cert.verify(c.getPublicKey());
                    return c;
                } catch (GeneralSecurityException e) {
                    // mesmo nome, chave diferente (ex.: AC renovada): tenta o próximo
                }
            }
        }
        return null;
    }

    private static boolean autoassinado(X509Certificate c) {
        if (!c.getSubjectX500Principal().equals(c.getIssuerX500Principal())) {
            return false;
        }
        try {
            c.verify(c.getPublicKey());
            return true;
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    /** Lê uma truststore (PKCS12 ou JKS) do disco. */
    public static KeyStore lerTruststore(Path arquivo, char[] senha) {
        String tipo = arquivo.toString().toLowerCase().endsWith(".jks") ? "JKS" : "PKCS12";
        try (InputStream in = Files.newInputStream(arquivo)) {
            KeyStore ks = KeyStore.getInstance(tipo);
            ks.load(in, senha);
            return ks;
        } catch (IOException | GeneralSecurityException e) {
            throw new DfeException("Não foi possível ler a truststore " + arquivo + ": " + e.getMessage(), e);
        }
    }

    private static X509TrustManager confianca(KeyStore extra) throws GeneralSecurityException {
        List<X509TrustManager> gerentes = new ArrayList<>();
        gerentes.add(gerentePara(null)); // confiança padrão do Java
        if (extra != null) {
            gerentes.add(gerentePara(extra));
        }
        return new ConfiancaComposta(gerentes);
    }

    private static X509TrustManager gerentePara(KeyStore ks) throws GeneralSecurityException {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager x) {
                return x;
            }
        }
        throw new GeneralSecurityException("Nenhum X509TrustManager disponível.");
    }

    /** Aceita o certificado do servidor se qualquer um dos gerentes aceitar. */
    private record ConfiancaComposta(List<X509TrustManager> gerentes) implements X509TrustManager {

        @Override
        public void checkServerTrusted(X509Certificate[] cadeia, String tipo) throws CertificateException {
            CertificateException ultima = null;
            for (X509TrustManager g : gerentes) {
                try {
                    g.checkServerTrusted(cadeia, tipo);
                    return;
                } catch (CertificateException e) {
                    ultima = e;
                }
            }
            throw new CertificateException(MENSAGEM_SERVIDOR_NAO_CONFIAVEL + " (falta a cadeia ICP-Brasil).", ultima);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] cadeia, String tipo) throws CertificateException {
            throw new CertificateException("Uso apenas como cliente.");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return gerentes.stream().flatMap(g -> Arrays.stream(g.getAcceptedIssuers())).toArray(X509Certificate[]::new);
        }
    }
}
