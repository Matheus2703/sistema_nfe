package br.com.sistemanfe.dfe.ws;

import br.com.sistemanfe.dfe.certificado.CertificadoA1;
import br.com.sistemanfe.dfe.comum.Ambiente;
import br.com.sistemanfe.dfe.comum.ModeloDocumento;
import br.com.sistemanfe.dfe.comum.UF;
import br.com.sistemanfe.dfe.servico.ConsultaStatusServico;
import br.com.sistemanfe.dfe.servico.RetornoStatusServico;
import br.com.sistemanfe.dfe.teste.CertificadosDeTeste;
import br.com.sistemanfe.dfe.teste.RespostasSefaz;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sobe uma "Sefaz falsa" local que EXIGE certificado do cliente (como a real) e
 * verifica a conversa completa: TLS mútuo, cabeçalhos SOAP 1.2, envelope e retorno.
 */
class ClienteSefazTlsMutuoTest {

    private static final char[] SENHA = "teste".toCharArray();

    private final CertificadosDeTeste.Gerado empresa =
            CertificadosDeTeste.eCnpj("EMPRESA TESTE LTDA", "12345678000195", null, 365);
    private final CertificadosDeTeste.Gerado servidor = CertificadosDeTeste.servidorLocalhost();

    private final AtomicReference<String> contentTypeRecebido = new AtomicReference<>();
    private final AtomicReference<String> corpoRecebido = new AtomicReference<>();
    private final AtomicReference<String> clienteApresentado = new AtomicReference<>();

    private final List<HttpsServer> servidores = new ArrayList<>();
    private URI endereco;

