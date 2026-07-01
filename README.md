# Tops do Job V3

Este repositório contém o trabalho inicial da V3 do Tops do Job.

## Estado atual

Fase atual: **Fase 2G - dossie de transicao para revisao Pro**.

Existe skeleton local de backend Spring Boot e frontend Next.js, contrato inicial, documentação físico-conceitual do banco V3, migrations Flyway/PostgreSQL geradas para auditoria, padrões transversais de API/logs/auditoria, implementação local mínima de request id/erros/health, skeleton das rotas públicas críticas com SEO local seguro, estratégia GEO/AEO/LLM Visibility, snapshot sanitizado de textos públicos atuais da produção, shell administrativo local estrutural, diretriz de preservação do visual atual e validação local das rotas públicas/SEO skeleton. As migrations da Fase 1D estão com status `AGUARDANDO_REVISAO_PRO`: não foram aplicadas, Flyway não foi executado e nenhum banco foi acessado. Ainda não existe dado real, busca real, anúncio real, conteúdo público final, fluxo Pix real, autenticação completa, painel admin funcional, frontend público real ou integração externa.

A Fase 1C.6B cria apenas o shell administrativo local e o mapa estrutural dos módulos do admin. Ele não é funcional, não autentica usuário, não executa ação real, não consulta backend, não usa dado real e mantém todas as páginas admin em `noindex`.

A Fase 1C.7 documenta que a V3 deve preservar o visual atual do Tops do Job. Nenhum redesign foi feito; os skeletons locais continuam temporários e não representam layout final. A fonte visual atual do legado permanece como `PENDENTE_FONTE_VISUAL_ATUAL` até que prints, arquivos ou referências confiáveis sejam fornecidos.

A Fase 1C.8 cria validação local estática para proteger `/anuncios/[slug]`, impedir rotas alternativas de anúncio, verificar páginas locais, robots, sitemap, canonical local, admin `noindex` e ausência de dados reais, imagens reais, conteúdo explícito, chamadas externas, SQL e migrations.

A Fase 1D cria as migrations SQL versionadas de auditoria em `backend/src/main/resources/db/migration`. Elas cobrem o schema inicial previsto, mas permanecem bloqueadas para aplicação até revisão Pro. A Fase 1D.4 executou validação real em PostgreSQL local descartável usando imagem local `postgres:16` e fallback `SQL_ORDENADO_PSQL`, sem pull, download, dados reais ou banco persistente. As migrations `V001` a `V017` aplicaram sem erro SQL. A Fase 1D.5 consolidou o pacote final de auditoria, sem criar migration nova e sem alterar SQL de schema.

A Fase 2A cria somente a estrutura local do importador saneador em Java puro, com enums, DTOs/records, builder de relatório em memória e contratos documentais. Nenhum dump foi lido, nenhum dado real foi usado, nenhum banco foi acessado e nenhuma importação real foi iniciada.

A Fase 2B cria contratos locais para o pacote de entrada da futura importação saneadora. Ela define tipos de arquivo esperados, DTOs de descritor, validador estrutural em memória e exemplo sanitizado. Nenhum dump foi lido, nenhum arquivo real de entrada foi aberto, nenhum banco foi acessado e nenhuma importação real foi iniciada.

A Fase 2C cria o dicionário estrutural de campos esperados por tipo de arquivo do pacote de importação. Ela define tipos de campo, sensibilidade, obrigatoriedade, catálogo por arquivo e validador estrutural em memória. Nenhum dump foi lido, nenhum arquivo real de entrada foi aberto, nenhum banco foi acessado e nenhuma importação real foi iniciada.

A Fase 2D cria regras estruturais de saneamento e transformação legado -> V3. Ela define tipos de regra, severidades, escopos, DTOs de regra, resultado estrutural, catálogo por escopo e validador em memória. Nenhum dump foi lido, nenhum arquivo real de entrada foi aberto, nenhum banco foi acessado e nenhuma transformação/importação real foi iniciada.

A Fase 2E cria plano de execucao e contratos de dry-run estrutural para a futura importacao saneadora. Ela define etapas, status, criticidades, dependencias, catalogo de ordem segura e validador em memoria. Nenhum dump foi lido, nenhum arquivo real de entrada foi aberto, nenhum banco foi acessado e nenhuma importacao real foi iniciada.

