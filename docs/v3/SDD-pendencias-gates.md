# SDD - Pendencias e gates

Este documento lista pendencias conhecidas e gates obrigatorios antes de fases futuras, homologacao ou producao.

## Gates sempre obrigatorios

- `scripts/security/verificar-codificacao.ps1`
- `scripts/security/verificar-arquivos-proibidos.ps1`
- `scripts/security/verificar-segredos.ps1`
- `git diff --check`
- `git diff --cached --check`
- `git status --short`
- `git remote -v`

## Gate de build local

Antes de novos blocos tecnicos:

- `scripts/local/diagnosticar-toolchain-local.ps1`
- `scripts/local/validar-build-local.ps1`
- backend `mvn -q -DskipTests compile`
- backend `mvn -q test`
- frontend `npm run lint`
- frontend `npm run build`

Pendencia aceita apenas como bloqueio operacional: ferramenta ausente sem autorizacao para instalar/baixar.

## Gate de banco

Antes de schema aprovado:

- validacao SQL estatica;
- PostgreSQL descartavel;
- Flyway real quando CLI/imagem estiver disponivel localmente;
- `scripts/local/validar-flyway-real-local.ps1` deve retornar `0` somente para Flyway real OK, `1` para falha e `2` para pendencia operacional;
- revisao Pro.

Proibido antes da aprovacao:

- banco persistente de dominio;
- migration nova sem fase expressa;
- SQL de schema fora de fase;
- importador real;
- homologacao/producao dependente de schema.

## Gate de UI mobile

Para mudancas visuais publicas:

- validar ausencia de scroll horizontal;
- validar cards dentro da viewport;
- validar CTA dentro do fluxo;
- validar ausencia de elemento flutuante solto;
- validar ausencia de animacao automatica;
- validar ausencia de scroll lock;
- validar ausencia de `document.body.style.overflow`;
- executar `scripts/local/validar-ui-mobile-estatica.ps1`;
- executar `scripts/local/validar-layout-publico-renderizado.ps1` quando houver home, cidade, bairro, anuncio ou `/anunciar`;
- gerar evidencias desktop/mobile quando a fase exigir.

## Gate de paridade visual producao

Status atual: `BLOQUEADO_PARIDADE_VISUAL_PRODUCAO`.

Mitigacao parcial: o Bloco 57 executa paridade visual publica fase 1 em shell/header, home, cards e listagens de cidade/bairro. O Bloco 58 identifica `C:\clone\topsdojob-frontend` como fonte visual local de producao, documenta o transplante e aplica apenas a base visual global/header de baixo risco. O Bloco 59 aprofunda cards, grids, placeholders e detalhe publico do anuncio. O Bloco 60 aplica a logo real local no header publico e aproxima visualmente o wizard `/anunciar`. O gate continua aberto para footer/admin quando aplicavel, comparacao final e revisao humana/Pro.

Antes de homologacao/cutover:

- comparar V3 local com producao publica atual em home, cidade, bairro, detalhe de anuncio, `/anunciar` e mobile;
- preservar header, containers, densidade de listagem, cards, cores, tipografia, botoes, espacamentos e hierarquia observaveis;
- gerar prints lado a lado ou redigidos quando houver risco de dado real;
- nao versionar dado real bruto de producao;
- obter revisao humana/Pro;
- nao avancar para staging real, VPS, restore, importacao real ou cutover enquanto o bloqueio estiver aberto.

## Gate de copy visivel

Para mudancas que renderizam UI publica/admin:

- nao exibir copy de bastidor como `local`, `sintetico`, `mock`, `fixture`, `smoke test`, `descartavel` ou `API local`;
- nao exibir `Metadados publicos locais`, enum `ANUNCIO`, `Autorizacao`, `admin configurar`, `anuncio ler` ou `Preparar autorizacao` como copy renderizada;
- nao exibir pares redundantes como `Fluxo autorizado` ou `Autorização autorizada` em telas publicas;
- usar acentuacao em textos publicos/admin como `anúncio`, `moderação`, `benefícios`, `sessão`, `papéis`, `permissões` e `ações`;
- manter identificadores tecnicos fora da UI ou formatados por helpers de exibicao;
- executar validadores renderizados aplicaveis: publico, wizard, admin/moderacao e Premium/beneficios.

## Gate de SEO publico

Para mudancas em rotas publicas, sitemap, robots, canonical, `/anunciar` ou textos de SEO:

- preservar `/anuncios/[slug]`;
- preservar `/acompanhantes/[uf]/[cidade]`;
- preservar `/acompanhantes/[uf]/[cidade]/[bairro]`;
- preservar `/anunciar`;
- impedir rotas paralelas publicas de anuncio;
- confirmar sitemap sem API;
- confirmar sitemap sem dominio de producao em local;
- confirmar robots local seguro;
- confirmar admin `noindex`;
- confirmar ausencia de textos publicos "skeleton" ou "API local";
- executar `scripts/local/validar-seo-publico-local.ps1`.
- executar `scripts/local/validar-layout-publico-renderizado.ps1` para provar ausencia de mini-coluna, H1 verticalizado, breadcrumbs estreitos e wizard espremido.