    @BeforeEach
    void subirSefazFalsa() throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(empresa.comoTruststore()); // só aceita o certificado da empresa
        endereco = subir(tmf.getTrustManagers());
    }

    /** Sobe uma Sefaz falsa que exige certificado do cliente e confia nele conforme {@code confianca}. */
    private URI subir(TrustManager[] confianca) throws Exception {
        KeyStore ksServidor = KeyStore.getInstance("PKCS12");
        ksServidor.load(null, null);
        ksServidor.setKeyEntry("srv", servidor.chaves().getPrivate(), SENHA,
                new X509Certificate[]{servidor.certificado()});
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ksServidor, SENHA);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), confianca, null);

        HttpsServer sefazFalsa = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
        sefazFalsa.setHttpsConfigurator(new HttpsConfigurator(ctx) {
            @Override
            public void configure(HttpsParameters params) {
                SSLParameters p = getSSLContext().getDefaultSSLParameters();
                p.setNeedClientAuth(true);
                params.setSSLParameters(p);
            }
        });
        sefazFalsa.createContext("/ws/NfeStatusServico4.asmx", troca -> {
            HttpsExchange https = (HttpsExchange) troca;
            clienteApresentado.set(((X509Certificate) https.getSSLSession().getPeerCertificates()[0])
                    .getSubjectX500Principal().getName());
            contentTypeRecebido.set(troca.getRequestHeaders().getFirst("Content-Type"));
            corpoRecebido.set(new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] resposta = RespostasSefaz.STATUS_107.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/soap+xml; charset=utf-8");
            troca.sendResponseHeaders(200, resposta.length);
            troca.getResponseBody().write(resposta);
            troca.close();
        });
        sefazFalsa.start();
        servidores.add(sefazFalsa);
        return URI.create("https://localhost:" + sefazFalsa.getAddress().getPort() + "/ws/NfeStatusServico4.asmx");
    }

    @AfterEach
    void derrubar() {
        servidores.forEach(s -> s.stop(0));
    }

    @Test
    void consultaStatusComTlsMutuo() {
        CertificadoA1 cert = CertificadoA1.carregar(empresa.comoPfx(SENHA), SENHA);
        ClienteSefaz cliente = ClienteSefaz.para(cert)
                .confiancaExtra(servidor.comoTruststore())
                .proxy(null)
                .construir();

        RetornoStatusServico r = new ConsultaStatusServico(cliente, CatalogoWebServices.fixo(endereco))
                .consultar(UF.RS, ModeloDocumento.NFE, Ambiente.HOMOLOGACAO);

        assertThat(r.emOperacao()).isTrue();
        assertThat(clienteApresentado.get()).contains("CN=EMPRESA TESTE LTDA");
        assertThat(contentTypeRecebido.get())
                .startsWith("application/soap+xml")
                .contains("action=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeStatusServico4/nfeStatusServicoNF\"");
        assertThat(corpoRecebido.get()).contains(
                "<nfeDadosMsg xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeStatusServico4\">"
                        + "<consStatServ xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">"
                        + "<tpAmb>2</tpAmb><cUF>43</cUF><xServ>STATUS</xServ></consStatServ></nfeDadosMsg>");
    }

    /**
     * Como a Sefaz RS: o servidor pede o certificado anunciando só a AC RAIZ como aceita, e o
     * .pfx traz só o certificado da empresa (emitido por uma AC intermediária). O gerente de
     * chaves padrão do Java não envia nada nesse caso, e o IIS responde HTTP 403.
     */
    @Test
    void enviaOCertificadoMesmoQuandoOServidorSoAnunciaARaiz() throws Exception {
        var raiz = CertificadosDeTeste.acRaiz("AC RAIZ TESTE");
        var ac = CertificadosDeTeste.emitidoPor(raiz, "AC EMISSORA TESTE");
        var empresaIcp = CertificadosDeTeste.emitidoPor(ac, "EMPRESA ICP LTDA:12345678000195");
        URI sefazRs = subir(new TrustManager[]{new AnunciaSoARaiz(raiz.certificado(), ac.certificado())});

        CertificadoA1 cert = CertificadoA1.carregar(empresaIcp.comoPfx(SENHA), SENHA);
        ClienteSefaz cliente = ClienteSefaz.para(cert)
                .confiancaExtra(servidor.comoTruststore()) // sem as ACs: a cadeia não é completada
                .proxy(null)
                .construir();

        RetornoStatusServico r = new ConsultaStatusServico(cliente, CatalogoWebServices.fixo(sefazRs))
                .consultar(UF.RS, ModeloDocumento.NFE, Ambiente.HOMOLOGACAO);

        assertThat(r.emOperacao()).isTrue();
        assertThat(clienteApresentado.get()).contains("CN=EMPRESA ICP LTDA");
    }

    @Test
    void servidorNaoConfiavelGeraErroDeTls() {
        CertificadoA1 cert = CertificadoA1.carregar(empresa.comoPfx(SENHA), SENHA);
        ClienteSefaz cliente = ClienteSefaz.para(cert).proxy(null).construir(); // sem truststore extra

        assertThatThrownBy(() -> cliente.enviar(endereco, ServicoSefaz.STATUS_SERVICO, "<x/>"))
                .isInstanceOf(SefazComunicacaoException.class)
                .hasMessageContaining("ICP-Brasil");
    }

    @Test
    void certificadoDeOutraEmpresaERecusadoPeloServidor() {
        var outra = CertificadosDeTeste.eCnpj("OUTRA EMPRESA", "11222333000181", null, 365);
        CertificadoA1 cert = CertificadoA1.carregar(outra.comoPfx(SENHA), SENHA);
        ClienteSefaz cliente = ClienteSefaz.para(cert)
                .confiancaExtra(servidor.comoTruststore())
                .proxy(null)
                .construir();

        assertThatThrownBy(() -> cliente.enviar(endereco, ServicoSefaz.STATUS_SERVICO, "<x/>"))
                .isInstanceOf(SefazComunicacaoException.class);
    }

    /**
     * Confiança do servidor no estilo do IIS da Sefaz: aceita empresas emitidas pela AC,
     * mas no pedido de certificado anuncia só a raiz.
     */
    private record AnunciaSoARaiz(X509Certificate raiz, X509Certificate ac) implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] cadeia, String tipo) throws CertificateException {
            try {
                cadeia[0].verify(ac.getPublicKey());
            } catch (GeneralSecurityException e) {
                throw new CertificateException("Certificado do cliente não foi emitido pela AC esperada.", e);
            }
        }

        @Override
        public void checkServerTrusted(X509Certificate[] cadeia, String tipo) throws CertificateException {
            throw new CertificateException("Uso apenas como servidor.");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[]{raiz};
        }
    }
}
