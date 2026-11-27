# sistema_nfe

Emissor próprio de **NF-e (55)** e **NFC-e (65)** para a **Sefaz RS**, multiempresa, em Java.
Começa em **homologação**. Nenhuma biblioteca fiscal pronta: o XML é gerado a partir dos
schemas oficiais, assinado e enviado direto aos web services da Sefaz.

- Plano completo e fases: [docs/PLANO.md](docs/PLANO.md)
- Decisão de linguagem e arquitetura: [docs/ADR-001-linguagem-e-arquitetura.md](docs/ADR-001-linguagem-e-arquitetura.md)

## Situação atual: fase 0 (fundação)

| Item | Status |
|---|---|
| Projeto Maven multi-módulo (Java 21) | ✅ |
| Classes Java geradas dos XSD oficiais (pacote PL_010h, já com IBS/CBS) | ✅ |
| Leitura do certificado A1 (.pfx) com CNPJ, titular e validade | ✅ |
| TLS mútuo com certificado por empresa + truststore ICP-Brasil opcional | ✅ |
| Envelope SOAP 1.2 e leitura do retorno / SOAP Fault | ✅ |
| Validação XSD antes de enviar | ✅ |
| Endereços dos web services do RS (NF-e e NFC-e, homologação e produção) | ✅ |
| Consulta de status do serviço (`NFeStatusServico4`) | ✅ testado com Sefaz falsa local |
| **Status 107 na homologação real da Sefaz RS** | ⏳ rodar na sua máquina (abaixo) |

## Estrutura

```
sistema_nfe/
├── dfe-core/   Núcleo DF-e em Java puro (sem Spring): schemas, certificado, TLS, SOAP, serviços
│   └── src/main/resources/
│       ├── schemas/PL_010h/       XSD oficiais da NF-e (Nota Técnica vigente)
│       └── sefaz/webservices.properties   endereços dos web services
└── dfe-cli/    Linha de comando para testar a comunicação com a Sefaz
```

Pacotes do `dfe-core`:

| Pacote | Responsabilidade |
|---|---|
| `comum` | `Ambiente`, `UF`, `ModeloDocumento` |
| `certificado` | `CertificadoA1`: abre o .pfx, lê CNPJ (OID ICP-Brasil 2.16.76.1.3.3), validade |
| `xml` | `Xml` (JAXB ⇄ DOM ⇄ texto sem prefixos, leitura segura contra XXE), `ValidadorXsd` |
| `ws` | `CatalogoWebServices`, `ContextoTls`, `EnvelopeSoap`, `ClienteSefaz` |
| `servico` | `ConsultaStatusServico` (os próximos serviços entram aqui) |
| `br.inf.portalfiscal.nfe` | classes geradas automaticamente dos XSD (em `target/`, não editar) |

## Pré-requisitos

- Java 21 ou superior
- Maven 3.9 ou superior
- Certificado A1 (e-CNPJ, .pfx) de uma empresa com inscrição estadual ativa no RS

## Compilar e testar

```bash
mvn verify
```

Os testes não precisam de certificado nem de internet: geram certificados de teste e
sobem uma "Sefaz falsa" local que exige certificado do cliente, como a real.

## Primeiro marco: status 107 na homologação da Sefaz RS

1. Gere o jar:

   ```bash
   mvn -q package -DskipTests
   ```

2. Rode, com a senha numa variável de ambiente (nunca como argumento):

   Linux/macOS:

   ```bash
   export NFE_PFX_SENHA='sua-senha'
   java -jar dfe-cli/target/dfe-cli.jar status --pfx /caminho/certificado.pfx
   ```

   Windows (PowerShell):

   ```powershell
   $env:NFE_PFX_SENHA = 'sua-senha'
   java -jar dfe-cli\target\dfe-cli.jar status --pfx C:\caminho\certificado.pfx
   ```

   Se não definir `NFE_PFX_SENHA`, a senha é pedida no terminal.

3. Resultado esperado:

   ```
   cStat    : 107
   xMotivo  : Servico em Operacao
   OK: serviço em operação.
   ```

Outras opções: `--modelo nfce`, `--ambiente producao`, `--verbose` (mostra o XML enviado
e recebido) e `--truststore`. Para só conferir o certificado:
`java -jar dfe-cli/target/dfe-cli.jar certificado --pfx certificado.pfx`.

Também dá para rodar o teste automatizado contra a Sefaz real:

```bash
NFE_PFX=/caminho/certificado.pfx NFE_PFX_SENHA='sua-senha' mvn -pl dfe-core test -Dtest=SefazHomologacaoRealTest
```

### Se der erro de TLS ("Certificado do servidor da Sefaz não é confiável")

O Java não traz a cadeia de certificados da ICP-Brasil. Monte uma truststore com ela:

1. Baixe o arquivo único das ACs da ICP-Brasil no site do ITI
   (gov.br/iti → Repositório → "Certificados das ACs da ICP-Brasil – arquivo único compactado")
   e descompacte numa pasta, por exemplo `icpbrasil/`.
2. Importe todos os certificados numa truststore:

   Linux/macOS:

   ```bash
   for c in icpbrasil/*.crt icpbrasil/*.cer; do [ -f "$c" ] && keytool -importcert -noprompt -storetype PKCS12 -keystore icpbrasil.p12 -storepass changeit -alias "$(basename "$c")" -file "$c"; done
   ```

   Windows (PowerShell):

   ```powershell
   Get-ChildItem icpbrasil\* -Include *.crt,*.cer | ForEach-Object { keytool -importcert -noprompt -storetype PKCS12 -keystore icpbrasil.p12 -storepass changeit -alias $_.Name -file $_.FullName }
   ```

3. Rode com `--truststore icpbrasil.p12` e `NFE_TRUSTSTORE_SENHA=changeit`.

## Segurança

- Arquivos `.pfx`, `.p12`, `.jks`, `.pem`, `.key` e `.env` estão no `.gitignore`. **Nunca** faça commit de certificado ou senha.
- A senha do certificado não é aceita como argumento de linha de comando.
- XML recebido é lido com DOCTYPE proibido (proteção contra XXE).

## Atualizar para uma nova Nota Técnica

1. Baixe o novo pacote de schemas no Portal Nacional da NF-e.
2. Coloque em `dfe-core/src/main/resources/schemas/<novo-pacote>/`.
3. Atualize `schemaDirectory` em `dfe-core/pom.xml` e `ValidadorXsd.PACOTE_PADRAO`.
4. Rode `mvn verify`: as classes são regeneradas e os testes mostram o que quebrou.

Os schemas atuais (PL_010h) vieram do espelho público mantido pelo projeto
nfephp-org/sped-nfe. Antes de ir para produção, confira com o pacote oficial do portal.