## Gate de producao atual

Se comportamento publico atual nao estiver claro:

- procurar primeiro em documentos locais, migrations, codigo e historico;
- consultar producao somente se estritamente necessario;
- consulta deve ser somente leitura;
- registrar motivo, comandos e evidencia;
- nao imprimir secrets, `.env`, certificados, tokens ou senhas;
- nao alterar arquivo, banco, midia, servico, deploy ou configuracao.

## Gate de staging/homologacao/producao

Sequencia obrigatoria:

1. local validado;
2. contrato de homologacao aprovado;
3. staging real criado em bloco proprio;
4. dry-run real autorizado;
5. homologacao validada;
6. producao com backup e rollback.

Bloqueios:

- fonte real nao autorizada;
- Flyway real OK localmente no Bloco 47, mas deve ser repetido em homologacao controlada;
- gitleaks real OK localmente no Bloco 44, mas deve ser repetido como gate operacional/CI;
- CSRF producao pendente;
- auditoria JSON Pro pendente;
- CDN/storage pendente;
- Pix/Efi pendente;
- SEO real pendente;
- paridade visual com producao pendente (`BLOQUEADO_PARIDADE_VISUAL_PRODUCAO`);
- deploy/cutover sem rollback.

## Gate de importador real

Antes de fonte real:

- autorizacao formal;
- contrato `docs/v3/HOMOLOGACAO-importacao-real-dryrun.md` revisado;
- diretorio fora do workspace/repositorio;
- `scripts/local/validar-fonte-importacao-local.ps1` com parametro autorizado;
- inventario de metadados sem abrir conteudo sensivel;
- revisao Pro;
- plano de rollback.

Continua proibido:

- dump sem autorizacao;
- copiar dado real para o repositorio;
- ETL real sem fase;
- banco de producao;
- importacao direta.

## Gate de Efi/Pix

Antes de Efi real:

- contrato `docs/v3/HOMOLOGACAO-financeiro-pix-efi-webhooks.md` revisado;
- credenciais fora do Git;
- ambiente de homologacao separado;
- politica de segredo;
- contrato de webhook revisado;
- logs sem payload sensivel;
- conciliacao testada com dados autorizados;
- revisao Pro.

Continua proibido:

- Efi real em local sem autorizacao;
- QR Code real;
- copia e cola real;
- cobranca real;
- webhook real;
- credito real por pagamento.

## Gate de midia/CDN

Antes de URL publica de midia:

- politica de storage/CDN aprovada;
- contrato `docs/v3/HOMOLOGACAO-storage-upload-cdn.md` revisado;
- separacao de midia publica e privada;
- regra de documento privado;
- assinatura/expiracao quando aplicavel;
- auditoria de acesso;
- DTO publico sem storage interno;
- validacao mobile de placeholders/midia.
- antivirus/moderacao real definidos;
- rollback de URL publica e invalidacao de cache testados.

## Gate juridico/documental

Pendencias:

- politica final de retencao;
- base legal de armazenamento;
- fluxo de remocao/expurgo;
- auditoria de acesso a documento;
- regras de validacao documental;
- decisao juridica final antes de automacao de expurgo.

## Gate de auditoria

Pendencias:

- auditoria JSON completa revisada por Pro;
- idempotencia real para comandos criticos quando houver suporte de schema;
- request id de ponta a ponta;
- retencao de logs sem dados sensiveis;
- mascaramento revisado para todos os DTOs admin.

## Gate de admin/producao

Antes de admin em ambiente nao local:

- CSRF revisado;
- cookie seguro;
- politica de senha/credencial;
- lockout distribuido/rate limit operacional revisado para multiplas instancias;
- session fixation validado em HTTPS/cookie Secure;
- RBAC revisado;
- rate limit;
- logs e auditoria;
- protecao contra acoes duplicadas;
- deny-all confirmado.

## Pendencias do Bloco 26.1

- `/anunciar` foi ajustado visualmente sem redesign.
- SDD central foi criado.
- Evidencias desktop/mobile devem ser geradas localmente no fechamento do bloco.
- Admin nao foi alterado por este ajuste visual; prints admin so sao necessarios se houver alteracao posterior.

## Pendencias do Bloco 26.2

- Revisao Pro da paridade final do wizard antigo antes de homologacao.
- Upload/KYC real permanecem pendentes.
- Premium real permanece pendente de fase financeira aprovada.
- Search Console completo e cutover SEO ficam para fase futura.
- Remover `noindex` publico somente em cutover aprovado.

## Pendencias do Bloco 27

- Search Console completo ainda pendente.
- Inventario completo de URLs reais ainda pendente.
- Revisao Pro de paginas vazias antes de indexacao futura.
- Gate para bairros vazios antes de producao.
- Cutover de robots/canonical/indexacao depende de homologacao aprovada.
- Nenhuma remocao de `noindex` foi feita nesta fase.

