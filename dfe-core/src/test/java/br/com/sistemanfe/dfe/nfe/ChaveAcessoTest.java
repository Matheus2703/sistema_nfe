package br.com.sistemanfe.dfe.nfe;

import br.com.sistemanfe.dfe.DfeException;
import br.com.sistemanfe.dfe.comum.FormaEmissao;
import br.com.sistemanfe.dfe.comum.ModeloDocumento;
import br.com.sistemanfe.dfe.comum.UF;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.YearMonth;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Chave de acesso da NF-e/NFC-e (MOC, item "Chave de Acesso"; NT Conjunta 2025.001 para o CNPJ alfanumérico).
 *
 * <p>Os CNPJ dos testes são fictícios: {@code 11222333000181} (exemplo clássico de documentação, já usado
 * nos testes do certificado) e {@code 12ABC34501DE35} (exemplo de CNPJ alfanumérico da Receita Federal,
 * reproduzido na NT Conjunta 2025.001).
 */
class ChaveAcessoTest {

    private static final String CNPJ = "11222333000181";
    private static final String CNPJ_ALFA = "12ABC34501DE35";

    /*
     * 43 primeiros dígitos: 43 2611 11222333000181 55 001 000000123 1 52817465.
     * Pesos 2..9 da direita para a esquerda: soma 532, resto 532 % 11 = 4, DV = 11 - 4 = 7.
     */
    private static final String CHAVE_NUMERICA = "43261111222333000181550010000001231528174657";

    /*
     * 43 2612 12ABC34501DE35 65 001 000000001 1 30481726, letras pelo ASCII - 48 (A=17, B=18, C=19, D=20, E=21):
     * soma 820, resto 6, DV 5. Com A=10, B=11... (Character.getNumericValue) o DV daria 7.
     */
    private static final String CHAVE_ALFANUMERICA = "43261212ABC34501DE35650010000000011304817265";

    @Test
    void montaOs44DigitosNaOrdemDoLeiauteComDigitoPeloModulo11() {
        ChaveAcesso chave = ChaveAcesso.gerar(UF.RS, YearMonth.of(2026, 11), CNPJ, ModeloDocumento.NFE,
                1, 123, FormaEmissao.NORMAL, "52817465");

        assertThat(chave.valor()).isEqualTo(CHAVE_NUMERICA);
        assertThat(chave.digitoVerificador()).isEqualTo(7);
        assertThat(chave).hasToString(CHAVE_NUMERICA);
    }

    @Test
    void letrasDoCnpjAlfanumericoEntramNoDigitoPeloAsciiMenos48() {
        ChaveAcesso chave = ChaveAcesso.gerar(UF.RS, YearMonth.of(2026, 12), CNPJ_ALFA, ModeloDocumento.NFCE,
                1, 1, FormaEmissao.NORMAL, "30481726");

        assertThat(chave.valor()).isEqualTo(CHAVE_ALFANUMERICA);
        assertThat(chave.digitoVerificador()).isEqualTo(5);
    }

    @Test
    void cadaLetraValeOCodigoAsciiMenos48() {
        // NT Conjunta 2025.001: '0'..'9' = 0..9, 'A' = 17, 'Z' = 42.
        // "000...0A" (A=17, peso 2): soma 34, resto 1, DV 0. "000...0Z" (Z=42, peso 2): soma 84, resto 7, DV 4.
        assertThat(ChaveAcesso.calcularDigito("0".repeat(42) + "A")).isZero();
        assertThat(ChaveAcesso.calcularDigito("0".repeat(42) + "Z")).isEqualTo(4);
    }

    @Test
    void digitoEhZeroQuandoORestoForZero() {
        // soma 341 = 11 x 31
        assertThat(ChaveAcesso.calcularDigito("4326111122233300018155001000000123110000001")).isZero();
    }

    @Test
    void digitoEhZeroQuandoORestoForUm() {
        // soma 353 = 11 x 32 + 1; 11 - 1 = 10 não cabe em um dígito
        assertThat(ChaveAcesso.calcularDigito("4326111122233300018155001000000123110000007")).isZero();
    }

    @Test
    void idDaInfNFeEhNFeMaisAChave() {
        assertThat(ChaveAcesso.de(CHAVE_NUMERICA).idInfNFe()).isEqualTo("NFe" + CHAVE_NUMERICA);
    }

    @Test
    void decompoeChaveNosCampos() {
        ChaveAcesso chave = ChaveAcesso.de(CHAVE_ALFANUMERICA);

        assertThat(chave.uf()).isEqualTo(UF.RS);
        assertThat(chave.anoMesEmissao()).isEqualTo(YearMonth.of(2026, 12));
        assertThat(chave.cnpjEmitente()).isEqualTo(CNPJ_ALFA);
        assertThat(chave.modelo()).isEqualTo(ModeloDocumento.NFCE);
        assertThat(chave.serie()).isEqualTo(1);
        assertThat(chave.numero()).isEqualTo(1);
        assertThat(chave.formaEmissao()).isEqualTo(FormaEmissao.NORMAL);
        assertThat(chave.codigoNumerico()).isEqualTo("30481726");
        assertThat(chave.digitoVerificador()).isEqualTo(5);
    }