A Fase 2F cria o gate operacional para fonte real autorizada antes de qualquer importacao. Ela documenta regras de recebimento futuro, reforca protecoes contra versionamento acidental de dados reais e cria validacao local que, sem parametro, confirma que nenhuma fonte real foi usada nesta fase. Nenhum dump foi lido, nenhum arquivo real de entrada foi aberto, nenhum banco foi acessado e nenhuma importacao real foi iniciada.

A Fase 2G cria um dossie documental de transicao para revisao Pro e futura fonte real autorizada. Ela consolida o estado da Fase 1D, das Fases 2A a 2F, gates bloqueantes, riscos residuais e proximas fases por dependencia. Nenhuma nova camada de importador foi criada, nenhum dump foi lido, nenhum arquivo real de entrada foi aberto e nenhuma importacao real foi iniciada.

## Stack planejada

- Backend: Java 17 LTS com Spring Boot.
- Banco: PostgreSQL.
- Frontend: Next.js.
- Migrations: Flyway, com SQL explícito versionado por Git.
- Pagamentos: Pix Efí como provedor ativo inicial.

## Desenvolvimento local

O desenvolvimento deve acontecer integralmente em ambiente local até os gates definidos no SDD. Produção nunca deve ser usada para desenvolvimento, depuração ou teste exploratório.

Infraestrutura local preparada:

```powershell
.\scripts\local\validar-config-local.ps1
.\scripts\local\iniciar-infra-local.ps1
.\scripts\local\status-infra-local.ps1
.\scripts\local\logs-infra-local.ps1
.\scripts\local\parar-infra-local.ps1
```

Os nomes padronizados atuais são:

```powershell
.\scripts\local\validar-ambiente-local.ps1
.\scripts\local\subir-local.ps1
.\scripts\local\status-local.ps1
.\scripts\local\parar-local.ps1
.\scripts\local\limpar-local.ps1
.\scripts\local\validar-skeleton-local.ps1
.\scripts\local\validar-rotas-publicas-seo-local.ps1
.\scripts\local\validar-migrations-sql-estatico.ps1
.\scripts\local\validar-migrations-postgres-descartavel.ps1
.\scripts\local\validar-fonte-importacao-local.ps1
.\scripts\local\validar-build-local.ps1
```

Esses scripts usam `infra/local/docker-compose.local.yml` e exigem Docker já disponível na máquina. Esta fase não instala ferramentas. Os volumes locais ficam sob `storage-local/`, que é ignorado pelo Git.

Validacao de build local do Bloco 4.2:

- backend exige Java 17 LTS e Maven ou Maven Wrapper ja disponivel sem download;
- frontend exige Node/npm e `frontend/node_modules` ja existente;
- os scripts nao executam `npm install`, `npm ci`, download de wrapper, instalacao de Maven, banco, Flyway ou rede externa;
- enquanto o backend build nao estiver validado, services/controllers/endpoints de dominio continuam bloqueados.

Gate de toolchain do Bloco 4.3:

```powershell
.\scripts\local\diagnosticar-toolchain-local.ps1
```

O diagnostico apenas lista Java, Maven, Node/npm, `node_modules` e gerenciadores como winget/choco/scoop. Ele nao instala ferramentas, nao baixa dependencias, nao altera PATH e nao cria Maven Wrapper.

Backend e frontend possuem camada transversal local mínima:

- backend: `backend/src/main/java/br/com/topsdojob/v3/platform`;
- frontend: `frontend/src/lib/api` e `frontend/src/lib/config`;
- health local: `GET /api/health`, `GET /api/health/readiness`, `GET /api/health/liveness` e página `frontend/src/app/health/page.tsx`.

Essa camada não conecta banco, não executa Flyway, não acessa produção e não chama integração externa.

Rotas públicas skeleton preparadas na Fase 1C.4:

- `frontend/src/app/anuncios/[slug]/page.tsx`;
- `frontend/src/app/acompanhantes/[uf]/[cidade]/page.tsx`;
- `frontend/src/app/acompanhantes/[uf]/[cidade]/[bairro]/page.tsx`;
- `frontend/src/app/robots.ts`;
- `frontend/src/app/sitemap.ts`.

SEO real, conteúdo público final, busca real e JSON-LD final ficam para fases futuras. Em ambiente local, canonical de produção permanece proibido.

Páginas institucionais skeleton da Fase 1C.5:

- `frontend/src/app/sobre/page.tsx`;
- `frontend/src/app/como-funciona/page.tsx`;
- `frontend/src/app/seguranca/page.tsx`;
- `frontend/src/app/anunciar/page.tsx`;
- `frontend/src/app/perguntas-frequentes/page.tsx`;
- `frontend/public/llms.txt`.

GEO/AEO/LLM Visibility é atividade transversal. IA externa não pode ser controlada; o objetivo é aumentar clareza, autoridade e citabilidade sem spam e com revisão humana obrigatória para conteúdo público final.

Captura pública sanitizada da Fase 1C.6A:

- `docs/v3/42-captura-conteudo-publico-producao.md`;
- `docs/v3/43-inventario-textos-publicos-producao.md`;
- `docs/v3/44-checklist-captura-publica-producao.md`;
- `docs/v3/conteudo-publico-capturado/`.

Shell admin local da Fase 1C.6B:

- `frontend/src/app/admin`;
- `frontend/src/modules/admin/shell`;
- `docs/v3/45-admin-shell-local.md`;
- `docs/v3/46-mapa-modulos-admin.md`;
- `docs/v3/47-checklist-admin-shell-local.md`.

Preservação visual da Fase 1C.7:

- `docs/v3/48-preservacao-visual-atual.md`;
- `docs/v3/49-inventario-visual-atual.md`;
- `docs/v3/50-checklist-preservacao-visual.md`.

Validação local de rotas e SEO da Fase 1C.8:

- `scripts/local/validar-rotas-publicas-seo-local.ps1`;
- `docs/v3/51-validacao-rotas-publicas-seo-local.md`;
- `docs/v3/52-checklist-validacao-rotas-seo.md`.

Migrations de auditoria da Fase 1D:

- `backend/src/main/resources/db/migration/V001__extensoes_postgresql.sql` a `V017__constraints_finais.sql`;
- `scripts/local/validar-migrations-sql-estatico.ps1`;
- `scripts/local/validar-migrations-postgres-descartavel.ps1`;
- `docs/v3/58-fase-1d-migrations-flyway.md`;
- `docs/v3/59-matriz-tabelas-migrations.md`;
- `docs/v3/60-checklist-auditoria-pro-migrations.md`;
- `docs/v3/61-pendencias-e-decisoes-schema-1d.md`;
- `docs/v3/63-validacao-postgres-descartavel-1d3.md`;
- `docs/v3/64-checklist-validacao-postgres-1d3.md`;
- `docs/v3/65-execucao-validacao-postgres-1d4.md`;
- `docs/v3/66-checklist-validacao-real-migrations.md`;
- `docs/v3/67-relatorio-final-fase-1d.md`;
- `docs/v3/68-checklist-final-fase-1d.md`.

O validador PostgreSQL descartável retorna `0` somente para `OK_POSTGRES_DESCARTAVEL`, `1` para falha real de migration/SQL e `2` para pendência operacional. O caso validado nesta fase continua retornando `0` com `SQL_ORDENADO_PSQL`.

Estrutura local do importador saneador da Fase 2A:

- `backend/src/main/java/br/com/topsdojob/v3/importacao`;
- `backend/src/test/java/br/com/topsdojob/v3/importacao/ImportacaoEstruturaTest.java`;
- `docs/v3/69-fase-2a-importador-saneador-estrutura.md`;
- `docs/v3/70-contratos-relatorios-importacao.md`;
- `docs/v3/71-checklist-fase-2a-importador.md`.

Contratos do pacote de entrada da Fase 2B:

- `backend/src/main/java/br/com/topsdojob/v3/importacao/pacote`;
- `backend/src/test/java/br/com/topsdojob/v3/importacao/pacote/PacoteEntradaImportacaoTest.java`;
- `docs/v3/72-pacote-entrada-importacao.md`;
- `docs/v3/73-validacao-pacote-importacao.md`;
- `docs/v3/74-checklist-fase-2b-pacote-importacao.md`;
- `docs/v3/exemplos/importacao/pacote-entrada-exemplo-sanitizado.json`.

Dicionário estrutural de campos da Fase 2C:

- `backend/src/main/java/br/com/topsdojob/v3/importacao/dicionario`;
- `backend/src/test/java/br/com/topsdojob/v3/importacao/dicionario/DicionarioImportacaoTest.java`;
- `docs/v3/75-dicionario-campos-importacao.md`;
- `docs/v3/76-regras-sensibilidade-campos-importacao.md`;
- `docs/v3/77-checklist-fase-2c-dicionario-importacao.md`.

