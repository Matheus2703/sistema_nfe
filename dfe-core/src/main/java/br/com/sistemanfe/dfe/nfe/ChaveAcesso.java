package br.com.sistemanfe.dfe.nfe;

import br.com.sistemanfe.dfe.DfeException;
import br.com.sistemanfe.dfe.comum.FormaEmissao;
import br.com.sistemanfe.dfe.comum.ModeloDocumento;
import br.com.sistemanfe.dfe.comum.UF;

import java.security.SecureRandom;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.random.RandomGenerator;

/**
 * Chave de acesso da NF-e (modelo 55) e da NFC-e (modelo 65): 44 caracteres.
 *
 * <pre>
 * cUF(2) + AAMM(4) + CNPJ(14) + mod(2) + serie(3) + nNF(9) + tpEmis(1) + cNF(8) + cDV(1)
 * </pre>
 *
 * <p>O dígito verificador segue o MOC (módulo 11, pesos de 2 a 9 da direita para a esquerda; resto 0 ou 1
 * dá dígito 0) com a regra da NT Conjunta 2025.001 para o CNPJ alfanumérico: cada caractere vale o seu código
 * ASCII menos 48, então os números valem o próprio algarismo e as letras valem de 17 ({@code A}) a 42
 * ({@code Z}). Só as 12 primeiras posições do CNPJ podem ter letras.
 *
 * <p>Para emitir, use {@link #gerar}; para ler uma chave existente, {@link #de(String)}.
 */
public final class ChaveAcesso {

    public static final int TAMANHO = 44;

    private static final Pattern FORMATO = Pattern.compile("[0-9]{6}[0-9A-Z]{12}[0-9]{26}");
    private static final Pattern FORMATO_CNPJ = Pattern.compile("[0-9A-Z]{12}[0-9]{2}");
    private static final Pattern FORMATO_CODIGO_NUMERICO = Pattern.compile("[0-9]{8}");

    /** Códigos numéricos vetados pela regra B03-10 da NT 2019.001 (rejeição 897). */
    private static final Set<String> CODIGOS_NUMERICOS_VETADOS = Set.of(
            "00000000", "11111111", "22222222", "33333333", "44444444",
            "55555555", "66666666", "77777777", "88888888", "99999999",
            "12345678", "23456789", "34567890", "45678901", "56789012",
            "67890123", "78901234", "89012345", "90123456", "01234567");

    private static final int LIMITE_CODIGO_NUMERICO = 100_000_000;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final String valor;
    private final UF uf;
    private final YearMonth anoMesEmissao;
    private final String cnpjEmitente;
    private final ModeloDocumento modelo;
    private final int serie;
    private final int numero;
    private final FormaEmissao formaEmissao;
    private final String codigoNumerico;

    private ChaveAcesso(String valor, UF uf, YearMonth anoMesEmissao, String cnpjEmitente, ModeloDocumento modelo,
                        int serie, int numero, FormaEmissao formaEmissao, String codigoNumerico) {
        this.valor = valor;
        this.uf = uf;
        this.anoMesEmissao = anoMesEmissao;
        this.cnpjEmitente = cnpjEmitente;
        this.modelo = modelo;
        this.serie = serie;
        this.numero = numero;
        this.formaEmissao = formaEmissao;
        this.codigoNumerico = codigoNumerico;
    }

    /**
     * Gera a chave de uma nota nova, com código numérico ({@code cNF}) sorteado por {@link SecureRandom}.
     *
     * @param anoMesEmissao ano e mês da data de emissão ({@code dhEmi}), por exemplo {@code YearMonth.from(dhEmi)}
     */
    public static ChaveAcesso gerar(UF uf, YearMonth anoMesEmissao, String cnpjEmitente, ModeloDocumento modelo,
                                    int serie, int numero, FormaEmissao formaEmissao) {
        validarNumero(numero);
        return gerar(uf, anoMesEmissao, cnpjEmitente, modelo, serie, numero, formaEmissao,
                novoCodigoNumerico(numero, ALEATORIO));
    }

