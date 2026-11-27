package br.com.sistemanfe.dfe.xml;

import br.com.sistemanfe.dfe.DfeException;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Valida XML contra os schemas oficiais empacotados em {@code /schemas/<pacote>/}.
 *
 * <p>Toda mensagem deve passar por aqui antes de ir para a Sefaz: um erro de schema
 * detectado localmente é muito mais claro que a rejeição 225 ("Falha no Schema XML").
 */
public final class ValidadorXsd {

    /** Pacote de schemas em uso (Nota Técnica vigente). */
    public static final String PACOTE_PADRAO = "PL_010h";

    private static final Map<String, Schema> CACHE = new ConcurrentHashMap<>();

    private final Schema schema;
    private final String nomeArquivo;

    private ValidadorXsd(Schema schema, String nomeArquivo) {
        this.schema = schema;
        this.nomeArquivo = nomeArquivo;
    }

    /** Validador para um arquivo do pacote padrão, ex.: {@code consStatServ_v4.00.xsd}. */
    public static ValidadorXsd para(String arquivoXsd) {
        return para(PACOTE_PADRAO, arquivoXsd);
    }

    public static ValidadorXsd para(String pacote, String arquivoXsd) {
        String caminho = "/schemas/" + pacote + "/" + arquivoXsd;
        Schema s = CACHE.computeIfAbsent(caminho, ValidadorXsd::carregar);
        return new ValidadorXsd(s, arquivoXsd);
    }

    /** Lista de erros encontrados; vazia quando o XML é válido. */
    public List<String> validar(String xml) {
        List<String> erros = new ArrayList<>();
        try {
            Validator v = schema.newValidator();
            v.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            v.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            v.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException e) {
                    // avisos não bloqueiam
                }

                @Override
                public void error(SAXParseException e) {
                    erros.add(e.getMessage());
                }

                @Override
                public void fatalError(SAXParseException e) {
                    erros.add(e.getMessage());
                }
            });
            v.validate(new StreamSource(new StringReader(xml)));
        } catch (SAXException e) {
            if (erros.isEmpty()) {
                erros.add(e.getMessage());
            }
        } catch (IOException e) {
            throw new DfeException("Erro ao validar XML.", e);
        }
        return erros;
    }

    /** Lança {@link DfeException} com todos os erros se o XML não for válido. */
    public void validarOuFalhar(String xml) {
        List<String> erros = validar(xml);
        if (!erros.isEmpty()) {
            throw new DfeException("XML não passou no schema " + nomeArquivo + ":\n - " + String.join("\n - ", erros));
        }
    }

    private static Schema carregar(String caminho) {
        URL url = ValidadorXsd.class.getResource(caminho);
        if (url == null) {
            throw new DfeException("Schema não encontrado no classpath: " + caminho);
        }
        try {
            SchemaFactory f = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            f.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            // Só permite includes/imports locais (dentro do próprio jar ou da pasta).
            f.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file,jar");
            return f.newSchema(new StreamSource(url.openStream(), url.toExternalForm()));
        } catch (SAXException | IOException e) {
            throw new DfeException("Erro ao carregar schema " + caminho + ": " + e.getMessage(), e);
        }
    }
}