Regras estruturais de saneamento da Fase 2D:

- `backend/src/main/java/br/com/topsdojob/v3/importacao/saneamento`;
- `backend/src/test/java/br/com/topsdojob/v3/importacao/saneamento/SaneamentoImportacaoTest.java`;
- `docs/v3/78-regras-saneamento-importacao.md`;
- `docs/v3/79-catalogo-transformacao-legado-v3.md`;
- `docs/v3/80-checklist-fase-2d-saneamento.md`.

Plano de execucao e dry-run estrutural da Fase 2E:

- `backend/src/main/java/br/com/topsdojob/v3/importacao/plano`;
- `backend/src/test/java/br/com/topsdojob/v3/importacao/plano/PlanoExecucaoImportacaoTest.java`;
- `docs/v3/81-plano-execucao-importacao.md`;
- `docs/v3/82-dry-run-estrutural-importacao.md`;
- `docs/v3/83-checklist-fase-2e-plano-dryrun.md`.

Gate de fonte real autorizada da Fase 2F:

- `scripts/local/validar-fonte-importacao-local.ps1`;
- `docs/v3/84-gate-fonte-real-autorizada-importacao.md`;
- `docs/v3/85-runbook-recebimento-pacote-real.md`;
- `docs/v3/86-checklist-fase-2f-fonte-real.md`;
- `docs/v3/exemplos/importacao/fonte-real-autorizada-exemplo-sanitizado.json`.

Dossie de transicao da Fase 2G:

- `docs/v3/87-dossie-transicao-revisao-pro.md`;
- `docs/v3/88-mapa-gates-proximas-fases.md`;
- `docs/v3/89-checklist-fase-2g-transicao.md`.

Serviços locais previstos:

- PostgreSQL local na porta `54329`;
- MinIO local em `9000` e console em `9001`;
- Mailpit SMTP em `1025` e interface HTTP em `8025`.

## Segurança do repositório

É proibido versionar secrets, credenciais, certificados, dumps, backups, uploads, logs, dados reais ou credenciais Efí.

Scripts locais de verificação:

```powershell
.\scripts\security\verificar-codificacao.ps1
.\scripts\security\verificar-arquivos-proibidos.ps1
.\scripts\security\verificar-segredos.ps1
```

Os scripts verificam blobs staged no Git. Achado de segurança retorna exit code `1`; erro operacional retorna exit code `2` e bloqueia commit local opcional e geração de pacote.

Arquivos compactados ou archives (`.zip`, `.7z`, `.rar`, `.tar`, `.tgz`, `.gz`, `.bz2`, `.xz`, `.iso`) são bloqueados por padrão porque podem encapsular secrets, certificados ou dados reais.

O fallback local de secrets é sempre executado. Quando `gitleaks` estiver instalado, ele roda como camada adicional com `gitleaks git --pre-commit --redact --staged --no-banner`; se não estiver instalado, o status de Gitleaks fica `PENDENTE` e o fallback continua obrigatório.

Também são bloqueados:

- UTF-16, UTF-32, NUL e controles inválidos em arquivos textuais;
- symlinks staged (`120000`) e gitlinks/submodules (`160000`);
- extensões perigosas compostas com `.example`, como `.p12.example`, `.pem.example`, `.zip.example`, `.7z.example`, `.dump.example` e `.db.example`;
- entradas ZIP com traversal, caminho absoluto, UNC, separador misto ou duplicidade apenas por diferença de caixa.

## Codificação

- Arquivos `.ps1`: UTF-8 com BOM para compatibilidade com Windows PowerShell 5.1.
- Arquivos `.md`, `.sh`, `.toml`, `.json`, `.yml` e `.yaml`: UTF-8 sem BOM.
- Arquivos `.csv` gerados para consumo no Windows: UTF-8 com BOM.

A geração de CSV usa escrita explícita com `System.Text.UTF8Encoding($true)`, sem depender da versão do PowerShell.

## Política de commit e remoto

Este repositório permanece 100% local nesta fase. Produção continua intocada. Nenhum remote deve ser configurado, nenhum push deve ser executado e nenhuma publicação deve ocorrer sem autorização explícita futura.

Commit local é opcional e não é executado nesta fase. A ausência de `user.name` ou `user.email` local não bloqueia as fases locais; enquanto não houver commit, o índice Git staged e os ZIPs validados são os checkpoints formais da entrega.

