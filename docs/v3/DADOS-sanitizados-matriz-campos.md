# Dados sanitizados - matriz de campos

| Tipo de dado | Acao no banco sanitizado | Pode ir para relatorio versionado? | Observacao |
| --- | --- | --- | --- |
| CPF/RG/documento | remover ou mascarar irreversivelmente | nao | documento privado nunca e publico |
| Nome civil | remover ou substituir | nao | nome publico/artistico tambem nao deve ser listado bruto |
| E-mail | substituir por dominio invalido | nao | preservar apenas contagem |
| Telefone/WhatsApp | substituir por placeholder | nao | WhatsApp real nao deve sair do banco sanitizado |
| IP/User-Agent | hash ou remover | nao | metricas so agregadas |
| Storage key/bucket | null/placeholder | nao | nunca expor storage interno |
| URL de midia | null/placeholder | nao | nao baixar arquivo real |
| Payload financeiro | remover ou reduzir a status | nao | sem Pix/Efi real |
| Slug de anuncio | preservar apenas no banco sanitizado | nao | relatorio usa `[slug-sanitizado-n]` |
| Cidade/UF/bairro | preservar | sim, como agregado/padrao | util para SEO |
| Status/Premium | preservar | sim, agregado | sem dado de pagamento |
| Datas | agregar quando possivel | sim, agregado | evitar trilha individual sensivel |

## Estado

Matriz pronta para execucao quando o restore local compativel estiver disponivel.

## Bloco 29.1

A sanitizacao automatica preparada para o banco sanitizado local cobre campos textuais com nomes ou padroes evidentes de CPF, documento, e-mail, telefone, WhatsApp, IP, URL, storage, token, senha, certificado, Pix/Efi, payload financeiro e logs sensiveis. Valores brutos nao devem aparecer em relatorios versionados.

## Bloco 29.2

A matriz permanece aplicavel, mas nao foi executada porque `postgres:17` nao foi obtido localmente. O bloqueio evita abrir o backup sem cliente compativel e sem Docker daemon operacional.

## Bloco 29.3

Nenhum campo real foi aprovado como sanitizado. O restore bruto falhou antes da sanitizacao, e o banco/container sanitizado ficou sem restore completo.

A matriz permanece obrigatoria para nova tentativa: CPF, RG, documento, selfie, nome civil, e-mail, telefone/WhatsApp, endereco especifico, IP/user-agent, storage, URL, Pix/Efi, payload financeiro, token, senha, certificado e logs sensiveis devem ser removidos, anulados, mascarados ou substituidos por placeholder/hash irreversivel conforme constraint.

## Bloco 29.4

A matriz ainda nao foi aplicada. O restore falhou novamente com diagnostico sanitizado `CONSTRAINT/FK`; os bancos permaneceram sem tabelas apos `--single-transaction`.

Nao ha dados sanitizados aprovados. A aplicacao da matriz depende de restore completo e decisao Pro/humana.

## Bloco 29.5

A matriz pode ser aplicada em modo quarentena, sobre banco sem `POST_DATA`, para remover CPF, documento, nome civil, e-mail, telefone, WhatsApp, endereco sensivel, IP, user-agent, token, senha, storage, URL real, payload financeiro e identificadores Pix/Efi. Esse modo nao aprova dados para staging final.

Resultado: matriz aplicada na quarentena e validacao agregada ficou com CPF, e-mail, telefone/WhatsApp, IP, URL/storage e token/Pix/Efi em 0.

## Bloco 30

A matriz de dados sanitizados permanece valida para pre-staging/cutover, mas nao sera executada neste ciclo. O Bloco 30 nao realiza restore novo, sanitizacao nova, correcao de orfaos, SQL em producao ou uso de banco de quarentena como staging final.

Para desenvolvimento local, a referencia passa a ser `docs/v3/DADOS-sinteticos-local.md` e a fixture `backend/src/test/resources/fixtures/v3-dados-sinteticos.json`. Esses dados sao artificiais e nao substituem dados sanitizados de homologacao/cutover.

Campos reais seguem proibidos em documentos e ZIPs versionados: CPF/RG/documento, selfie, nome civil real, e-mail real, telefone/WhatsApp real, endereco especifico real, IP/user-agent bruto, storage/bucket real, URL privada, payload financeiro, token, senha, certificado, log bruto e Pix/Efi real.
