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
import javax.net.ssl.TrustManagerFactory;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
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

    private HttpsServer sefazFalsa;
    private URI endereco;

    @BeforeEach
    void subirSefazFalsa() throws Exception {
        KeyStore ksServidor = KeyStore.getInstance("PKCS12");
        ksServidor.load(null, null);
        ksServidor.setKeyEntry("srv", servidor.chaves().getPrivate(), SENHA,
                new X509Certificate[]{servidor.certificado()});
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ksServidor, SENHA);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(empresa.comoTruststore()); // só aceita o certificado da empresa
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        sefazFalsa = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
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
        endereco = URI.create("https://localhost:" + sefazFalsa.getAddress().getPort() + "/ws/NfeStatusServico4.asmx");
    }

    @AfterEach
    void derrubar() {
        sefazFalsa.stop(0);
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

    @Test
    void servidorNaoConfiavelGeraErroDeTls() {
        CertificadoA1 cert = CertificadoA1.carregar(empresa.comoPfx(SENHA), SENHA);
        ClienteSefaz cliente = ClienteSefaz.para(cert).proxy(null).construir(); // sem truststore extra

        assertThatThrownBy(() -> cliente.enviar(endereco, ServicoSefaz.STATUS_SERVICO, "<x/>"))
                .isInstanceOf(SefazComunicacaoException.class)
                .hasMessageContaining("TLS");
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
}