    /**
     * Gera a chave com um código numérico ({@code cNF}) já escolhido, por exemplo para refazer a chave de uma
     * nota já numerada. O código precisa obedecer à regra da rejeição 897.
     */
    public static ChaveAcesso gerar(UF uf, YearMonth anoMesEmissao, String cnpjEmitente, ModeloDocumento modelo,
                                    int serie, int numero, FormaEmissao formaEmissao, String codigoNumerico) {
        Objects.requireNonNull(uf, "uf");
        Objects.requireNonNull(anoMesEmissao, "anoMesEmissao");
        Objects.requireNonNull(modelo, "modelo");
        Objects.requireNonNull(formaEmissao, "formaEmissao");
        if (anoMesEmissao.getYear() < 2000 || anoMesEmissao.getYear() > 2099) {
            throw new DfeException("Ano de emissão fora do que a chave de acesso representa (2000 a 2099): "
                    + anoMesEmissao.getYear() + ".");
        }
        if (cnpjEmitente == null || !FORMATO_CNPJ.matcher(cnpjEmitente).matches()) {
            throw new DfeException("CNPJ do emitente inválido para a chave de acesso: " + cnpjEmitente
                    + ". Informe os 14 caracteres sem pontuação (letras maiúsculas só nas 12 primeiras posições).");
        }
        if (serie < 0 || serie > 999) {
            throw new DfeException("Série fora do intervalo de 0 a 999: " + serie + ".");
        }
        validarNumero(numero);
        validarCodigoNumerico(codigoNumerico, numero);

        String semDigito = uf.codigoIbge()
                + String.format(Locale.ROOT, "%02d%02d", anoMesEmissao.getYear() % 100, anoMesEmissao.getMonthValue())
                + cnpjEmitente
                + modelo.codigo()
                + String.format(Locale.ROOT, "%03d%09d", serie, numero)
                + formaEmissao.codigo()
                + codigoNumerico;
        String valor = semDigito + calcularDigito(semDigito);
        return new ChaveAcesso(valor, uf, anoMesEmissao, cnpjEmitente, modelo, serie, numero, formaEmissao,
                codigoNumerico);
    }

    /**
     * Lê uma chave existente: confere o tamanho, os caracteres e o dígito verificador e decompõe os campos.
     * Não aplica a regra do código numérico, que vale só para notas novas.
     *
     * @throws DfeException se a chave não for de uma NF-e ou NFC-e válida
     */
    public static ChaveAcesso de(String chave) {
        if (chave == null) {
            throw new DfeException("Chave de acesso não informada.");
        }
        if (chave.length() != TAMANHO) {
            throw new DfeException("A chave de acesso deve ter " + TAMANHO + " caracteres; a informada tem "
                    + chave.length() + ".");
        }
        if (!FORMATO.matcher(chave).matches()) {
            throw new DfeException("Chave de acesso com caracteres inválidos: só números, e letras maiúsculas "
                    + "nas posições do CNPJ (7 a 18).");
        }
        int informado = chave.charAt(TAMANHO - 1) - '0';
        int calculado = calcularDigito(chave.substring(0, TAMANHO - 1));
        if (informado != calculado) {
            throw new DfeException("Dígito verificador da chave de acesso inválido: informado " + informado
                    + ", calculado " + calculado + ".");
        }

        UF uf = campo(chave.substring(0, 2), "Chave de acesso com código de UF inexistente: ", UF::porCodigoIbge);
        int mes = Integer.parseInt(chave.substring(4, 6));
        if (mes < 1 || mes > 12) {
            throw new DfeException("Chave de acesso com mês de emissão inválido: " + chave.substring(4, 6) + ".");
        }
        YearMonth anoMes = YearMonth.of(2000 + Integer.parseInt(chave.substring(2, 4)), mes);
        ModeloDocumento modelo = campo(chave.substring(20, 22),
                "A chave de acesso não é de NF-e (55) nem de NFC-e (65): modelo ", ModeloDocumento::porCodigo);
        int serie = Integer.parseInt(chave.substring(22, 25));
        int numero = Integer.parseInt(chave.substring(25, 34));
        if (numero == 0) {
            throw new DfeException("Chave de acesso com número da nota zero.");
        }
        FormaEmissao formaEmissao = campo(chave.substring(34, 35),
                "Chave de acesso com forma de emissão inexistente: ", FormaEmissao::porCodigo);

        return new ChaveAcesso(chave, uf, anoMes, chave.substring(6, 20), modelo, serie, numero, formaEmissao,
                chave.substring(35, 43));
    }