Remote, push, publicação, deploy, acesso a VPS, produção, banco de produção ou Efí real continuam proibidos.

## Pacotes de revisão

Ao final de cada execução futura do Codex, deve ser criado um ZIP na Área de Trabalho contendo somente os arquivos criados ou modificados naquela execução, com manifesto SHA-256, resumo da entrega e relatório de validações. A seleção usa comparação do hash do índice Git contra o inventário inicial, não apenas `git diff`.

O inventário inicial deve ser criado antes de qualquer alteração com `scripts/entrega/criar-inventario-inicial.ps1`, fora do repositório, em CSV UTF-8 com BOM. O gerador valida o inventário real antes de usar, bloqueia qualquer alteração fora do índice Git, reabre o ZIP, extrai em diretório temporário, importa o `MANIFESTO-ARQUIVOS.csv` real armazenado no pacote, exige UTF-8 com BOM, compara colunas, caminhos, tipos, tamanhos e hashes com o manifesto esperado, recalcula o SHA-256 dos arquivos extraídos e escaneia os arquivos de controle antes e depois da compactação.

## Documentação SDD

Os documentos de Specification-Driven Development ficam em:

```text
docs/v3
```

O plano de fases está em `docs/v3/11-plano-execucao-fases.md`.

## Observação

Os comandos de backend/frontend dependem de autorização futura para instalar ou baixar dependências. Nesta execução não houve instalação nem download.

## Bloco 3 - backend de domínio base

O Bloco 3 cria uma base local de domínio em Java puro em `backend/src/main/java/br/com/topsdojob/v3/domain`, espelhando estruturalmente as migrations `V001` a `V017` sem acessar banco, sem criar migration, sem alterar SQL e sem iniciar importação real.

Como o `pom.xml` ainda não possui JPA/Spring Data JPA local, não foram criadas entidades JPA anotadas nem repositories. As pendências formais são `PENDENTE_JPA_JAKARTA_PERSISTENCE` e `PENDENTE_REPOSITORIES_SPRING_DATA_JPA`.

## Bloco 4 - persistência JPA base

O Bloco 4 adiciona a camada `backend/src/main/java/br/com/topsdojob/v3/persistence` com entidades JPA e repositories mínimos. Foram adicionadas apenas dependências oficiais mínimas de Spring Data JPA e driver PostgreSQL runtime.

Esta camada não cria controller, endpoint, service de negócio, importador real, migration nova, SQL novo ou acesso a banco. Build Maven local permanece `PENDENTE_BUILD_MAVEN_LOCAL` quando não houver Maven/wrapper e dependências em cache sem download.

## Bloco 4.4 - build local validado

O Bloco 4.4 regularizou a toolchain local com autorizacao expressa e validou build backend/frontend.

Resultado atual:

- Java 17 LTS localizado e usado apenas por processo;
- Maven 3.9.9 localizado em pasta local do usuario;
- `npm install` executado no frontend e `frontend/package-lock.json` criado;
- backend `mvn -q -DskipTests compile` OK;
- backend `mvn -q test` OK;
- frontend `npm run lint` OK;
- frontend `npm run build` OK;
- `typecheck` nao aplicavel porque nao existe script configurado.

`backend/target`, `frontend/node_modules` e `frontend/.next` permanecem ignorados pelo Git.

O gate de build/toolchain esta validado para o proximo bloco tecnico autorizado. Services, controllers e endpoints de dominio nao foram criados no Bloco 4.4 e continuam dependentes de autorizacao expressa futura.

## Bloco 5 - API publica minima de leitura

O Bloco 5 cria a primeira camada publica de leitura do backend:

- `GET /api/public/anuncios/{slug}`;
- `GET /api/public/acompanhantes/{uf}/{cidade}`;
- `GET /api/public/acompanhantes/{uf}/{cidade}/{bairro}`;
- `GET /api/public/seo/rota`.

Foram criados DTOs, mappers, services `readOnly`, controllers `GET` e repositories derivados simples. A API nao expoe documento privado, CPF, IP, user-agent, hash, auditoria, pagamento, credito, e-mail privado, storage key ou midia nao aprovada.

Frontend publico visual nao foi alterado. Admin funcional, autenticacao real, acoes criticas, financeiro, Pix/Efi, moderacao real, importador real, banco, migration e SQL continuam fora do escopo.

## Bloco 6 - frontend publico integrado a API local

O Bloco 6 integra o frontend publico skeleton aos endpoints locais de leitura do Bloco 5, sem redesign e sem dados reais.

