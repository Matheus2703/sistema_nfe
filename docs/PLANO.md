# Plano do sistema

Emissor próprio de NF-e e NFC-e para a Sefaz RS. Java, multiempresa, web, Simples Nacional.
Estimativa total: **4 a 5 meses** para um desenvolvedor Java experiente.

## Arquitetura

```
┌───────────── Navegador ─────────────┐
│ Thymeleaf + HTMX                    │
└─────────────────┬───────────────────┘
                  │ HTTPS
┌─────────────────▼──────────────────────────────────────┐
│                 Aplicação Spring Boot                   │
│  web / auth ─ cadastros ─ motor fiscal (Simples)        │
│        │                                                │
│  ┌─────▼───────── dfe-core (Java puro) ──────────────┐  │
│  │ montagem XML (JAXB) → chave de acesso → assinatura│  │
│  │ → validação XSD → cliente SOAP (TLS mútuo/CNPJ)   │  │
│  │ → protocolo/nfeProc → eventos → DANFE             │  │
│  └───────────────────────────────────────────────────┘  │
│  fila de emissão (tabela no banco) ─ cofre certificados │
└──────┬──────────────────┬──────────────────┬───────────┘
       │                  │                  │
  PostgreSQL       Armazenamento de XML   Sefaz RS / SVRS
                   (S3/MinIO ou disco)    Ambiente Nacional
```

Módulos previstos: `dfe-core`, `dfe-danfe`, `fiscal`, `app` (+ `dfe-cli` para testes).

## Fases

| Fase | Entrega | Marco | Estimativa |
|---|---|---|---|
| **0. Fundação** | Maven, XSD → JAXB, certificado, TLS mútuo, SOAP | `NfeStatusServico4` responde **107** em homologação | 1 semana |
| **1. Núcleo NF-e** | XML mínimo (CSOSN 102), chave de acesso, assinatura, autorização síncrona, `nfeProc`, consulta protocolo | **Primeira NF-e autorizada (100)** | 2–3 semanas |
| **2. Eventos** | Cancelamento, carta de correção, inutilização | Cancelar e corrigir nota de teste | 1 semana |
| **3. DANFE** | PDF A4 conforme MOC, e-mail com XML + PDF | DANFE aprovado pelo contador | 1–2 semanas |
| **4. Multiempresa e web** | Empresas, usuários, cofre de certificados, produtos, clientes, naturezas, séries, painel | Duas empresas emitindo isoladas | 2–3 semanas |
| **5. Motor fiscal Simples** | CSOSN 101/102/103/201/202/500/900, ST, DIFAL, IBS/CBS | Casos validados pelo contador | 2 semanas |
| **6. NFC-e** | CSC, QR Code, DANFE 80 mm, contingência offline | Primeira NFC-e autorizada | 2 semanas |
| **7. Robustez** | Fila, novas tentativas, contingência SVC, alertas, monitoramento | Queda simulada sem perder nota | 1 semana |
| **8. Notas recebidas** | `NFeDistribuicaoDFe` + manifestação do destinatário | Download automático por CNPJ | 1–2 semanas |
| **9. Produção** | Credenciamento, segurança, backup 5 anos, LGPD, `tpAmb=1` | Primeira nota real | 1–2 semanas |

## Decisões técnicas que evitam rejeição

- XML no namespace padrão, sem prefixos (`ns2:`) nem declarações sobrando.
- Assinatura só sobre `infNFe`, `Reference URI="#NFe{chave}"`, enveloped + C14N, RSA-SHA1; `Id` registrado como ID.
- Truststore com a cadeia ICP-Brasil (não vem no Java).
- Um `SSLContext` por empresa, em cache, recriado na troca do certificado.
- Numeração com trava no banco por (empresa, modelo, série); número nunca reaproveitado; buracos viram inutilização.
- Timeout nunca reenvia com número novo: consulta pela chave; rejeição 204 (duplicidade) recupera o protocolo.
- Estados: `RASCUNHO → VALIDADA → ASSINADA → ENVIADA → AUTORIZADA | REJEITADA | DENEGADA → CANCELADA`.
- Todo XML enviado e recebido é guardado (auditoria).
- Certificados criptografados em repouso (envelope encryption); senha nunca em log.

## Regras de homologação

- `tpAmb=2`. Na NF-e, nome do destinatário: `NF-E EMITIDA EM AMBIENTE DE HOMOLOGACAO - SEM VALOR FISCAL`.
- CNPJ do emitente igual ao do certificado; inscrição estadual ativa no RS.

## Riscos

| Risco | Mitigação |
|---|---|
| Assinatura rejeitada (canonicalização/namespace) | Resolver na fase 1 com testes de XML de referência |
| Mudança de Nota Técnica (IBS/CBS) | Classes geradas dos XSD; acompanhar Portal Nacional e Portal DF-e SVRS |
| Regra fiscal errada | Contador valida os casos da fase 5 antes da produção |
| Vazamento de certificado | Cofre criptografado, senha separada, logs sem segredos |
| Duplicidade ou buraco na numeração | Trava no banco, consulta por chave, inutilização |

## Decisões tomadas

- Linguagem: Java 21 ([ADR-001](ADR-001-linguagem-e-arquitetura.md)).
- Sem biblioteca fiscal pronta.
- NFC-e depois da NF-e (fase 6).
- Tela: Thymeleaf + HTMX.

## Em aberto

- Hospedagem (nuvem, VPS ou servidor próprio): o sistema roda em Docker em qualquer uma.
- Contador para validar motor fiscal e DANFE.
