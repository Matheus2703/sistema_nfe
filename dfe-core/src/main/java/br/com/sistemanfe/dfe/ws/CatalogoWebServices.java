package br.com.sistemanfe.dfe.ws;

import br.com.sistemanfe.dfe.DfeException;
import br.com.sistemanfe.dfe.comum.Ambiente;
import br.com.sistemanfe.dfe.comum.ModeloDocumento;
import br.com.sistemanfe.dfe.comum.UF;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Resolve o endereço de cada web service por UF, modelo e ambiente.
 *
 * <p>Por enquanto só o RS está cadastrado. Para atender outra UF, basta incluir os
 * endereços no {@code webservices.properties} (e, para UFs atendidas pela SVRS/SVAN,
 * mapear a UF para o autorizador quando essa necessidade surgir).
 */
public final class CatalogoWebServices {

    private static final String RECURSO = "/sefaz/webservices.properties";

    private final Map<String, URI> enderecos;
    private final URI enderecoFixo;

    private CatalogoWebServices(Map<String, URI> enderecos, URI enderecoFixo) {
        this.enderecos = Map.copyOf(enderecos);
        this.enderecoFixo = enderecoFixo;
    }

    /** Catálogo oficial, lido de {@code /sefaz/webservices.properties}. */
    public static CatalogoWebServices padrao() {
        Properties p = new Properties();
        try (InputStream in = CatalogoWebServices.class.getResourceAsStream(RECURSO)) {
            if (in == null) {
                throw new DfeException("Arquivo de endereços não encontrado: " + RECURSO);
            }
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                p.load(r);
            }
        } catch (IOException e) {
            throw new DfeException("Erro ao ler " + RECURSO, e);
        }
        Map<String, URI> mapa = new HashMap<>();
        p.stringPropertyNames().forEach(k -> mapa.put(k, URI.create(p.getProperty(k).trim())));
        return new CatalogoWebServices(mapa, null);
    }

    /** Catálogo que manda todos os serviços para um único endereço (testes com servidor falso). */
    public static CatalogoWebServices fixo(URI endereco) {
        return new CatalogoWebServices(Map.of(), endereco);
    }

    public URI endereco(UF uf, ModeloDocumento modelo, Ambiente ambiente, ServicoSefaz servico) {
        if (enderecoFixo != null) {
            return enderecoFixo;
        }
        String chave = uf.name() + "." + modelo.codigo() + "." + ambiente.codigo() + "." + servico.chave();
        URI uri = enderecos.get(chave);
        if (uri == null) {
            throw new DfeException("Web service não cadastrado: " + servico + " / " + modelo + " / " + ambiente
                    + " / " + uf + " (chave " + chave + ").");
        }
        return uri;
    }
}
