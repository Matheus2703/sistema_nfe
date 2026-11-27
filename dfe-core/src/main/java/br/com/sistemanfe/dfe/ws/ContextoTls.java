package br.com.sistemanfe.dfe.ws;

import br.com.sistemanfe.dfe.DfeException;
import br.com.sistemanfe.dfe.certificado.CertificadoA1;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Monta o {@link SSLContext} de TLS mútuo com a Sefaz para UM certificado (uma empresa).
 *
 * <p>Certificado do cliente: o da empresa é sempre apresentado (ver {@link CertificadoDaEmpresa}).
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
            List<X509Certificate> cadeia = completarCadeia(certificado.cadeia(), confiancaExtra);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(new KeyManager[]{new CertificadoDaEmpresa(certificado.chavePrivada(), cadeia)},
                    new TrustManager[]{confianca(confiancaExtra)}, new SecureRandom());
            return ctx;
        } catch (GeneralSecurityException e) {
            throw new DfeException("Não foi possível preparar a conexão segura com o certificado.", e);
        }
    }

    /**
     * Completa a cadeia do certificado com as ACs intermediárias encontradas na truststore.
     *
     * <p>Muitos .pfx trazem só o certificado da empresa. Enviar também as intermediárias
     * (ex.: AC SAFEWEB RFB → AC Secretaria da Receita Federal) permite que o servidor da
     * Sefaz monte a cadeia até a raiz ICP-Brasil mesmo sem ter essas ACs instaladas.
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

    /**
     * Gerente de chaves com um único certificado: o da empresa, sempre apresentado.
     *
     * <p>A Sefaz RS pede o certificado do cliente no handshake e manda a lista de ACs que
     * aceita, que traz só as raízes (ex.: "Autoridade Certificadora Raiz Brasileira v5").
     * O gerente padrão do Java só apresenta um certificado quando algum da cadeia foi
     * emitido por uma AC da lista. Um .pfx com só o certificado da empresa (emitido por
     * uma AC intermediária) não passa nesse filtro: o Java não envia nada e o IIS responde
     * HTTP 403 (403.7). Como cada conexão tem exatamente um certificado, este gerente
     * ignora a lista e deixa a decisão para o servidor. Só o tipo de chave é respeitado.
     */
    static final class CertificadoDaEmpresa extends X509ExtendedKeyManager {

        static final String ALIAS = "empresa";

        private final PrivateKey chave;
        private final X509Certificate[] cadeia;

        CertificadoDaEmpresa(PrivateKey chave, List<X509Certificate> cadeia) {
            if (cadeia.isEmpty()) {
                throw new IllegalArgumentException("Cadeia do certificado vazia.");
            }
            this.chave = chave;
            this.cadeia = cadeia.toArray(new X509Certificate[0]);
        }

        @Override
        public String chooseClientAlias(String[] tiposDeChave, Principal[] emissores, Socket socket) {
            return aliasPara(tiposDeChave);
        }

        @Override
        public String chooseEngineClientAlias(String[] tiposDeChave, Principal[] emissores, SSLEngine engine) {
            return aliasPara(tiposDeChave);
        }

        @Override
        public String[] getClientAliases(String tipoDeChave, Principal[] emissores) {
            return aliasPara(new String[]{tipoDeChave}) == null ? null : new String[]{ALIAS};
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return ALIAS.equals(alias) ? cadeia.clone() : null;
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            return ALIAS.equals(alias) ? chave : null;
        }

        @Override
        public String chooseServerAlias(String tipoDeChave, Principal[] emissores, Socket socket) {
            return null; // uso apenas como cliente
        }

        @Override
        public String[] getServerAliases(String tipoDeChave, Principal[] emissores) {
            return null;
        }

        private String aliasPara(String[] tiposDeChave) {
            if (tiposDeChave == null) {
                return null;
            }
            String algoritmo = cadeia[0].getPublicKey().getAlgorithm();
            for (String tipo : tiposDeChave) {
                if (algoritmo.equalsIgnoreCase(tipo)) {
                    return ALIAS;
                }
            }
            return null;
        }
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