## Pendencias do Bloco 28

- Search Console completo ainda precisa ser exportado manualmente.
- Mapa completo de URLs reais depende de revisao com dados autorizados, sem versionar lista bruta de anuncios.
- Redirects 301 devem ser testados antes de qualquer cutover.
- Canonical, sitemap e robots de producao dependem de gate aprovado.
- Cidades prioritarias dependem de Search Console e conteudo real aprovado.
- Cutover SEO continua bloqueado ate mapa completo aprovado.
- Saida bruta externa `C:\topsv3-auditoria-local\seo\bloco-28` nao deve entrar no Git nem no ZIP.

## Pendencias do Bloco 29

- Disponibilizar cliente/imagem PostgreSQL compativel com custom format 1.16 em ambiente local autorizado.
- Executar restore local isolado do backup autorizado.
- Criar banco bruto local e banco sanitizado local.
- Executar sanitizacao real de CPF/RG/documento, nome civil, e-mail, telefone/WhatsApp, IP/User-Agent, storage, midia e payloads sensiveis.
- Validar contagens reais sanitizadas.
- Validar SEO com dados sanitizados.
- Classificar 45 URLs desconhecidas do Bloco 28.
- Manter backup bruto fora do Git, ZIP e `C:\topsv3`.

## Pendencias do Bloco 29.1

- Executar `docker pull postgres:17` somente se o usuario autorizar conscientemente.
- Reexecutar diagnostico de cliente PostgreSQL compativel.
- Executar restore local isolado apos cliente 17.x existir localmente.
- Executar sanitizacao real no banco sanitizado, nunca no banco bruto.
- Validar ausencia de CPF, e-mail, telefone, IP, storage, URL real, Pix/Efi, token, senha, certificado e payload sensivel.
- Gerar agregados SEO com dados sanitizados sem listar slugs reais.

## Pendencias do Bloco 29.2

- Disponibilizar Docker daemon local.
- Reexecutar apenas o download autorizado `docker pull postgres:17` se houver nova tentativa consciente.
- Confirmar imagem `postgres:17` local.
- Reexecutar `pg_restore -l` com `--pull=never`.
- Executar restore local isolado, sanitizacao real e validacoes agregadas.
- Manter `--pull=never` em todo `docker run` operacional.

## Pendencias do Bloco 29.3

- Decidir tratamento seguro dos recursos proprios parcialmente criados: `topsv3-bloco29-pg17-bruto`, `topsv3-bloco29-pg17-sanitizado`, `topsv3-bloco29-pgdata-bruto`, `topsv3-bloco29-pgdata-sanitizado` e `topsv3-bloco29-net`.
- Investigar `FALHA_PG_RESTORE_RAW` sem versionar saida bruta, slugs, dados reais ou conteudo sensivel.
- Nao executar limpeza destrutiva automatica sem autorizacao expressa.
- Reexecutar restore completo somente com recursos exclusivos `topsv3-bloco29-*` e `docker run --pull=never`.
- Executar sanitizacao real somente no container/banco sanitizado.
- Validar ausencia de CPF, e-mail, telefone, IP, storage, URL real, Pix/Efi, token, senha, certificado e payload sensivel.
- Gerar agregados SEO com dados sanitizados sem listar slugs reais.
- Preservar TopsWI/terceiros: sem stop, rm, prune, compose down, volume rm ou reutilizacao de recurso externo.

## Pendencias do Bloco 29.4

- Revisar com Pro/humano o raw log externo nao versionado do `pg_restore`.
- Investigar a falha sanitizada `CONSTRAINT/FK` em fase `POST_DATA`.
- Nao aplicar `--disable-triggers`, `--clean`, `--if-exists`, `--schema-only`, `--data-only` ou outra flag sem justificativa tecnica aprovada.
- Manter `--single-transaction` em novas tentativas.
- Executar restore completo bruto e sanitizado somente apos decisao explicita.
- Executar sanitizacao real apenas apos restore completo.
- Validar dados e SEO sanitizados somente apos sanitizacao aprovada.
- Manter raw logs, backup, dump e qualquer saida bruta fora do repositorio e fora do ZIP.
- Preservar TopsWI/terceiros: sem stop, rm, prune, compose down, volume rm ou reutilizacao de recurso externo.

## Pendencias do Bloco 29.5

- Revisar com Pro/humano o dossie A/B/C.
- Caminho final consolidado no Bloco 29.6: Opcao A e obrigatoria para homologacao/cutover; Opcao B e apenas insumo auxiliar; Opcao C permanece bloqueada ate revisao Pro/humana em novo bloco.
- Classificar em novo bloco, ou por revisao Pro/humana, os 4 erros agregados de sanitizacao registrados como `ALERTA_SANITIZACAO_AGREGADA_NAO_CLASSIFICADA`.
- Nao promover a quarentena a staging final.
- Nao iniciar importacao definitiva com dados da quarentena.
- Nao usar a quarentena para validacao transacional final.