    @Test
    void decompoeContingenciaOfflineDaNfce() {
        String semDigito = "43" + "2611" + CNPJ + "65" + "999" + "123456789" + "9" + "30481726";
        String chave = semDigito + ChaveAcesso.calcularDigito(semDigito);

        ChaveAcesso decomposta = ChaveAcesso.de(chave);

        assertThat(decomposta.serie()).isEqualTo(999);
        assertThat(decomposta.numero()).isEqualTo(123_456_789);
        assertThat(decomposta.formaEmissao()).isEqualTo(FormaEmissao.CONTINGENCIA_OFFLINE_NFCE);
    }

    @Test
    void chaveGeradaEDecompostaSaoIguais() {
        ChaveAcesso gerada = ChaveAcesso.gerar(UF.RS, YearMonth.of(2026, 11), CNPJ, ModeloDocumento.NFE,
                1, 123, FormaEmissao.NORMAL, "52817465");

        assertThat(ChaveAcesso.de(gerada.valor())).isEqualTo(gerada).hasSameHashCodeAs(gerada);
    }

    @Test
    void validaChaveExistente() {
        assertThat(ChaveAcesso.valida(CHAVE_NUMERICA)).isTrue();
        assertThat(ChaveAcesso.valida(CHAVE_ALFANUMERICA)).isTrue();
        assertThat(ChaveAcesso.valida("43261111222333000181550010000001231528174650")).isFalse();
        assertThat(ChaveAcesso.valida("4326111122233300018155001000000123152817465")).isFalse();
        assertThat(ChaveAcesso.valida(null)).isFalse();
    }

    @Test
    void recusaChaveComTamanhoErrado() {
        assertThatThrownBy(() -> ChaveAcesso.de(CHAVE_NUMERICA + "1"))
                .isInstanceOf(DfeException.class)
                .hasMessageContaining("44")
                .hasMessageContaining("45");
    }