    /** {@code true} se {@link #de(String)} aceita a chave. */
    public static boolean valida(String chave) {
        try {
            de(chave);
            return true;
        } catch (DfeException e) {
            return false;
        }
    }

    /** Dígito verificador dos 43 primeiros caracteres da chave. */
    static int calcularDigito(String semDigito) {
        int soma = 0;
        int peso = 2;
        for (int i = semDigito.length() - 1; i >= 0; i--) {
            soma += (semDigito.charAt(i) - '0') * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    /** Sorteia um código numérico de 8 dígitos que não cai na rejeição 897. */
    static String novoCodigoNumerico(int numero, RandomGenerator aleatorio) {
        while (true) {
            String codigo = String.format(Locale.ROOT, "%08d", aleatorio.nextInt(LIMITE_CODIGO_NUMERICO));
            if (codigoNumericoPermitido(codigo, numero)) {
                return codigo;
            }
        }
    }

    private static void validarNumero(int numero) {
        if (numero < 1 || numero > 999_999_999) {
            throw new DfeException("Número da nota fora do intervalo de 1 a 999999999: " + numero + ".");
        }
    }

    private static void validarCodigoNumerico(String codigo, int numero) {
        if (codigo == null || !FORMATO_CODIGO_NUMERICO.matcher(codigo).matches()) {
            throw new DfeException("O código numérico (cNF) deve ter 8 dígitos: " + codigo + ".");
        }
        if (CODIGOS_NUMERICOS_VETADOS.contains(codigo)) {
            throw new DfeException("Código numérico (cNF) " + codigo + " vetado pela Sefaz (rejeição 897): "
                    + "sequência fácil de adivinhar.");
        }
        if (Integer.parseInt(codigo) == numero) {
            throw new DfeException("Código numérico (cNF) " + codigo + " igual ao número da nota "
                    + "(rejeição 897).");
        }
    }

    private static boolean codigoNumericoPermitido(String codigo, int numero) {
        return !CODIGOS_NUMERICOS_VETADOS.contains(codigo) && Integer.parseInt(codigo) != numero;
    }

    private static <T> T campo(String codigo, String mensagem, Function<String, T> conversor) {
        try {
            return conversor.apply(codigo);
        } catch (IllegalArgumentException e) {
            throw new DfeException(mensagem + codigo + ".", e);
        }
    }

    /** Os 44 caracteres da chave. */
    public String valor() {
        return valor;
    }

    /** Valor do atributo {@code Id} da {@code infNFe}: {@code "NFe"} seguido da chave. */
    public String idInfNFe() {
        return "NFe" + valor;
    }

    public UF uf() {
        return uf;
    }

    public YearMonth anoMesEmissao() {
        return anoMesEmissao;
    }

    public String cnpjEmitente() {
        return cnpjEmitente;
    }

    public ModeloDocumento modelo() {
        return modelo;
    }

    public int serie() {
        return serie;
    }

    public int numero() {
        return numero;
    }

    public FormaEmissao formaEmissao() {
        return formaEmissao;
    }

    /** Campo {@code cNF}. */
    public String codigoNumerico() {
        return codigoNumerico;
    }

    /** Campo {@code cDV}. */
    public int digitoVerificador() {
        return valor.charAt(TAMANHO - 1) - '0';
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ChaveAcesso outra && valor.equals(outra.valor);
    }

    @Override
    public int hashCode() {
        return valor.hashCode();
    }

    @Override
    public String toString() {
        return valor;
    }
}