## Pendencias do Bloco 29.6

- Revisao Pro/humana para aprovar caminho A antes de homologacao/cutover.
- Obter novo backup consistente ou corrigir a origem/backup antes de qualquer staging final.
- Manter a quarentena apenas como diagnostico agregado e insumo auxiliar.
- Nao executar correcao local de orfaos sem novo bloco aprovado.
- Manter logs brutos, backup, dump, SQL bruto e dados reais fora do repositorio e do ZIP.

## Pendencias do Bloco 30

- Bloco 29 fica adiado como gate de pre-staging/cutover.
- Reexecutar frente de dados reais somente com autorizacao expressa, novo backup consistente ou correcao da origem/backup.
- Gitleaks real continua gate antes de homologacao/producao enquanto ausente no PATH.
- Validacoes que exigem Docker devem respeitar a politica de nao alterar Docker deste bloco.
- Base sintetica deve ser mantida sem dados reais, sem midia real, sem telefone/e-mail real e sem payload financeiro real.

## Pendencias do Bloco 31

- Bloco 31 local sintetico: E2E/API/SEO aprovados.
- Bloco 29 segue adiado para pre-staging/cutover.
- Quarentena sem `POST_DATA` continua proibida como staging final.
- `gitleaks` real segue pendente no PATH e permanece gate antes de producao.

## Pendencias do Bloco 31.1

- Validadores sinteticos API/SEO nao podem retornar OK por relatorio antigo quando o backend local estiver indisponivel.
- Backend indisponivel em validacao padrao deve retornar pendente com exit code 2.
- Reutilizacao de evidencia existente exige parametro explicito e alerta documentado.
- Prefixo default do E2E local descartavel deve permanecer `topsv3-e2e-local`; wrapper sintetico deve usar `topsv3-e2e-sintetico`.
- Bloco 29 segue adiado para pre-staging/cutover e a quarentena sem `POST_DATA` continua proibida como staging final.
- `gitleaks` real segue pendente no PATH.
- Pro continua obrigatorio antes de homologacao/cutover real com dados reais/sanitizados, financeiro, Pix/Efi, webhooks ou producao.

## Pendencias do Bloco 32

- Auditoria renderizada sintetica automatizada passou com dados locais, mas a revisao visual humana reprovou textos tecnicos visiveis; a correcao fica no Bloco 32.1.
- Manter prints apenas sinteticos e leves; nunca versionar midia/documento real.
- Bloco 29 segue adiado para pre-staging/cutover e a quarentena sem `POST_DATA` continua proibida como staging final.
- `gitleaks` real segue pendente no PATH.
- Pro continua obrigatorio antes de homologacao/cutover real com dados reais/sanitizados, financeiro, Pix/Efi, webhooks ou producao.
- Pro continua obrigatorio antes de homologacao/cutover real com dados reais/sanitizados, financeiro, Pix/Efi, webhook ou producao.

## Pendencias do Bloco 32.1

- Confirmar por prints desktop/mobile que `PENDENTE_POLITICA_EXPOSICAO_WHATSAPP_PUBLICO` e `conteudo_autorizado` nao aparecem em paginas publicas.
- Manter o validador renderizado falhando para enum/status/snake_case tecnico visivel.
- Manter anuncio `BLOQUEADO` sem WhatsApp publico indevido.
- `gitleaks` real segue pendente no PATH.
- Pro continua obrigatorio antes de homologacao/cutover real com dados reais/sanitizados, financeiro, Pix/Efi, webhooks ou producao.

## Pendencias do Bloco 33

- Wizard `/anunciar` validado localmente com dados sinteticos e prints desktop/mobile.
- Manter `scripts/local/validar-wizard-anunciar-sintetico-local.ps1` como gate antes de alterar o funil publico.
- O wizard nao pode ganhar upload real, pagamento, Pix/Efi, checkout, Premium obrigatorio, stores, e-mail real, WhatsApp real, autopublicacao, scroll lock ou elemento mobile flutuante sem fase expressa.
- `gitleaks` real segue pendente no PATH.
- Pro continua obrigatorio antes de homologacao/cutover real com dados reais/sanitizados, restore completo, financeiro, Pix/Efi, webhooks ou producao.

## Pendencias do Bloco 33.1

- Textos publicos sem acento do wizard `/anunciar` corrigidos sem alteracao funcional.
- Validador do wizard reforcado para reprovar textos criticos antigos renderizados ao visitante.
- Checkpoint local do delta Bloco 33/33.1 criado no Bloco 34 em `b7f5f98`.
- `gitleaks` real segue pendente no PATH.
- Pro continua obrigatorio antes de homologacao/cutover real com dados reais/sanitizados, restore completo, financeiro, Pix/Efi, webhooks ou producao.

## Pendencias do Bloco 34