    @Test
    void recusaChaveComDigitoErrado() {
        assertThatThrownBy(() -> ChaveAcesso.de("43261111222333000181550010000001231528174650"))
                .isInstanceOf(DfeException.class)
                .hasMessageContaining("Dígito verificador")
                .hasMessageContaining("informado 0, calculado 7");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "4326111122233300018155001000000123152817465A", // letra fora do CNPJ
            "A3261111222333000181550010000001231528174657", // letra na UF
            "43261212abc34501DE35650010000000011304817265", // letra minúscula
            "432611112223330001815500100000012315281746 7", // espaço
    })
    void recusaCaracteresForaDoPadrao(String chave) {
        assertThatThrownBy(() -> ChaveAcesso.de(chave))
                .isInstanceOf(DfeException.class)
                .hasMessageContaining("caracteres inválidos");
    }

    @Test
    void recusaCodigoDeUfInexistente() {
        assertThatThrownBy(() -> ChaveAcesso.de(comDigito("99" + CHAVE_NUMERICA.substring(2, 43))))
                .isInstanceOf(DfeException.class)
                .hasMessageContaining("UF")
                .hasMessageContaining("99");
    }

    @Test
    void recusaMesDeEmissaoInvalido() {
        assertThatThrownBy(() -> ChaveAcesso.de(comDigito("432613" + CHAVE_NUMERICA.substring(6, 43))))
                .isInstanceOf(DfeException.class)
                .hasMessageContaining("mês")
                .hasMessageContaining("13");
    }

    @Test
    void recusaModeloQueNaoEhNfeNemNfce() {
        // 57 = CT-e
        String semDigito = CHAVE_NUMERICA.substring(0, 20) + "57" + CHAVE_NUMERICA.substring(22, 43);
        assertThatThrownBy(() -> ChaveAcesso.de(comDigito(semDigito)))
                .isInstanceOf(DfeException.class)
                .hasMessageContaining("modelo")
                .hasMessageContaining("57");
    }

    @Test
    void recusaFormaDeEmissaoInexistente() {
        String semDigito = CHAVE_NUMERICA.substring(0, 34) + "8" + CHAVE_NUMERICA.substring(35, 43);
        assertThatThrownBy(() -> ChaveAcesso.de(comDigito(semDigito)))
                .isInstanceOf(DfeException.class)
                .hasMessageContaining("forma de emissão")
                .hasMessageContaining("8");
    }

    @Test
    void recusaNumeroZero() {
        String semDigito = CHAVE_NUMERICA.substring(0, 25) + "000000000" + CHAVE_NUMERICA.substring(34, 43);
        assertThatThrownBy(() -> ChaveAcesso.de(comDigito(semDigito)))
                .isInstanceOf(DfeException.class)
                .hasMessageContaining("número");
    }

    @Test
    void geraCodigoNumericoAleatorioDe8Digitos() {
        ChaveAcesso chave = ChaveAcesso.gerar(UF.RS, YearMonth.of(2026, 11), CNPJ, ModeloDocumento.NFE,
                1, 123, FormaEmissao.NORMAL);

        assertThat(chave.codigoNumerico()).matches("[0-9]{8}");
        assertThat(ChaveAcesso.valida(chave.valor())).isTrue();
    }

    @Test
    void codigoNumericoAleatorioPulaSequenciasVetadasEONumeroDaNota() {
        // Regra B03-10 da NT 2019.001 (rejeição 897): o sorteio é refeito até sair um código permitido.
        var aleatorio = new SequenciaFixa(12_345_678, 0, 99_999_999, 123, 1_234_567, 52_817_465);

        assertThat(ChaveAcesso.novoCodigoNumerico(123, aleatorio)).isEqualTo("52817465");
        assertThat(aleatorio.sorteiosRestantes()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "00000000", "11111111", "22222222", "33333333", "44444444", "55555555", "66666666", "77777777",
            "88888888", "99999999", "12345678", "23456789", "34567890", "45678901", "56789012", "67890123",
            "78901234", "89012345", "90123456", "01234567"})
    void recusaCodigoNumericoVetadoPelaNt2019001(String codigo) {
        assertThatThrownBy(() -> ChaveAcesso.gerar(UF.RS, YearMonth.of(2026, 11), CNPJ, ModeloDocumento.NFE,
                1, 123, FormaEmissao.NORMAL, codigo))
                .isInstanceOf(DfeException.class)
                .hasMessageContaining("897");
    }

    @Test
    void recusaCodigoNumericoIgualAoNumeroDaNota() {
        assertThatThrownBy(() -> ChaveAcesso.gerar(UF.RS, YearMonth.of(2026, 11), CNPJ, ModeloDocumento.NFE,
                1, 123, FormaEmissao.NORMAL, "00000123"))
                .isInstanceOf(DfeException.class)
                .hasMessageContaining("897")
                .hasMessageContaining("número da nota");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567", "123456789", "1234567A", "-1234567"})
    void recusaCodigoNumericoForaDoFormato(String codigo) {
        assertThatThrownBy(() -> ChaveAcesso.gerar(UF.RS, YearMonth.of(2026, 11), CNPJ, ModeloDocumento.NFE,
                1, 123, FormaEmissao.NORMAL, codigo))
                .isInstanceOf(DfeException.class)
                .hasMessageContaining("8 dígitos");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1122233300018", "112223330001811", "11.222.333/0001-81", "12abc34501de35",
            "12ABC34501DE3A"})
    void recusaCnpjForaDoFormato(String cnpj) {
        assertThatThrownBy(() -> ChaveAcesso.gerar(UF.RS, YearMonth.of(2026, 11), cnpj, ModeloDocumento.NFE,
                1, 123, FormaEmissao.NORMAL, "52817465"))
                .isInstanceOf(DfeException.class)
                .hasMessageContaining("CNPJ");
    }

    @Test
    void recusaSerieENumeroForaDoIntervalo() {
        assertThatThrownBy(() -> gerarComSerieENumero(1000, 1)).isInstanceOf(DfeException.class)
                .hasMessageContaining("Série");
        assertThatThrownBy(() -> gerarComSerieENumero(-1, 1)).isInstanceOf(DfeException.class)
                .hasMessageContaining("Série");
        assertThatThrownBy(() -> gerarComSerieENumero(1, 0)).isInstanceOf(DfeException.class)
                .hasMessageContaining("Número");
        assertThatThrownBy(() -> gerarComSerieENumero(1, 1_000_000_000)).isInstanceOf(DfeException.class)
                .hasMessageContaining("Número");
    }

    @Test
    void aceitaSerieZeroENumeroMaximo() {
        ChaveAcesso chave = gerarComSerieENumero(0, 999_999_999);

        assertThat(chave.valor().substring(22, 34)).isEqualTo("000999999999");
    }

    @Test
    void recusaAnoQueNaoCabeEmDoisDigitos() {
        assertThatThrownBy(() -> ChaveAcesso.gerar(UF.RS, YearMonth.of(2100, 1), CNPJ, ModeloDocumento.NFE,
                1, 123, FormaEmissao.NORMAL, "52817465"))
                .isInstanceOf(DfeException.class)
                .hasMessageContaining("2100");
    }

    private static ChaveAcesso gerarComSerieENumero(int serie, int numero) {
        return ChaveAcesso.gerar(UF.RS, YearMonth.of(2026, 11), CNPJ, ModeloDocumento.NFE,
                serie, numero, FormaEmissao.NORMAL, "52817465");
    }

    private static String comDigito(String semDigito) {
        return semDigito + ChaveAcesso.calcularDigito(semDigito);
    }

    /** Gerador que devolve os valores dados, na ordem, para testar o novo sorteio do cNF. */
    private static final class SequenciaFixa implements RandomGenerator {
        private final Deque<Integer> valores;

        SequenciaFixa(Integer... valores) {
            this.valores = new ArrayDeque<>(List.of(valores));
        }

        int sorteiosRestantes() {
            return valores.size();
        }

        @Override
        public int nextInt(int limite) {
            int valor = valores.removeFirst();
            assertThat(valor).isBetween(0, limite - 1);
            return valor;
        }

        @Override
        public long nextLong() {
            throw new UnsupportedOperationException("use nextInt(limite)");
        }
    }
}
