package br.com.sistemanfe.dfe.teste;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Gera certificados autoassinados para testes (nunca usar fora dos testes). */
public final class CertificadosDeTeste {

    public static final String OID_CNPJ = "2.16.76.1.3.3";
    private static final AtomicLong SERIAL = new AtomicLong(System.currentTimeMillis());

    private CertificadosDeTeste() {
    }

    public record Gerado(KeyPair chaves, X509Certificate certificado) {

        /** Exporta como .pfx (PKCS12) protegido pela senha informada. */
        public byte[] comoPfx(char[] senha) {
            try {
                KeyStore ks = KeyStore.getInstance("PKCS12");
                ks.load(null, null);
                ks.setKeyEntry("teste", chaves.getPrivate(), senha, new X509Certificate[]{certificado});
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ks.store(out, senha);
                return out.toByteArray();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        /** Truststore em memória contendo só este certificado. */
        public KeyStore comoTruststore() {
            try {
                KeyStore ks = KeyStore.getInstance("PKCS12");
                ks.load(null, null);
                ks.setCertificateEntry("confiavel", certificado);
                return ks;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** Certificado no estilo e-CNPJ ICP-Brasil, com o CNPJ no SubjectAlternativeName. */
    public static Gerado eCnpj(String nome, String cnpj, ASN1Encodable valorCnpj, int validadeDias) {
        List<GeneralName> nomes = new ArrayList<>();
        if (cnpj != null) {
            ASN1Encodable valor = valorCnpj != null ? valorCnpj : new org.bouncycastle.asn1.DEROctetString(cnpj.getBytes());
            nomes.add(new GeneralName(GeneralName.otherName, new DERSequence(new ASN1Encodable[]{
                    new ASN1ObjectIdentifier(OID_CNPJ),
                    new DERTaggedObject(true, 0, valor)
            })));
        }
        return gerar("CN=" + nome + ", OU=Teste, O=ICP-Brasil, C=BR", nomes, validadeDias);
    }

    /** Certificado de servidor para {@code localhost}. */
    public static Gerado servidorLocalhost() {
        return gerar("CN=localhost", List.of(new GeneralName(GeneralName.dNSName, "localhost")), 30);
    }

    private static Gerado gerar(String dn, List<GeneralName> nomesAlternativos, int validadeDias) {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();
            X500Name nome = new X500Name(dn);
            Instant agora = Instant.now();
            JcaX509v3CertificateBuilder b = new JcaX509v3CertificateBuilder(
                    nome, BigInteger.valueOf(SERIAL.incrementAndGet()),
                    Date.from(agora.minus(1, ChronoUnit.DAYS)),
                    Date.from(agora.plus(validadeDias, ChronoUnit.DAYS)),
                    nome, kp.getPublic());
            if (!nomesAlternativos.isEmpty()) {
                b.addExtension(Extension.subjectAlternativeName, false,
                        new GeneralNames(nomesAlternativos.toArray(new GeneralName[0])));
            }
            X509Certificate cert = new JcaX509CertificateConverter().getCertificate(
                    b.build(new JcaContentSignerBuilder("SHA256withRSA").build(kp.getPrivate())));
            return new Gerado(kp, cert);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