- Paridade profunda do wizard de producao ficou limitada pelo age gate publico, que nao foi aceito nem contornado.
- Admin/moderacao do anuncio criado pelo wizard foi validado sinteticamente no Bloco 35.
- Manter o wizard local sem stores, upload real, pagamento real, Pix/Efi real, e-mail real, WhatsApp real, publicacao automatica, scroll lock ou elemento mobile flutuante.
- `gitleaks` real segue pendente no PATH.
- Pro continua obrigatorio antes de homologacao/cutover real com dados reais/sanitizados, restore completo, financeiro, Pix/Efi, webhooks, importador real ou producao.

## Pendencias do Bloco 35

- Checkpoint corrigido do Bloco 34 criado em `9b677ea`, sem remote e sem push.
- Correcao pos-auditoria do Bloco 35 removeu copy publica de bastidor da etapa "Fotos e videos" do wizard `/anunciar`, sem alteracao funcional.
- Admin/moderacao sintetica local validada com PostgreSQL descartavel e prefixo Docker `topsv3-admin-sintetico-*`.
- Checkpoint local do Bloco 35 criado no Bloco 36 em `00e1a02`, sem remote e sem push.

## Pendencias do Bloco 36

- Premium/beneficios sinteticos validados com PostgreSQL descartavel e prefixo Docker `topsv3-premium-sintetico-*`.
- Plano gratuito permanece util e sem limite comercial artificial.
- Premium permanece aditivo, sem promessa de contratacao ou resultado garantido.
- Expiracao real automatica, checkout, Pix/Efi real, pagamento real, credito real, webhook e financeiro real continuam bloqueados ate fase propria e revisao Pro.
- Bloco 29 permanece aberto e adiado para pre-staging/cutover.
- Quarentena sanitizada sem `POST_DATA` continua proibida para staging final.
- Autenticacao/RBAC de producao, auditoria JSON de homologacao/producao, dados reais/sanitizados, financeiro, Pix/Efi, webhooks, importador real e producao continuam exigindo Pro.
- `gitleaks` real segue pendente no PATH quando nao instalado; fallback local permanece ativo.

## Pendencias do Bloco 37 ao Bloco 40

- Bloco 37 corrigido removeu copy visivel tecnica/local/sintetica e deve permanecer protegido pelos validadores renderizados.
- Bloco 38 poliu rotulos publicos de status sem alterar regra de autorizacao.
- Bloco 39 validou Age Gate/WhatsApp sintetico para `LIVRE` e `BLOQUEADO`, mantendo WhatsApp mediado pelo backend.
- Bloco 40 corrigido validou midia/fotos/stories sinteticos e corrigiu a cadeia de encoding que gerava mojibake em prints publicos.
- Checkpoint local do Bloco 40 corrigido: `f67880a`.

## Pendencias do Bloco 41

- MVP local sintetico consolidado como base local, nao como aprovacao de homologacao/cutover.
- Bloco 29 permanece materialmente aberto e adiado para pre-staging/cutover.
- Quarentena sanitizada sem `POST_DATA` continua proibida para staging final.
- Pro continua obrigatorio antes de dados reais/sanitizados, restore completo, autenticacao/RBAC de producao, financeiro real, Pix/Efi real, webhooks, importador real, homologacao ou producao.
- `gitleaks` real segue pendente no PATH quando nao instalado; fallback local permanece ativo nas validacoes locais.

## Pendencias do Bloco 42

- Checkpoint local do Bloco 41 criado em `46ed655`, sem remote e sem push.
- Matriz de prontidao para homologacao/cutover criada, sem executar producao, restore, dados reais, staging, Pix/Efi, webhook, importador real ou API externa.
- Pronto localmente: MVP sintetico, validadores locais e documentacao de gates.
- Pendente antes de homologacao: restore completo aprovado, staging controlado, Flyway real, gitleaks real ou decisao formal, hardening auth/RBAC/CSRF, storage/CDN real, upload real, SEO real e monitoramento.
- Bloqueante antes de producao: dados reais/sanitizados aprovados, backup/rollback, Pix/Efi/webhooks homologados, financeiro real revisado, auditoria JSON, LGPD/dados sensiveis e decisao humana/Pro onde aplicavel.

## Pendencias do Bloco 43

- Checkpoint local do Bloco 42 criado em `09ffcd3`, sem remote e sem push.
- `gitleaks` real nao foi encontrado no PATH: `PENDENTE_GITLEAKS_REAL_NO_PATH`.
- Nenhuma instalacao automatica foi executada.
- Fallback local permanece secundario e nao substitui gate definitivo de producao sem decisao formal.
- Empacotador corrigido para preencher `Objetivo` no `RESUMO-ENTREGA.md` mesmo quando `-ResumoExecucao` ou metadados JSON vierem vazios.

## Pendencias do Bloco 44

