# ADR-001: Linguagem e arquitetura base

**Status:** Aceito
**Data:** 27/11/2026

## Contexto

- Emissão de NF-e (55) e NFC-e (65) na Sefaz RS, começando em homologação.
- Várias empresas (multiempresa), todas do Simples Nacional no início.
- Interface web; até 20 empresas no primeiro ano.
- "Do zero": sem biblioteca fiscal pronta, mas com bibliotecas genéricas (JAXB, Spring, PDF).
- Quem mantém o projeto domina Java.

## Decisão

**Java 21 LTS, Maven multi-módulo, monólito modular.** O núcleo de comunicação com a Sefaz
(`dfe-core`) é Java puro, sem framework, para poder ser testado e reaproveitado sozinho.
A aplicação web (fase 4) usará Spring Boot, PostgreSQL e Thymeleaf + HTMX, em Docker.

## Por que Java

| Necessidade da NF-e | Recurso pronto |
|---|---|
| Assinatura XMLDSig (RSA-SHA1, C14N, enveloped) | `javax.xml.crypto.dsig` (JDK) |
| Certificado A1 (.pfx) | `KeyStore` PKCS12 (JDK) |
| XML a partir dos XSD oficiais | JAXB/xjc |
| Validação XSD | `javax.xml.validation` (JDK) |
| SOAP 1.2 com TLS mútuo por empresa | `SSLContext` + `java.net.http.HttpClient` (JDK) |

## Opções consideradas

- **C#/.NET:** tecnicamente equivalente; descartado pela familiaridade com Java.
- **Python / Node.js:** assinatura e TLS mútuo dependem de bibliotecas externas com mais armadilhas.
- **Microsserviços:** complexidade sem ganho para o volume esperado. O monólito modular pode ser dividido depois.
- **Biblioteca fiscal pronta (java-nfe, ACBr):** descartada por decisão do projeto; o custo é manter MOC e Notas Técnicas internamente.

## Consequências

- Fica fácil: atualizar a cada Nota Técnica (regenerar classes do novo XSD), testar o núcleo isolado.
- Fica por nossa conta: regras fiscais, layout do DANFE, acompanhamento das NTs.
- Classes geradas pelo JAXB usam prefixos de namespace por padrão; o `Xml` normaliza tudo para o
  namespace padrão sem prefixo, exigência da Sefaz.
- O SOAP é montado à mão (envelope simples) em vez de clientes gerados por WSDL: menos dependências
  e controle total do XML enviado.
