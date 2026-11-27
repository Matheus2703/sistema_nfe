package br.com.sistemanfe.dfe.certificado;

import br.com.sistemanfe.dfe.DfeException;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.asn1.ASN1TaggedObject;

import javax.security.auth.x500.X500Principal;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Certificado digital A1 (e-CNPJ ICP-Brasil, arquivo .pfx/.p12) já aberto em memória.
 *
 * <p>Usado para duas coisas: autenticar a conexão TLS com a Sefaz e assinar os XML.
 * A senha só é usada na abertura e não fica guardada neste objeto.
 */
public final class CertificadoA1 {

    /** OID ICP-Brasil do CNPJ da pessoa jurídica no SubjectAlternativeName (otherName). */
    static final String OID_CNPJ = "2.16.76.1.3.3";

    private static final Pattern CNPJ_NO_CN = Pattern.compile(":(\\d{14})\\b");

    private final X509Certificate certificado;
    private final PrivateKey chavePrivada;
    private final List<X509Certificate> cadeia;

    private CertificadoA1(X509Certificate certificado, PrivateKey chavePrivada, List<X509Certificate> cadeia) {
        this.certificado = certificado;
        this.chavePrivada = chavePrivada;
        this.cadeia = List.copyOf(cadeia);
    }

    public static CertificadoA1 carregar(Path arquivo, char[] senha) {
        try {
            return carregar(Files.readAllBytes(arquivo), senha);
        } catch (IOException e) {
            throw new DfeException("Não foi possível ler o arquivo do certificado: " + arquivo, e);
        }
    }

    public static CertificadoA1 carregar(byte[] conteudoPfx, char[] senha) {
        KeyStore ks;
        try {
            ks = KeyStore.getInstance("PKCS12");
            ks.load(new ByteArrayInputStream(conteudoPfx), senha);
        } catch (IOException e) {
            if (e.getCause() instanceof UnrecoverableKeyException) {
                throw new DfeException("Senha do certificado incorreta.", e);
            }
            throw new DfeException("Senha do certificado incorreta ou arquivo .pfx inválido.", e);
        } catch (GeneralSecurityException e) {
            throw new DfeException("Arquivo .pfx inválido.", e);
        }

        try {
            for (String alias : Collections.list(ks.aliases())) {
                if (!ks.isKeyEntry(alias)) {
                    continue;
                }
                Key chave = ks.getKey(alias, senha);
                Certificate[] cadeiaBruta = ks.getCertificateChain(alias);
                if (chave instanceof PrivateKey pk && cadeiaBruta != null && cadeiaBruta.length > 0) {
                    List<X509Certificate> cadeia = Arrays.stream(cadeiaBruta)
                            .map(X509Certificate.class::cast)
                            .toList();
                    return new CertificadoA1(cadeia.get(0), pk, cadeia);
                }
            }
        } catch (GeneralSecurityException e) {
            throw new DfeException("Não foi possível ler a chave privada do certificado.", e);
        }
        throw new DfeException("O arquivo .pfx não contém chave privada. Ele precisa ser um certificado A1.");
    }

    public X509Certificate certificado() {
        return certificado;
    }

    public PrivateKey chavePrivada() {
        return chavePrivada;
    }

    /** Cadeia completa, começando pelo certificado do titular. */
    public List<X509Certificate> cadeia() {
        return cadeia;
    }

    /** Nome do titular (CN), ex.: {@code EMPRESA EXEMPLO LTDA:12345678000199}. */
    public String nomeTitular() {
        String dn = certificado.getSubjectX500Principal().getName(X500Principal.RFC2253);
        for (String parte : dn.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")) {
            String p = parte.trim();
            if (p.regionMatches(true, 0, "CN=", 0, 3)) {
                return p.substring(3).replace("\\", "");
            }
        }
        return dn;
    }

    /**
     * CNPJ do titular. Lido do campo ICP-Brasil (OID 2.16.76.1.3.3) e, se ausente,
     * do final do CN ({@code NOME:CNPJ}).
     */
    public Optional<String> cnpj() {
        return cnpjDoSubjectAltName().or(() -> {
            Matcher m = CNPJ_NO_CN.matcher(nomeTitular());
            return m.find() ? Optional.of(m.group(1)) : Optional.empty();
        });
    }

    public Instant validoDesde() {
        return certificado.getNotBefore().toInstant();
    }

    public Instant validoAte() {
        return certificado.getNotAfter().toInstant();
    }

    public boolean vencido(Clock relogio) {
        Instant agora = relogio.instant();
        return agora.isAfter(validoAte()) || agora.isBefore(validoDesde());
    }

    /** Dias até o vencimento (negativo se já venceu). */
    public long diasParaVencer(Clock relogio) {
        return Duration.between(relogio.instant(), validoAte()).toDays();
    }

    private Optional<String> cnpjDoSubjectAltName() {
        Collection<List<?>> nomes;
        try {
            nomes = certificado.getSubjectAlternativeNames();
        } catch (CertificateParsingException e) {
            return Optional.empty();
        }
        if (nomes == null) {
            return Optional.empty();
        }
        for (List<?> nome : nomes) {
            // Tipo 0 = otherName; o JDK entrega o DER da estrutura OtherName.
            if (nome.size() >= 2 && Integer.valueOf(0).equals(nome.get(0)) && nome.get(1) instanceof byte[] der) {
                Optional<String> valor = lerOtherName(der, OID_CNPJ);
                if (valor.isPresent()) {
                    String digitos = valor.get().replaceAll("\\D", "");
                    if (digitos.length() == 14) {
                        return Optional.of(digitos);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> lerOtherName(byte[] der, String oidProcurado) {
        try {
            ASN1Primitive primitivo = ASN1Primitive.fromByteArray(der);
            // Alguns JDKs entregam o OtherName ainda envolvido na tag [0] do GeneralName.
            if (primitivo instanceof ASN1TaggedObject tagged) {
                primitivo = tagged.getBaseObject().toASN1Primitive();
            }
            ASN1Sequence seq = ASN1Sequence.getInstance(primitivo);
            ASN1ObjectIdentifier oid = ASN1ObjectIdentifier.getInstance(seq.getObjectAt(0));
            if (!oidProcurado.equals(oid.getId())) {
                return Optional.empty();
            }
            ASN1Encodable valor = seq.getObjectAt(1);
            while (valor instanceof ASN1TaggedObject t) {
                valor = t.getBaseObject();
            }
            if (valor instanceof ASN1String s) {
                return Optional.of(s.getString());
            }
            if (valor instanceof ASN1OctetString o) {
                return Optional.of(new String(o.getOctets(), StandardCharsets.ISO_8859_1));
            }
            return Optional.empty();
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }
}