- Checkpoint local do Bloco 43 criado em `71404a3`, sem remote e sem push.
- `winget` encontrado em `C:\Users\WpD\AppData\Local\Microsoft\WindowsApps\winget.exe`.
- `gitleaks` 8.30.1 instalado via `winget`.
- Primeiro scan real encontrou 4 achados em `frontend/.next`, artefato ignorado de build local.
- `frontend/.next` foi removido por ser build/cache ignorado e regeneravel.
- Scan real final `gitleaks detect --source . --no-git --redact --verbose` passou sem leaks.
- Fallback local permanece secundario e continua sendo executado pelo scanner de segredos.

## Pendencias do Bloco 45

- Checkpoint local do Bloco 44 criado em `0603a59`, sem remote e sem push.
- Flyway CLI nao foi encontrado no PATH.
- Imagem `flyway/flyway` nao foi encontrada localmente.
- Imagens `postgres:16` e `postgres:17` existem localmente, mas PostgreSQL descartavel do Flyway nao foi iniciado porque Flyway real estava ausente.
- Resultado atual: `PENDENTE_FLYWAY_REAL_LOCAL`.
- Nenhuma instalacao automatica foi executada.
- Nenhum `docker pull` foi executado.
- Nenhum recurso Docker do Flyway foi criado.
- Validacao Flyway real continua gate antes de homologacao/producao.

## Pendencias do Bloco 46

- Checkpoint local do Bloco 45 criado em `282802d`, sem remote e sem push.
- `winget` esta disponivel localmente.
- `flyway` nao foi encontrado no PATH.
- Pacote exato `Redgate.Flyway` nao foi encontrado via `winget search`.
- Resultado atual: `PENDENTE_FLYWAY_INSTALACAO_LOCAL`.
- Nenhuma instalacao alternativa foi executada.
- Nenhum `docker pull` foi executado.
- Validacao Flyway real local permanece pendente e continua gate antes de homologacao/producao.

## Estado do Bloco 47

- Checkpoint local do Bloco 46 criado em `220c2ba`, sem remote e sem push.
- `docker pull flyway/flyway` foi autorizado e executado com sucesso.
- Imagem local: `flyway/flyway:latest`.
- Versao observada: Flyway OSS Edition 12.10.0 by Redgate.
- Validacao Flyway real local: `OK_FLYWAY_REAL_LOCAL`.
- Migrations V001 a V017 aplicadas em PostgreSQL 17 descartavel.
- `flyway info`, `migrate`, `validate` e `info` final executados com sucesso.
- Recursos Docker temporarios `topsv3-flyway-local-*` foram removidos.
- Homologacao/producao continuam exigindo gates proprios, revisao Pro e ambiente controlado.

## Estado do Bloco 48

- Checkpoint local do Bloco 47 criado em `7791d11`, sem remote e sem push.
- Auth/RBAC/CSRF local validado com `scripts/local/validar-auth-rbac-csrf-local.ps1`.
- Resultado: `OK_AUTH_RBAC_CSRF_LOCAL`.
- Login admin, cookie `HttpOnly`/`SameSite=Lax`, logout, bloqueio sem sessao, RBAC `ADMIN`/`MODERADOR`, fallback `/api/**` e CORS local passaram com dados sinteticos.
- CSRF local permanece desabilitado apenas para smoke controlado em `APP_ENV=local`.
- Nao-local possui `CookieCsrfTokenRepository`, mas homologacao/producao ainda exigem revisao Pro de CSRF real, HTTPS, cookie seguro, CORS definitivo e politica de sessao.
- Nenhuma producao, VPS, dado real, restore, staging, Pix/Efi real, webhook, API externa ou push foi usado.

## Estado do Bloco 49

- Checkpoint local do Bloco 48 criado em `9bd38f3`, sem remote e sem push.
- Observabilidade/auditoria local validada com `scripts/local/validar-observabilidade-auditoria-local.ps1`.
- Resultado: `OK_OBSERVABILIDADE_AUDITORIA_LOCAL`.
- `RequestIdFilter` passou a rodar antes da seguranca para cobrir 401/403.
- `AdminSecurityErrorWriter` foi endurecido para UTF-8, JSON via writer e flush explicito.
- Request-id, logs locais, erros 400/401/403/404/500 e auditoria admin sanitizada foram validados com dados sinteticos.
- Producao/homologacao ainda exigem logs estruturados JSON finais, hashing real de IP/user-agent, pipeline centralizado, alertas, retencao e revisao Pro de auditoria JSON.
- Nenhuma producao, VPS, dado real, restore, staging, Pix/Efi real, webhook, API externa ou push foi usado.

## Estado do Bloco 50

- Checkpoint local do Bloco 49 criado em `037f9b22`, sem remote e sem push.
- Preflight local de homologacao criado com `scripts/local/validar-preflight-homologacao-local.ps1`.
- O preflight classifica itens como pronto localmente, pendente antes de homologacao e bloqueante antes de producao.
- `APP_ENV`, exemplos de ambiente, cookies, CORS, CSRF, storage, midia, Pix/Efi, importador, SEO, backup/rollback, monitoramento, Bloco 29 e Pro ficam documentados como gates objetivos.
- O resultado local esperado e `OK_PREFLIGHT_HOMOLOGACAO_LOCAL`, sem autorizar staging real, homologacao, cutover ou producao.
- Nenhuma producao, VPS, dado real, restore, staging, Pix/Efi real, webhook, API externa, remote ou push foi usado.