Foram criados `frontend/src/lib/api/publicApi.ts` e `frontend/src/lib/api/publicTypes.ts`. As rotas `/anuncios/[slug]`, `/acompanhantes/[uf]/[cidade]` e `/acompanhantes/[uf]/[cidade]/[bairro]` passam a consumir a API local com fallback seguro quando o backend estiver indisponivel.

O backend tambem aceita `/sitemap.xml` e `/robots.txt` no endpoint `GET /api/public/seo/rota`, mantendo bloqueadas rotas alternativas como `/perfil`, `/ads`, `/anuncio` e `/acompanhante`.

A paginacao publica foi corrigida para filtrar anuncios `PUBLICADO`/`APROVADO` antes da pagina final. `MidiaPublicaDto.urlPublica` segue pendente por seguranca com `PENDENTE_URL_PUBLICA_MIDIA_CDN`.

Nao houve nova paleta, nova tipografia, imagem real, WhatsApp publico, storage key, hash, bucket, admin funcional, autenticacao real, Pix/Efi, financeiro, migration, SQL, API externa, producao, VPS, remote, push ou commit.

## Bloco 7 - e2e local descartavel

O Bloco 7 validou o fluxo local completo com PostgreSQL descartavel:

- imagem local usada: `postgres:16`;
- migrations V001-V017 aplicadas via `psql` ordenado;
- dados sinteticos minimos aplicados;
- backend local iniciado em profile `local`;
- smoke HTTP da API publica executado;
- frontend lint/build validado;
- backend compile/test validado;
- container e rede removidos;
- nenhum volume persistente criado.

O pacote final deste bloco usa metadados de execucao para preencher corretamente a secao de testes no `RESUMO-ENTREGA.md`.

Nao houve producao, VPS, banco de producao, API externa, dados reais, dump, migration nova, SQL de schema, WhatsApp publico, documento privado, storage key/hash/bucket, remote, push ou commit.

## Bloco 8 - metricas publicas locais e WhatsApp

O Bloco 8 adiciona endpoints publicos locais:

- `POST /api/public/anuncios/{slug}/visualizacao`;
- `POST /api/public/anuncios/{slug}/clique-whatsapp`.

Os eventos usam hash tecnico para IP/User-Agent/referer e registram dados apenas em banco local descartavel. A politica backend de contato libera WhatsApp para anuncio `LIVRE` sem idade, ou `BLOQUEADO` somente apos confirmacao de idade valida, sempre exigindo `PUBLICADO`, `APROVADO`, sem `removido_em` e contato valido.

Stories seguem bloqueados sem confirmacao de idade usando motivo `IDADE_NAO_CONFIRMADA`. Quando a idade esta confirmada e a midia publica/CDN ainda nao existe, a pendencia e `PENDENTE_URL_PUBLICA_MIDIA_CDN`.

## Bloco 9 - confirmacao de idade local e stories

O Bloco 9 adiciona `POST /api/public/idade/confirmar`, `GET /api/public/idade/status` e `GET /api/public/anuncios/{slug}/stories`.

A confirmacao usa declaracao/data de nascimento local, cookie HttpOnly assinado, `SameSite=Lax`, sem CPF, sem documento, sem conta de usuario e sem localStorage/sessionStorage. Fora de `local`, salt/hash de metricas e segredo de idade falham se estiverem ausentes ou ficticios.

Conteudo `BLOQUEADO` nao libera sem idade confirmada, mas pode ser liberado pelo backend apos confirmacao valida. Stories retornam apenas metadata segura enquanto `PENDENTE_URL_PUBLICA_MIDIA_CDN`.

## Bloco 10 - UX local de age gate e CORS/cookie

O Bloco 10 corrige a UX local de `/anuncios/[slug]`: quando o detalhe inicial nao retorna por falta de idade confirmada, a pagina ainda renderiza confirmacao local de idade, emite cookie HttpOnly pelo backend e reconsulta o detalhe com `credentials: include`.

O frontend nao decide classificacao nem liberacao de conteudo. Ele apenas confirma idade localmente e reconsulta o backend. CORS com credentials fica limitado a `APP_ENV=local` e origens localhost configuradas; fora de local fica fechado.

Nao houve limite diario comercial, admin funcional, autenticacao real, Pix/Efi, financeiro, moderacao real, importador real, API externa, producao, VPS, remote, push ou commit.
