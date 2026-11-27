package br.com.sistemanfe.dfe.certificado;

import br.com.sistemanfe.dfe.DfeException;
import br.com.sistemanfe.dfe.teste.CertificadosDeTeste;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TruststoreIcpBrasilTest {

    private final X509Certificate raiz = CertificadosDeTeste.eCnpj("AC RAIZ TESTE", null, null, 365).certificado();
    private final X509Certificate intermediaria = CertificadosDeTeste.eCnpj("AC INTERMEDIARIA", null, null, 365).certificado();

    @Test
    void leZipComDerPemZipInternoEIgnoraDuplicados(@TempDir Path dir) throws Exception {
        byte[] zipInterno = zip(new String[]{"interna.cer"}, new byte[][]{intermediaria.getEncoded()});
        Path zip = dir.resolve("ACcompactado.zip");
        Files.write(zip, zip(
                new String[]{"raiz.crt", "raiz-copia.pem", "leia-me.txt", "mais.zip"},
                new byte[][]{raiz.getEncoded(), pem(raiz), "texto".getBytes(), zipInterno}));

        List<X509Certificate> certs = TruststoreIcpBrasil.lerZip(zip);

        assertThat(certs).containsExactlyInAnyOrder(raiz, intermediaria);
    }

    @Test
    void gravaERelePkcs12(@TempDir Path dir) throws Exception {
        Path destino = dir.resolve("icpbrasil.p12");
        TruststoreIcpBrasil.gravar(TruststoreIcpBrasil.criar(List.of(raiz, intermediaria)), destino, "changeit".toCharArray());

        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (var in = Files.newInputStream(destino)) {
            ks.load(in, "changeit".toCharArray());
        }
        assertThat(ks.size()).isEqualTo(2);
    }

    @Test
    void leDePasta(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve("a.crt"), raiz.getEncoded());
        Files.write(dir.resolve("b.pem"), pem(intermediaria));

        assertThat(TruststoreIcpBrasil.lerPasta(dir)).hasSize(2);
    }

    @Test
    void zipSemCertificadoGeraErroClaro(@TempDir Path dir) throws Exception {
        Path zip = dir.resolve("vazio.zip");
        Files.write(zip, zip(new String[]{"x.txt"}, new byte[][]{"nada".getBytes()}));

        assertThatThrownBy(() -> TruststoreIcpBrasil.lerZip(zip))
                .isInstanceOf(DfeException.class)
                .hasMessageContaining("Nenhum certificado");
    }

    private static byte[] pem(X509Certificate c) throws Exception {
        return ("-----BEGIN CERTIFICATE-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(c.getEncoded())
                + "\n-----END CERTIFICATE-----\n").getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] zip(String[] nomes, byte[][] conteudos) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            for (int i = 0; i < nomes.length; i++) {
                z.putNextEntry(new ZipEntry(nomes[i]));
                z.write(conteudos[i]);
                z.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