## Estado do Bloco 51

- Checkpoint local do Bloco 50 criado em `8757e48a`, sem remote e sem push.
- Contrato documental de homologacao criado sem deploy.
- Contratos versionados: ambiente, secrets externos, CORS/cookies/CSRF, rollback e monitoramento.
- `APP_ENV=homologacao`, dominio proprio, banco isolado, secrets fora do Git, cookies seguros, CSRF nao-local, storage pendente, logs/auditoria JSON e rollback ficam definidos como requisitos.
- Staging real, dados reais/sanitizados, restore, Pix/Efi real, webhook, API externa, remote e push continuam proibidos sem bloco futuro autorizado.

## Estado do Bloco 52

- Checkpoint local do Bloco 51 criado em `eacecaa2`, sem remote e sem push.
- Contrato tecnico storage/upload/CDN criado sem upload real.
- O contrato separa midia publica, stories, midia pendente, midia rejeitada e documento privado.
- URL publica fica permitida apenas para midia aprovada; URL privada, storage key, provider, bucket, hash interno e credenciais continuam fora de DTO publico.
- Limite gratuito de 2 fotos, Premium/fotos extras e expiracao conjunta de beneficios ficam documentados como regras contratuais.
- Storage/CDN/upload real, antivirus real, cache/invalidation e rollback real permanecem pendentes de homologacao futura e revisao Pro quando aplicavel.
- Nenhuma producao, VPS, dado real, upload real, storage real, CDN real, R2/S3 real, API externa, Pix/Efi real, webhook, remote ou push foi usado.

## Estado do Bloco 53

- Checkpoint local do Bloco 52 criado em `d528f7ed`, sem remote e sem push.
- Contratos criticos de homologacao/cutover consolidados documentalmente.
- Contratos versionados: importacao real/dry-run, SEO real/cutover, financeiro/Pix/Efi/webhooks, backup/rollback, monitoramento operacional e Go/No-Go.
- Go/No-Go passa a exigir criterios objetivos para liberar homologacao, bloquear homologacao, liberar cutover e acionar rollback.
- Nenhuma producao, VPS, dado real, importacao real, restore, staging real, Pix/Efi real, webhook real, API externa real, remote ou push foi usado.

## Estado do Bloco 54

- Checkpoint local do Bloco 53 criado em `0fb2b771`, sem remote e sem push.
- Dossie final do ciclo local/sintetico criado.
- Status consolidado: `MVP_LOCAL_SINTETICO_VALIDADO`.
- Dossies versionados: final do ciclo local, revisao Pro/humana e proximos passos de homologacao.
- O status consolidado nao autoriza homologacao real, cutover, producao, dados reais/sanitizados, restore, Pix/Efi real, webhook real, importador real, storage/CDN real, API externa real ou push.
- Proxima etapa segura: revisao Pro/humana dos dossies e contratos.

## Estado do Bloco 55

- Checkpoint local do Bloco 54 criado em `30be1db7`, sem remote e sem push.
- Ciclo local/sintetico fechado.
- Status final: `MVP_LOCAL_SINTETICO_VALIDADO`.
- Freeze final: `CICLO_LOCAL_SINTETICO_FECHADO`.
- Proximo passo recomendado: revisao Pro/humana do dossie final.
- Proibido seguir para homologacao real sem decisao expressa.
- Bloco 29 / restore completo segue pendente.
- Quarentena sem `POST_DATA` segue proibida para staging final.
- Producao/cutover seguem bloqueados.
- Nenhuma producao, VPS, dado real, importacao real, restore, staging real, Pix/Efi real, webhook real, API externa real, remote ou push foi usado.

## Estado do Bloco 56

- Checkpoint final do Bloco 55 criado em `4323df90`, sem remote e sem push.
- Login admin recebeu lockout local por login hash e IP hash: 5 falhas em 15 minutos bloqueiam por 15 minutos.
- Login inexistente conta para lockout e o erro permanece generico.
- Login admin bem-sucedido troca o ID da sessao antes de salvar o `SecurityContext`.
- Gitleaks historico completo executado com 35 commits escaneados e sem leaks.
- Protocolo VPS restore integral criado apenas como documento.
- `site.zip` manual registrado como artefato confidencial excepcional, nao pacote oficial.
- Nenhuma producao, VPS, dado real novo, importacao real, restore, staging real, Pix/Efi real, webhook real, API externa real, remote ou push foi usado.

## Estado do Bloco 58

