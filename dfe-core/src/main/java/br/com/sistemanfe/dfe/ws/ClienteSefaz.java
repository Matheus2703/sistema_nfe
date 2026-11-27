package br.com.sistemanfe.dfe.ws;

import br.com.sistemanfe.dfe.certificado.CertificadoA1;
import org.w3c.dom.Element;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.ConnectException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.Duration;

/**
 * Envia mensagens aos web services da Sefaz com TLS mútuo (certificado da empresa).
 *
 * <p>Uma instância por certificado/empresa. É thread-safe e deve ser reaproveitada
 * (guarda a conexão TLS).
 */
public final class ClienteSefaz {

    public static final Duration TIMEOUT_CONEXAO_PADRAO = Duration.ofSeconds(15);
    public static final Duration TIMEOUT_RESPOSTA_PADRAO = Duration.ofSeconds(60);

    private final HttpClient http;
    private final Duration timeoutResposta;
    private final OuvinteMensagens ouvinte;

    private ClienteSefaz(HttpClient http, Duration timeoutResposta, OuvinteMensagens ouvinte) {
        this.http = http;
        this.timeoutResposta = timeoutResposta;
        this.ouvinte = ouvinte;
    }

    public static Construtor para(CertificadoA1 certificado) {
        return new Construtor(certificado);
    }

    /**
     * Envia {@code mensagemXml} ao serviço e devolve o elemento de retorno
     * (ex.: {@code <retConsStatServ>}).
     */
    public Element enviar(URI endereco, ServicoSefaz servico, String mensagemXml) {
        String envelope = EnvelopeSoap.montar(servico, mensagemXml);
        ouvinte.enviando(servico, endereco, envelope);

        HttpRequest req = HttpRequest.newBuilder(endereco)
                .timeout(timeoutResposta)
                .header("Content-Type",
                        "application/soap+xml; charset=utf-8; action=\"" + servico.soapAction() + "\"")
                .POST(HttpRequest.BodyPublishers.ofString(envelope, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpConnectTimeoutException e) {
            throw new SefazComunicacaoException("Tempo esgotado ao conectar na Sefaz (" + endereco.getHost() + ").", false, e);
        } catch (HttpTimeoutException e) {
            throw new SefazComunicacaoException(
                    "A Sefaz não respondeu a tempo. A mensagem pode ter sido processada: consulte antes de reenviar.",
                    true, e);
        } catch (ConnectException | UnknownHostException e) {
            throw new SefazComunicacaoException("Não foi possível conectar na Sefaz (" + endereco.getHost() + "): "
                    + e.getMessage(), false, e);
        } catch (SSLException e) {
            if (servidorNaoConfiavel(e)) {
                throw new SefazComunicacaoException(
                        "O Java não confia no certificado do servidor da Sefaz: falta a cadeia ICP-Brasil. "
                                + "Gere a truststore ICP-Brasil e informe-a na conexão.", false, e);
            }
            throw new SefazComunicacaoException("Falha na conexão segura (TLS) com a Sefaz: " + e.getMessage()
                    + " | Verifique se o certificado da empresa está válido (não vencido) e é um e-CNPJ A1.",
                    false, e);
        } catch (IOException e) {
            throw new SefazComunicacaoException("Erro de comunicação com a Sefaz: " + e.getMessage(), true, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SefazComunicacaoException("Envio interrompido.", true, e);
        }

        String protocolo = resp.sslSession().map(javax.net.ssl.SSLSession::getProtocol).orElse("?");
        ouvinte.recebido(servico, endereco, resp.statusCode(), "[" + protocolo + "]\n" + resp.body());

        // SOAP 1.2 devolve Fault com HTTP 500; tenta extrair a mensagem antes de falhar.
        if (resp.statusCode() != 200) {
            String corpo = resp.body() == null ? "" : resp.body();
            if (corpo.contains("Envelope")) {
                EnvelopeSoap.extrairRetorno(corpo); // lança com o texto do Fault
            }
            String dica = resp.statusCode() == 403
                    ? " O servidor recusou o certificado da empresa (vencido ou revogado, cadeia fora da"
                    + " ICP-Brasil, ou empresa sem acesso a este ambiente)."
                    : "";
            throw new SefazComunicacaoException("Sefaz respondeu HTTP " + resp.statusCode() + " (" + protocolo + ")."
                    + dica + resumo(corpo), resp.statusCode() >= 500, null);
        }
        return EnvelopeSoap.extrairRetorno(resp.body());
    }

    /** Texto curto do corpo de uma resposta de erro (normalmente HTML do IIS). */
    static String resumo(String corpo) {
        if (corpo == null || corpo.isBlank()) {
            return "";
        }
        String texto = corpo.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .strip();
        if (texto.isEmpty()) {
            return "";
        }
        return " Resposta: " + (texto.length() > 300 ? texto.substring(0, 300) + "..." : texto);
    }

    private static boolean servidorNaoConfiavel(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains(ContextoTls.MENSAGEM_SERVIDOR_NAO_CONFIAVEL)) {
                return true;
            }
        }
        return false;
    }

