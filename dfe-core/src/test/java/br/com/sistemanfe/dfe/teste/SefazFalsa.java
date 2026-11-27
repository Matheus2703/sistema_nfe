package br.com.sistemanfe.dfe.teste;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicReference;

/**
 * "Sefaz falsa" local: servidor HTTPS que EXIGE o certificado do cliente (como a real), só confia
 * no certificado da empresa e responde sempre a mesma mensagem SOAP no caminho indicado.
 * Guarda o que recebeu para o teste conferir.
 */
public final class SefazFalsa implements AutoCloseable {

    private static final char[] SENHA = "teste".toCharArray();

    private final HttpsServer servidor;
    private final URI endereco;
    private final AtomicReference<String> contentType = new AtomicReference<>();
    private final AtomicReference<String> corpo = new AtomicReference<>();
    private final AtomicReference<String> cliente = new AtomicReference<>();

    private SefazFalsa(CertificadosDeTeste.Gerado empresa, CertificadosDeTeste.Gerado certificadoServidor,
                       String caminho, String respostaSoap) throws IOException, GeneralSecurityException {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry("srv", certificadoServidor.chaves().getPrivate(), SENHA,
                new X509Certificate[]{certificadoServidor.certificado()});
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, SENHA);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(empresa.comoTruststore());
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        servidor = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.setHttpsConfigurator(new HttpsConfigurator(ctx) {
            @Override
            public void configure(HttpsParameters params) {
                SSLParameters p = getSSLContext().getDefaultSSLParameters();
                p.setNeedClientAuth(true);
                params.setSSLParameters(p);
            }
        });
        servidor.createContext(caminho, troca -> {
            HttpsExchange https = (HttpsExchange) troca;
            cliente.set(((X509Certificate) https.getSSLSession().getPeerCertificates()[0])
                    .getSubjectX500Principal().getName());
            contentType.set(troca.getRequestHeaders().getFirst("Content-Type"));
            corpo.set(new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] resposta = respostaSoap.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/soap+xml; charset=utf-8");
            troca.sendResponseHeaders(200, resposta.length);
            troca.getResponseBody().write(resposta);
            troca.close();
        });
        servidor.start();
        endereco = URI.create("https://localhost:" + servidor.getAddress().getPort() + caminho);
    }

    /** Sobe a Sefaz falsa respondendo {@code respostaSoap} em {@code caminho}. */
    public static SefazFalsa subir(CertificadosDeTeste.Gerado empresa, CertificadosDeTeste.Gerado servidor,
                                   String caminho, String respostaSoap) {
        try {
            return new SefazFalsa(empresa, servidor, caminho, respostaSoap);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public URI endereco() {
        return endereco;
    }

    /** Cabeçalho {@code Content-Type} da última requisição. */
    public String contentTypeRecebido() {
        return contentType.get();
    }

    /** Corpo (envelope SOAP) da última requisição. */
    public String corpoRecebido() {
        return corpo.get();
    }

    /** Nome X.500 do certificado que o cliente apresentou no handshake. */
    public String clienteApresentado() {
        return cliente.get();
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}