- Checkpoint local do Bloco 57 criado em `a6487f5b`, sem remote e sem push.
- `C:\clone\topsdojob-frontend` foi identificado como frontend visual de producao e usado somente em leitura.
- A raiz `C:\clone` nao respondeu como repositorio Git valido, mas contem o subprojeto frontend valido.
- O clone possuia alteracoes locais preexistentes e elas foram apenas registradas, sem escrita.
- Primeira adaptacao visual de baixo risco aplicada na V3: tokens globais, container, CTAs e header publico.
- Gate `BLOQUEADO_PARIDADE_VISUAL_PRODUCAO` permanece aberto para detalhe de anuncio, wizard `/anunciar`, admin, mobile final e revisao humana/Pro.
- Nenhuma producao, VPS, dado real, backend, banco, migration, auth/RBAC, importacao real, restore, staging real, Pix/Efi real, pagamento, upload real, CDN/storage real, webhook, API externa, remote ou push foi usado.

## Estado do Bloco 59

- Checkpoint local do Bloco 58 criado em `46a1ff7`, sem remote e sem push.
- `C:\clone\topsdojob-frontend` segue como fonte visual somente leitura e permanece com as mesmas 3 alteracoes locais preexistentes registradas no Bloco 58.
- Cards, grid, placeholders e detalhe publico da V3 foram ajustados visualmente sem copiar fetches, auth, upload, stores, pagamento ou regras da producao.
- Gate `BLOQUEADO_PARIDADE_VISUAL_PRODUCAO` permanece aberto para wizard `/anunciar`, footer/admin quando aplicavel, comparacao visual final e revisao humana/Pro.
- Nenhuma producao, VPS, dado real, backend, banco, migration, auth/RBAC, importacao real, restore, staging real, Pix/Efi real, pagamento, upload real, CDN/storage real, webhook, API externa, remote ou push foi usado.

## Estado do Bloco 60

- Checkpoint local do Bloco 59 criado em `3111afc`, sem remote e sem push.
- Header publico passa a usar a logo real local em `/logo.webp`, derivada do clone somente leitura.
- Wizard `/anunciar` recebeu aproximacao visual de card, progresso, botoes, campos e espacamentos.
- Gate `BLOQUEADO_PARIDADE_VISUAL_PRODUCAO` permanece aberto para footer/admin quando aplicavel, comparacao visual final e revisao humana/Pro.
- Nenhuma producao, VPS, dado real, backend, banco, migration, auth/RBAC, importacao real, restore, staging real, Pix/Efi real, pagamento, upload real, CDN/storage real, webhook, API externa, remote ou push foi usado.

## Complementacao deploy HML do Bloco 60

- Configuracao de GitHub Actions + SSH para `v3.esle.cloud` criada localmente.
- Usuario esperado: `topsv3`.
- Deploy path esperado: `/opt/topsv3/app/current`.
- Secrets externos esperados: `/opt/topsv3/secrets/hml.env`.
- Nginx HML bloqueia indexacao com `X-Robots-Tag` e `Disallow: /`.
- Complemento corretivo aplicado: backend HML usa `SPRING_PROFILES_ACTIVE=homologacao`, com profile suportado por `backend/src/main/resources/application-homologacao.yml`.
- Backend compile/test validado pela toolchain local em `scripts/local/validar-build-local.ps1`.
- Endpoint real de health confirmado: `GET /api/health`.
- Workflow reforcado para excluir explicitamente `topsv3-auditoria-local`, `logs-brutos-nao-versionar`, `**/*.dump`, `**/*.backup`, `**/*.log`, `**/.env`, `**/*.pem`, `**/*.key` e `**/*.crt`.
- Gate aberto: `PENDENTE_HTTPS_HML_ANTES_DO_TESTE_PUBLICO`.
- Workflow nao foi executado; sem deploy, sem push, sem acesso a VPS, sem producao e sem dados reais.

## Estado do Bloco 61

- HML registrada como online em `https://v3.esle.cloud`.
- Nginx versionado atualizado para refletir HTTP 80 redirecionando para HTTPS 443.
- Certificados HML esperados em `/etc/letsencrypt/live/v3.esle.cloud/`.
- `X-Robots-Tag: noindex, nofollow, noarchive` validado.
- `/robots.txt` validado com `Disallow: /`.
- `GET /api/health` validado com status `UP`.
- O gate `PENDENTE_HTTPS_HML_ANTES_DO_TESTE_PUBLICO` fica fechado para HML navegavel, mas homologacao/cutover/producao seguem bloqueados pelos demais gates.
- Nenhuma producao, dado real, Pix/Efi real, webhook real, upload real, push ou fase posterior foi usado neste bloco.

## Proibicoes ate novo bloco autorizado

- nao iniciar fase posterior implicitamente;
- nao criar migration nova;
- nao alterar SQL de schema;
- nao usar producao;
- nao usar banco de producao;
- nao usar API externa;
- nao usar Efi real;
- nao fazer deploy;
- nao configurar remote;
- nao executar push;
- nao executar commit sem autorizacao expressa.