    /** Recebe cópia de toda mensagem enviada/recebida, para auditoria e depuração. */
    public interface OuvinteMensagens {
        OuvinteMensagens NENHUM = new OuvinteMensagens() {
        };

        default void enviando(ServicoSefaz servico, URI endereco, String envelope) {
        }

        default void recebido(ServicoSefaz servico, URI endereco, int statusHttp, String corpo) {
        }
    }

    public static final class Construtor {
        private final CertificadoA1 certificado;
        private KeyStore confiancaExtra;
        private Duration timeoutConexao = TIMEOUT_CONEXAO_PADRAO;
        private Duration timeoutResposta = TIMEOUT_RESPOSTA_PADRAO;
        private ProxySelector proxy = ProxySelector.getDefault();
        private String[] protocolosTls = {"TLSv1.2"};
        private OuvinteMensagens ouvinte = OuvinteMensagens.NENHUM;

        private Construtor(CertificadoA1 certificado) {
            this.certificado = certificado;
        }

        /** Truststore adicional (cadeia ICP-Brasil) para confiar no servidor da Sefaz. */
        public Construtor confiancaExtra(KeyStore truststore) {
            this.confiancaExtra = truststore;
            return this;
        }

        public Construtor timeoutConexao(Duration d) {
            this.timeoutConexao = d;
            return this;
        }

        public Construtor timeoutResposta(Duration d) {
            this.timeoutResposta = d;
            return this;
        }

        /** {@code null} = sem proxy. */
        public Construtor proxy(ProxySelector proxy) {
            this.proxy = proxy;
            return this;
        }

        /**
         * Versões de TLS permitidas. Padrão: só TLS 1.2. Os servidores da Sefaz RS não aceitam
         * TLS 1.3 (derrubam a conexão; conferido em 27/11/2026).
         */
        public Construtor protocolosTls(String... protocolos) {
            this.protocolosTls = protocolos.clone();
            return this;
        }

        public Construtor ouvinte(OuvinteMensagens ouvinte) {
            this.ouvinte = ouvinte;
            return this;
        }

        public ClienteSefaz construir() {
            SSLContext ctx = ContextoTls.criar(certificado, confiancaExtra);
            SSLParameters params = ctx.getDefaultSSLParameters();
            params.setProtocols(protocolosTls);
            HttpClient.Builder b = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .sslContext(ctx)
                    .sslParameters(params)
                    .connectTimeout(timeoutConexao)
                    .followRedirects(HttpClient.Redirect.NEVER);
            if (proxy != null) {
                b.proxy(proxy);
            }
            return new ClienteSefaz(b.build(), timeoutResposta, ouvinte);
        }
    }
}
