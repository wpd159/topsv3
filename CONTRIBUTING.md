# Contribuição

## Regras gerais

- Não commitar secrets, credenciais, dumps, backups, uploads, logs ou dados reais.
- Não copiar arquivos do legado para este repositório sem decisão formal.
- Não criar migrations sem revisão.
- Migrations usam Flyway, SQL explícito e versionamento por Git.
- Não usar migration automática por ORM, executor próprio ou alteração manual de schema em produção.
- Não alterar regras financeiras, autenticação, SEO ou backup sem revisão.
- Não usar produção como ambiente de desenvolvimento.
- Fase 1C é documentação e desenho físico-conceitual do banco; não cria migrations, SQL, schema executável, entidades JPA de domínio, repositories ou services.
- Fase 1C.1 é preparação da Fase 1D; pode documentar nomes planejados de migrations, mas não cria arquivos SQL.
- Fase 1C.2 documenta padrões transversais de API, logs, auditoria e OpenAPI; não implementa regra de negócio.
- Fase 1C.3 implementa somente infraestrutura transversal mínima local em backend/frontend; não cria banco, migration, SQL, entidade JPA de domínio, repository, service de negócio, autenticação real, Pix/Efí ou integração externa.
- Fase 1C.4 implementa somente skeleton local das rotas públicas preservadas e SEO local seguro; não cria busca real, anúncio real, conteúdo adulto real, canonical de produção local, API externa, banco, migration ou SQL.
- Fase 1C.5 documenta GEO/AEO/LLM Visibility e cria skeleton institucional neutro; não acessa IA externa, não cria integração OpenAI, scraping, spam, conteúdo explícito, anúncio real, dados reais, backend de domínio, banco, migration ou SQL.
- Fase 1C.6A permite apenas captura pública somente leitura de textos SEO atuais, com GET, rate limit e sanitização; não acessa admin, login, API privada, banco, VPS, Efí, IA externa, imagens, vídeos, dados sensíveis, HTML bruto completo, migration ou SQL.
- Fase 1C.6B cria apenas shell administrativo local e mapa estrutural; não cria autenticação funcional, RBAC real, ação administrativa, backend de domínio, dado real, integração externa, banco, migration ou SQL.
- Fase 1C.7 documenta preservação do visual atual; não cria redesign, nova identidade visual, nova paleta, nova tipografia, layout final, dado real, conteúdo explícito, banco, migration ou SQL.
- Fase 1C.8 cria validação local estática de rotas públicas e SEO skeleton; não executa crawler real, produção, banco, API externa, build com download, migration ou SQL.
- Fase 1D gera migrations Flyway/PostgreSQL apenas para auditoria, com status `AGUARDANDO_REVISAO_PRO`; não aplicar Flyway, não acessar banco, não iniciar fase dependente e não tratar o schema como aprovado antes da revisão Pro.
- Fase 1D.3 prepara validação Flyway/PostgreSQL apenas em banco local descartável; se Docker daemon, imagem PostgreSQL ou Flyway não existirem localmente, registrar `PENDENTE_VALIDACAO_POSTGRES_LOCAL` sem instalar, baixar, puxar imagem ou acessar ambiente externo.
- Fase 1D.4 permite iniciar Docker Desktop local já instalado e validar migrations em PostgreSQL descartável. Se Flyway não existir localmente, pode ser usado fallback `SQL_ORDENADO_PSQL`; isso não aprova o schema e não autoriza banco persistente, importador, backend de domínio ou fase dependente.
- Fase 1D.5 fecha o pacote final de auditoria das migrations; não cria migration nova, não altera SQL de schema e mantém o status `AGUARDANDO_REVISAO_PRO`.
- Fase 2A cria apenas estrutura local do importador saneador: Java puro, DTOs/records, enums e builder em memória. Não ler dump, não acessar banco, não importar dados, não criar migration, não alterar SQL, não criar entidade JPA, repository, controller ou endpoint funcional.
- Fase 2B cria apenas contratos locais do pacote de entrada da importação: Java puro, DTOs/records, enums, validador estrutural em memória e exemplo sanitizado. Não ler dump, não abrir arquivo real de entrada, não acessar banco, não importar dados, não executar ETL, não criar migration, não alterar SQL, não criar entidade JPA, repository, controller ou endpoint funcional.
- Fase 2C cria apenas dicionário estrutural de campos do pacote de importação: Java puro, enums, DTOs/records, catálogo e validador em memória. Não ler dump, não abrir arquivo real de entrada, não acessar banco, não importar dados, não executar ETL, não criar migration, não alterar SQL, não criar entidade JPA, repository, controller ou endpoint funcional.
- Fase 2D cria apenas regras estruturais de saneamento e transformação legado -> V3: Java puro, enums, DTOs/records, catálogo e validador em memória. Não ler dump, não abrir arquivo real de entrada, não acessar banco, não transformar dados, não importar dados, não executar ETL, não criar migration, não alterar SQL, não criar entidade JPA, repository, controller ou endpoint funcional.
- Fase 2E cria apenas plano de execucao e dry-run estrutural da importacao: Java puro, enums, DTOs/records, catalogo de etapas e validador em memoria. Nao ler dump, nao abrir arquivo real de entrada, nao acessar banco, nao transformar dados, nao importar dados, nao executar ETL, nao criar migration, nao alterar SQL, nao criar entidade JPA, repository, service, controller ou endpoint funcional.
- Fase 2F cria apenas gate operacional de fonte real autorizada e protecao contra vazamento. Nao usar dump, nao usar dado real, nao abrir arquivo real de entrada, nao acessar banco, nao importar dados, nao executar ETL, nao criar migration, nao alterar SQL, nao criar entidade JPA, repository, service, controller ou endpoint funcional.
- Fase 2G cria apenas dossie documental de transicao para revisao Pro e fonte real futura. Nao criar nova camada de importador, nao criar codigo Java, nao criar script, nao usar dump, nao usar dado real, nao abrir arquivo real de entrada, nao acessar banco, nao importar dados, nao executar ETL, nao criar migration, nao alterar SQL.
- Bloco 16 permite somente moderacao funcional local minima: decidir revisao e midia sinteticas com RBAC e auditoria sanitizada. Nao criar hard delete, upload real, e-mail real, pagamento, credito, Pix/Efi, importador real, dado real, migration, SQL de schema, producao, remote, push ou commit.
- Bloco 16.1 permite somente hardening das acoes do Bloco 16. `REPROVAR` deve exigir `motivo`, `requestIdCliente` deve ficar reservado quando nao houver suporte de schema para idempotencia real, e auditoria JSON sanitizada exige revisao Pro antes de homologacao/producao.
- Bloco 17 permite somente `SOLICITAR_AJUSTE` em revisao, remeter anuncio para revisao e outbox local pendente quando suportado pelo schema atual. Nao criar envio externo, e-mail real, WhatsApp real, hard delete, upload, pagamento, credito, Pix/Efi, importador real, dado real, migration, SQL de schema, producao, remote, push ou commit.
- Bloco 17.1 corrige `SOLICITAR_AJUSTE` para acao intermediaria: nao gravar `decisao_moderacao`, nao finalizar revisao, permitir `APROVAR`/`REPROVAR` depois, retornar `409` em duplicidade de outbox pendente e exigir motivo para remeter revisao. Nao criar nova acao administrativa, migration, SQL de schema, envio externo, remote, push ou commit.
- Bloco 18 permite somente outbox administrativo local read-only por GET, com DTO sanitizado e previa logica. Nao criar envio, reenvio, worker, scheduler, SMTP externo, WhatsApp real, API externa, metodo POST/PUT/PATCH/DELETE de outbox, migration, SQL de schema, dado real, producao, remote, push ou commit.
- Bloco 19 permite apenas `POST /api/admin/outbox/{id}/simular-processamento-local`, restrito a ADMIN, para simulacao local sem envio externo. Pode alterar `PENDENTE -> PROCESSADO` porque o status ja existe, registra auditoria sanitizada e continua proibido criar envio real, worker, scheduler, retry real, API externa, migration, SQL de schema, producao, remote, push ou commit apos o checkpoint autorizado.
- Bloco 19.1 torna a simulacao fail-closed fora de `APP_ENV=local`. Em `nao_configurado`, vazio, staging, homologacao ou producao, o backend deve retornar `403` antes de alterar status, marcar `PROCESSADO` ou auditar simulacao.
- Bloco 22 permite somente leitura/calculo local de Premium e beneficios. Endpoints devem ser GET/read-only, Premium deve ser aditivo, gratuito nao pode ganhar limite comercial de clique/contato/WhatsApp, e continuam proibidos compra real, checkout, cobranca, Pix/Efi funcional, credito real, ativacao real por dinheiro, job de expiracao, migration, SQL de schema, producao, remote, push ou commit.
- Bloco 26 permite somente funil local `Anuncie gratis`: `POST /api/public/anunciar` pode criar solicitacao sintetica nao publica e revisao aberta. Nao criar publicacao automatica, upload real, pagamento, credito, Pix/Efi, Premium obrigatorio, envio real, dado real, migration, SQL de schema, producao, remote, push ou commit depois do checkpoint local.
- Bloco 26.2 permite somente wizard progressivo de `/anunciar`, preview Premium local e documentacao/validacao SEO. Nao criar pagamento, credito, Pix/Efi, checkout, webhook, ativacao real, upload, dado real, migration, SQL de schema, producao, remote, push ou commit.
- Bloco 27.1 permite somente correcao visual de SEO publico, breadcrumbs e gate renderizado. Nao criar redesign, nova paleta, nova tipografia, elemento flutuante, scroll lock, migration, SQL, dado real, producao, remote, push ou commit.

## Antes de gerar pacote ou commitar localmente

Execute:

```powershell
.\scripts\security\verificar-codificacao.ps1
.\scripts\security\verificar-arquivos-proibidos.ps1
.\scripts\security\verificar-segredos.ps1
.\scripts\local\validar-migrations-sql-estatico.ps1
.\scripts\local\validar-migrations-postgres-descartavel.ps1
.\scripts\local\validar-seo-publico-local.ps1
.\scripts\local\validar-layout-publico-renderizado.ps1
git diff --cached --check
```

O hook de pre-commit executa os scripts automaticamente para arquivos staged.

Commit local é opcional nas fases locais e não deve ser executado quando a fase solicitar apenas staging e ZIP de revisão. A falta de `user.name` ou `user.email` local não bloqueia o avanço quando os ZIPs validados, relatórios e índice staged estiverem aprovados. Remote, push, publicação ou deploy só podem ocorrer com autorização explícita futura.

Os testes dos hooks cobrem os scripts isolados e o hook real em repositório temporário, inclusive execução a partir de subdiretório, ordem `codificação -> arquivos proibidos -> secrets`, propagação de exit code e simulação de PowerShell ausente.

Os scripts distinguem achado de segurança de erro operacional:

- `0`: validação limpa;
- `1`: risco encontrado;
- `2`: falha operacional, como erro de Git, leitura de blob ou scanner indisponível.

O scanner de segredos sempre executa o fallback local. Se `gitleaks` estiver disponível, ele roda como camada adicional; se não estiver, o resultado de Gitleaks fica `PENDENTE`, sem substituir a validação local.

Enquanto houver suporte ao Windows PowerShell 5.1, scripts `.ps1` devem ser salvos em UTF-8 com BOM. Documentos Markdown, shell scripts, TOML, JSON e YAML devem ficar em UTF-8 sem BOM. CSV de relatório destinado ao Windows deve usar UTF-8 com BOM.

Arquivos textuais em UTF-16, UTF-32, com byte NUL ou controles inválidos são bloqueados. A heurística de `?` corrompido é aplicada apenas a texto natural, para evitar falso positivo em URL, query string e código.

## Documentação

Mudanças de arquitetura devem atualizar os documentos em `docs/v3` e, quando aplicável, registrar ADR.

Use `docs/v3/SDD.md` como documento central da V3. Consulte tambem `docs/v3/SDD-indice-rastreabilidade.md`, `docs/v3/SDD-decisoes-consolidadas.md` e `docs/v3/SDD-pendencias-gates.md` antes de abrir novo bloco tecnico.

Contratos de API futuros devem seguir os padrões em `docs/v3/32-padroes-api-erros-paginacao.md` e `docs/v3/33-padroes-logs-auditoria-observabilidade.md`. Entidades JPA não devem ser expostas como DTO público.

A camada transversal implementada na Fase 1C.3 fica restrita a pacotes `platform/*`, health local e cliente API local do frontend. Novos endpoints de domínio, autenticação funcional, services de negócio, repositories e entidades JPA continuam bloqueados até fases específicas.

O skeleton público da Fase 1C.4 deve permanecer neutro: páginas públicas locais podem validar rotas e metadados `noindex`, mas não podem carregar dados reais, fotos reais, listagens reais, JSON-LD final, busca real, backend de domínio ou canonical de produção em ambiente local.

`/anuncios/[slug]` é o contrato público absoluto da página de anúncio. Rotas alternativas como `/anuncio/[id]`, `/perfil/[slug]`, `/acompanhante/[slug]` e `/ads/[slug]` são proibidas. Antes de alterar rotas públicas ou SEO local, execute `scripts/local/validar-rotas-publicas-seo-local.ps1`.

Antes de aprovar visualmente home, cidade, bairro, anuncio ou `/anunciar`, execute `scripts/local/validar-layout-publico-renderizado.ps1`. O script deve reprovar mini-coluna, H1 verticalizado, breadcrumb estreito, wizard espremido, scroll horizontal, texto tecnico publico e link para `/acompanhantes` ou `/acompanhantes/[uf]` quando essas rotas nao existirem.

Antes de revisar ou alterar migrations da Fase 1D, execute `scripts/local/validar-migrations-sql-estatico.ps1`. Esse script é textual, não acessa banco e não substitui revisão Pro, validação PostgreSQL descartável ou análise manual de arquitetura.

Quando a fase autorizar validação com banco descartável, use `scripts/local/validar-migrations-postgres-descartavel.ps1`. O script só pode usar Docker/Flyway/imagens já disponíveis localmente; não deve instalar ferramentas, baixar dependências, executar `docker pull`, criar volume persistente, acessar produção ou aplicar migrations em banco persistente.

Na Fase 1D.4, a validação real foi executada com imagem local `postgres:16` e método `SQL_ORDENADO_PSQL`, porque Flyway CLI/imagem Flyway não estavam disponíveis sem instalação. Qualquer execução futura com Flyway real continua restrita a banco local descartável.

O validador PostgreSQL descartável deve retornar `0` apenas para `OK_POSTGRES_DESCARTAVEL`, `1` para falha real de migration/SQL e `2` para pendência operacional. Retorno `2` não é aprovação; é bloqueio ambiental que deve ser resolvido ou registrado.

No módulo `br.com.topsdojob.v3.importacao`, classes das Fases 2A, 2B, 2C, 2D e 2E devem permanecer sem anotações JPA/Spring de domínio. Os subpacotes `importacao.pacote`, `importacao.dicionario`, `importacao.saneamento` e `importacao.plano` não podem receber I/O real, filesystem, datasource, repository, storage client, HTTP client ou leitura de dump. Testes podem ser criados, mas só devem ser executados quando houver executor local disponível sem download de dependências.

O script `scripts/local/validar-fonte-importacao-local.ps1` e gate preventivo da Fase 2F. Sem parametro, ele apenas confirma que nenhuma fonte real foi validada. Com diretorio em fase futura autorizada, ele deve verificar somente metadados de caminho/listagem, bloquear pasta dentro do workspace/repositorio e nunca abrir conteudo de arquivo real.

O dossie da Fase 2G e documental. Ele nao substitui revisao Pro, nao aprova schema, nao autoriza fonte real e nao desbloqueia backend de dominio.

GEO/AEO/LLM Visibility é transversal, mas não autoriza automação de conteúdo público. Páginas institucionais, FAQ, schema.org, `llms.txt` e textos úteis para IA devem ser neutros, revisados por humano e compatíveis com SEO tradicional, privacidade, segurança e compliance.

Capturas de produção, quando expressamente autorizadas, devem ser públicas, somente leitura, limitadas por rate, sanitizadas e armazenadas sem HTML bruto completo, imagens, vídeos, telefones, WhatsApp, documentos ou dados privados.

Paginas admin skeleton devem permanecer `noindex` e sem dados reais. A partir do Bloco 16, apenas botoes locais de moderacao minima podem chamar backend autenticado; acoes criticas futuras exigem autorizacao expressa, RBAC e auditoria. Telas com botoes de acao local nao devem ser chamadas de read-only.

Outbox local de moderacao e apenas registro pendente para revisao futura. Sem worker, scheduler, SMTP, WhatsApp, provedor externo ou processamento real ate autorizacao e revisao Pro.

Mudanças visuais devem preservar o visual atual do Tops do Job. Skeletons locais não são referência de layout final; qualquer nova paleta, tipografia, redesign de cards, redesign da home ou mudança estrutural de UX exige aprovação expressa e fonte visual atual inventariada.

## Pacotes de revisão

Toda execução futura do Codex deve gerar um ZIP na Área de Trabalho com apenas os arquivos criados ou modificados na execução, preservando estrutura relativa, manifesto SHA-256, resumo da entrega e relatório de validações. O ZIP não deve entrar no Git.

A seleção do pacote deve comparar o hash do índice Git do inventário inicial com o hash final do índice. O inventário deve ser criado fora do repositório com `scripts/entrega/criar-inventario-inicial.ps1`, em UTF-8 com BOM, e validado pelo gerador antes do uso. O script de pacote deve bloquear qualquer alteração fora do staging, reabrir o ZIP, validar entradas, manifesto, hashes, codificação e ausência de arquivos proibidos.

O manifesto validado é sempre o CSV extraído do ZIP, não apenas os objetos em memória do script. `MANIFESTO-ARQUIVOS.csv`, `RESUMO-ENTREGA.md`, `RELATORIO-VALIDACOES.md` e relatórios de testes incluídos passam pelo scanner de secrets.

O manifesto lista apenas arquivos versionaveis criados ou modificados no repositorio desde o inventario inicial. `RESUMO-ENTREGA.md` e `RELATORIO-VALIDACOES.md` sao controles obrigatorios do ZIP, validados e escaneados separadamente.

## Bloco 3 - backend de domínio base

O Bloco 3 pode criar apenas domínio Java puro espelhando as migrations auditadas. Enquanto `jakarta.persistence` e Spring Data JPA não estiverem aprovados localmente, é proibido criar entidades JPA anotadas, repositories, datasource de domínio, services de negócio, controllers de domínio ou endpoints funcionais.

Campos sensíveis devem permanecer como hashes, referências ou textos sanitizados. Documento privado não pode virar mídia pública. Auditoria deve usar snapshots sanitizados ou hashes. Pix Efi continua proibido fora de mock/local sem credencial real.

## Bloco 4 - persistência JPA base

Entidades JPA devem permanecer em `br.com.topsdojob.v3.persistence.entity` e repositories em `br.com.topsdojob.v3.persistence.repository`. Os records de `domain/*` continuam como contratos estruturais Java puros.

Repositories do Bloco 4 são mínimos e não devem receber query nativa, regra de negócio, integração externa, controller, endpoint ou service. Migrations e SQL continuam protegidos por revisão específica.

## Bloco 4.2 - build local

Antes de criar services, controllers ou endpoints, execute:

```powershell
.\scripts\local\validar-build-local.ps1
```

O script nao instala ferramentas, nao baixa dependencias, nao cria Maven Wrapper, nao executa `npm install`, nao executa `npm ci`, nao acessa banco e nao acessa rede externa. Retorno `2` indica pendencia operacional, como Java 17, Maven/wrapper ou `frontend/node_modules` ausentes.

Sem build backend validado ou decisao expressa documentada, novos services/controllers/endpoints de dominio permanecem bloqueados.

## Bloco 4.3 - gate de toolchain

Use o diagnostico abaixo para levantar a toolchain local:

```powershell
.\scripts\local\diagnosticar-toolchain-local.ps1
```

Comandos de instalacao documentados em runbook sao apenas sugestoes para execucao manual/autorizada futura. Nao execute instalacao, download, alteracao de PATH, criacao de wrapper, `npm install` ou `npm ci` sem autorizacao expressa.

## Bloco 4.4 - build local validado

O Bloco 4.4 teve autorizacao expressa para localizar Java 17 LTS, localizar Maven, executar `npm install` no frontend e baixar dependencias publicas necessarias ao build local.

Antes de iniciar um proximo bloco tecnico, execute:

```powershell
.\scripts\local\validar-build-local.ps1
.\scripts\local\validar-persistencia-jpa-estatica.ps1
```

Estado do gate:

- backend compile/test validado com Java 17 e Maven local;
- frontend lint/build validado com Node/npm local;
- `frontend/package-lock.json` versionavel;
- `backend/target`, `frontend/node_modules` e `frontend/.next` proibidos no Git.

Services, controllers e endpoints de dominio continuam proibidos sem fase futura expressamente autorizada. O Bloco 4.4 nao autoriza banco, Flyway, migration nova, SQL novo, seed, importador real, API externa, producao, VPS, remote, push ou commit.

## Bloco 5 - API publica de leitura

O Bloco 5 autoriza apenas services/controllers publicos de leitura em:

- `br.com.topsdojob.v3.application.publico`;
- `br.com.topsdojob.v3.web.publico`.

Regras:

- somente `GET`;
- sem admin funcional;
- sem autenticacao real;
- sem sessao/token real;
- sem acao critica;
- sem financeiro/Pix/Efi funcional;
- sem moderacao real;
- sem importador real;
- sem banco persistente;
- sem migration ou SQL novo.

DTO publico nao pode expor entidade JPA, documento privado, CPF, IP, user-agent, hash, auditoria, pagamento, credito, e-mail privado, storage key ou midia nao aprovada.

## Bloco 6 - frontend publico e API local

O frontend publico pode consumir apenas a API local de leitura por `frontend/src/lib/api/publicApi.ts`.

Regras:

- `NEXT_PUBLIC_API_BASE_URL` tem prioridade quando configurado;
- `http://localhost:8080` e fallback somente em ambiente local;
- nenhum dominio de producao deve ser default;
- nao usar token, secret, localStorage ou sessionStorage;
- manter fallback seguro de backend indisponivel;
- preservar visual atual, sem nova paleta, tipografia ou redesign;
- nao expor WhatsApp publico enquanto `PENDENTE_POLITICA_EXPOSICAO_WHATSAPP_PUBLICO`;
- nao expor bucket, storage key, hash ou URL privada enquanto `PENDENTE_URL_PUBLICA_MIDIA_CDN`;
- rotas alternativas `/perfil`, `/ads`, `/anuncio` e `/acompanhante` continuam proibidas.

Services/controllers/repositorios deste bloco permanecem de leitura. Migration, SQL, banco persistente, Flyway, seed, API externa, producao, VPS, remote, push e commit continuam proibidos sem autorizacao futura expressa.

## Bloco 7 - e2e local descartavel

Validacoes end-to-end locais podem usar somente PostgreSQL descartavel, sem volume persistente e sem pull automatico de imagem.

Regras:

- usar imagem PostgreSQL local versionada, quando disponivel;
- aplicar migrations V001-V017 sem alterar SQL;
- dados sinteticos devem ficar apenas em `scripts/local/dados-sinteticos/`;
- backend local deve usar datasource por env local e `ddl-auto: validate`;
- smoke HTTP deve apontar somente para localhost;
- ao final, backend, container e rede devem ser encerrados/removidos;
- `RESUMO-ENTREGA.md` deve receber metadados reais de validacoes via `-MetadadosExecucao`.

Permanece proibido usar dados reais, dump, arquivo real de entrada, producao, VPS, banco de producao, API externa, Efi real, OpenAI, seed real, admin funcional, autenticacao real, Pix/Efi funcional, importador real, remote, push ou commit.

## Bloco 8 - metricas publicas e WhatsApp local

Endpoints `POST` publicos de metrica sao permitidos somente para:

- registrar visualizacao;
- registrar clique WhatsApp;
- avaliar politica backend de contato publico.

Eles nao podem alterar anuncio, pagamento, credito, moderacao, admin ou importacao.

Dados tecnicos devem ser minimizados por hash. IP, User-Agent e referer brutos nao podem ser persistidos nem logados.

WhatsApp so pode ser retornado por `POST /api/public/anuncios/{slug}/clique-whatsapp`, depois de verificacao reforcada valida no backend.

## Age gate e stories

- O conjunto canonico fica sob `/api/public/compliance`, com aceite global, challenge, verify, status, documento e revoke.
- O aceite global por sete dias nao libera original `RESTRITA_18`, Story nem WhatsApp.
- A verificacao reforcada valida nascimento, CPF e aceites; tokens brutos nunca sao persistidos.
- O fallback documental aceita exatamente um JPEG, PNG ou PDF de ate 12 MB em storage privado.
- CPF, nascimento, token, storage key, bucket, hash e URL privada nao podem aparecer em respostas administrativas ou logs.
- `localStorage`, `sessionStorage` e estado React nao podem ser autoridade de seguranca.
- Fora de `local`, salt de metricas e segredo do age gate ficticios devem falhar.

## UX e CSRF do age gate

- Todos os POSTs publicos do age gate devem enviar o cookie `XSRF-TOKEN`, o header `X-XSRF-TOKEN` e `credentials: include`.
- Apos verificacao reforcada, o frontend reconsulta o backend e atualiza midia, Story e WhatsApp sem reload completo.
- CORS com credentials e permitido somente em `APP_ENV=local` para origens localhost configuradas.
- O frontend continua proibido de decidir classificacao, usar localStorage/sessionStorage ou liberar conteudo sem retorno do backend.

## Bloco 11 - midia publica/CDN local

- URL publica de midia permanece nula enquanto nao houver CDN/storage publico aprovado.
- A pendencia publica padrao e `PENDENTE_URL_PUBLICA_MIDIA_CDN`.
- Backend e a unica fonte para decidir midia publicavel, classificacao, idade, stories e WhatsApp.
- DTOs publicos nao podem expor `bucket`, `chaveObjeto`, `storageProvider`, `sha256`, `etag`, URL privada ou documento privado.
- Frontend pode exibir apenas estado pendente localmente; nao deve criar regra propria de liberacao ou montar URL a partir de campos de storage.
- Placeholder local so pode existir em fase futura se for explicitamente aprovado, neutro e restrito a local.

## Bloco 12 - autenticacao admin local

- `/api/admin/**` deve exigir sessao autenticada, exceto `POST /api/admin/auth/login`.
- `/api/public/**` deve permanecer publico.
- Login admin local usa sessao/cookie; JWT, OAuth e social login continuam proibidos.
- Respostas de auth admin nao podem retornar senha, hash, token ou cookie.
- Frontend admin nao pode usar localStorage/sessionStorage nem salvar credencial.
- Dados admin sinteticos devem ficar somente em `scripts/local/dados-sinteticos/` e nunca em migration.
- CSRF local desabilitado fica documentado como `PENDENTE_CSRF_ADMIN_PRODUCAO`.
- Acoes administrativas criticas continuam proibidas ate fase futura expressa.

## Bloco 13 - hardening auth admin

- O arquivo base deve usar `APP_ENV:nao_configurado`.
- Somente profile local pode defaultar `APP_ENV` para `local`.
- `/api/health/**`, `/api/public/**` e `POST /api/admin/auth/login` sao as liberacoes publicas conhecidas.
- `/api/admin/**` exige sessao autenticada.
- Qualquer outro `/api/**` deve permanecer bloqueado por deny-all.
- Frontend admin deve usar `credentials: include` e nao pode usar localStorage/sessionStorage.
- Login/credencial admin nao devem vir pre-preenchidos.
- `RESUMO-ENTREGA.md` deve refletir metadados reais de testes; sem metadados, usar `nao informado`, nao `Nenhum`.
- Continuam proibidos endpoints de moderacao real, financeiro/Pix, importador real, production/VPS, remote, push e commit sem autorizacao futura.

## Bloco 14 - admin read-only local

- `application.yml` deve manter `EFI_ENABLED=false` por default; local/teste nao simulam sucesso financeiro e HML so pode habilitar Efi com secrets e certificado externos de homologacao.
- Endpoints admin read-only permitidos neste bloco usam somente `GET` sob `/api/admin/**`.
- `ADMIN` pode ler todos os resumos; `MODERADOR` pode ler anuncios/moderacao/midia; `COMERCIAL` pode ler visao geral, anuncios e metricas; `USUARIO` nao acessa admin.
- DTOs admin read-only nao podem expor documento privado, telefone/WhatsApp real, storage key/hash/bucket, payload sensivel, senha/hash/token/cookie ou dado financeiro sensivel.
- Frontend admin deve consumir read-only com `credentials: "include"` e continuar sem localStorage/sessionStorage.
- Continuam proibidos aprovacao, reprovacao, exclusao, upload real, pagamento, credito, Pix/Efi funcional, moderacao real, importador real, seed real, migration/SQL de schema, producao/VPS, remote, push e commit.

## Bloco 15 - admin read-only detalhado

- Health publico deve expor apenas `status`, `app` e `requestId`.
- Dados de ambiente e o indicador `efiPixEnabled` devem ficar somente em `/api/admin/sistema/status`, restrito a `ADMIN`, sem expor configuracao ou secrets.
- Endpoints detalhados admin seguem somente `GET`, com sessao/RBAC.
- `COMERCIAL` pode consultar anuncios em versao limitada e nao acessa midia ou revisao detalhada.
- DTOs detalhados nao podem expor documento privado, CPF, telefone bruto, WhatsApp normalizado, storage provider, bucket, chaveObjeto, hash, etag, payload completo, financeiro sensivel, senha/hash/token ou auditoria sensivel.
- Filtros devem permanecer simples, paginados e sem query nativa.
- Frontend admin detalhado permanece sem botoes funcionais de acao critica.

## Bloco 20 - templates e preview de outbox

- `GET /api/admin/outbox/{id}/preview` e endpoint read-only.
- Preview de outbox deve retornar `envioExternoExecutado=false` e `somentePreview=true`.
- Preview nao pode alterar status, marcar `PROCESSADO`, auditar envio ou chamar provider externo.
- A previa administrativa deve usar placeholders neutros e nao pode conter dado real, URL privada, telefone real, WhatsApp real, documento, Pix ou link de pagamento.
- Frontend pode exibir `Ver previa`, mas nao pode criar enviar, reenviar ou marcar enviado.
- O worker canonico de e-mail e independente da previa: processa somente eventos novos com `communicationVersion=1`, usa lock, idempotency key, retry e templates server-side.
- Local e pre-producao usam `CAPTURE` com Mailpit; `DIRECT` e permitido apenas com configuracao explicita de producao, TLS e secrets externos.
- Codigo de confirmacao ou recuperacao nunca pode ficar em claro na outbox, logs ou endpoints administrativos.

## Bloco 21 - ajustes visuais pequenos e mobile estavel

- Melhorias leves de UI/UX sao permitidas somente para espacamento, alinhamento, legibilidade, contraste, hierarquia, responsividade, estados vazios, loading estavel e organizacao de CTA.
- Redesign, nova identidade visual, nova paleta, nova tipografia, animacoes chamativas e mudanca estrutural nao aprovada continuam proibidos.
- Mobile nao pode ter elemento solto, dancando, flutuante indevido, sobrepondo conteudo, com scroll horizontal ou causando layout shift perceptivel.
- `document.body.style.overflow` e scroll lock continuam proibidos.
- Qualquer `position: fixed`, `position: absolute`, `position: sticky`, `100vw`, animacao ou transform em area publica deve ter justificativa documentada e validacao mobile.
- Executar `scripts/local/validar-ui-mobile-estatica.ps1` quando houver mudanca visual/frontend no Bloco 21.

Resultado do Bloco 21:

- componentes publicos locais foram adicionados sem nova identidade visual;
- WhatsApp bruto nao deve ser renderizado no HTML;
- placeholders de midia podem ser neutros e locais, mas nao podem criar URL, storage ou CDN;
- consulta a producao, quando estritamente necessaria, deve permanecer somente leitura e documentada;
- `scripts/local/validar-ui-mobile-estatica.ps1` deve continuar sem alertas ou com justificativa documentada.

Bloco 21.1:

- age gate publico nao pode iniciar com data pre-preenchida;
- botao de confirmacao de idade deve ficar desabilitado ate data valida;
- prints de aprovacao visual devem ser gerados localmente e versionados como evidencia do bloco;
- prints nao podem usar producao, dado real, midia real, WhatsApp real ou storage real;
- `RESUMO-ENTREGA.md` do pacote deve receber metadados reais de testes quando as validacoes forem executadas.

## Bloco 22 - Premium local read-only

- Premium preserva producao atual e recursos novos so podem ser aditivos.
- Gratuito continua util e sem limite comercial artificial de cliques, contatos ou WhatsApp.
- Endpoints Premium admin devem ser GET e sanitizados.
- Publico pode receber apenas flags/rotulos seguros de beneficio ativo.
- DTO publico nao pode expor valor pago, credito, grupo, campanha, origem financeira ou historico.
- Expiracao conjunta pode ser calculada e reportada, mas nao executa job real nem altera banco por vencimento.
- Dados sinteticos Premium devem ficar em `scripts/local/dados-sinteticos/`, nunca em migration.
- Frontend admin pode exibir status/read-only, sem botao de ativar, comprar, pagar, ajustar credito ou checkout.

## Bloco 23 - creditos e ledger read-only

- Endpoints de creditos/ledger devem ser `GET`, locais e restritos a `ADMIN` com `FINANCEIRO_LER`.
- DTOs nao podem expor txid, e2eid, copia e cola, QR Code, payload Pix/Efi, valor monetario, chave operacional bruta, documento, contato real ou storage.
- Frontend `/admin/creditos` pode exibir saldo, movimentos e inconsistencias sanitizadas, sem botao de comprar, pagar, ajustar, estornar, conciliar ou Pix.
- Dados sinteticos de creditos ficam em `scripts/local/dados-sinteticos/`, nunca em migration.
- Continuam proibidos credito real, pagamento real, Pix/Efi real, webhook, conciliacao real, ajuste/estorno real, worker/scheduler, migration/SQL de schema, producao/VPS, remote, push e commit sem autorizacao futura.

## Bloco 24 - pagamentos read-only local

- Endpoints de pagamentos devem ser `GET`, locais e restritos a `ADMIN` com `FINANCEIRO_LER`.
- DTOs nao podem expor valor monetario, evidencia bruta de transacao, identificador bruto de provedor, chave operacional bruta, payload Pix/Efi, QR Code, copia e cola, documento, contato real ou storage.
- Provedor deve ser classificado por evidencia explicita. Nao presumir Mercado Pago por legado nem Efi sem evidencia.
- Frontend `/admin/financeiro` pode exibir pagamentos e inconsistencias sanitizadas, sem botao de cobrar, pagar, gerar Pix, processar webhook, conciliar, creditar, estornar ou ajustar.
- Dados sinteticos de pagamentos ficam em `scripts/local/dados-sinteticos/`, nunca em migration.
- Continuam proibidos cobranca real, Pix/Efi real, Mercado Pago real, webhook real, conciliacao real, credito real, estorno, worker/scheduler, migration/SQL de schema, producao/VPS, API externa, remote, push e commit sem autorizacao futura.

## Bloco 25 - desempenho read-only

- Endpoints de desempenho devem ser `GET`, locais, sanitizados e protegidos por sessao/RBAC.
- `ADMIN` e `COMERCIAL` podem ler resumo comercial agregado; `MODERADOR` pode ler apenas desempenho basico de anuncio; `USUARIO` nao acessa admin.
- DTOs nao podem expor IP, User-Agent, referer bruto, hash interno, documento, contato bruto, storage, pagamento, credito, Pix/Efi, saldo, txid ou valor monetario.
- Prova de resultado nao pode prometer contratacao, lead, clique ou retorno garantido.
- Premium pode ser comparado como exposicao/tendencia, mas `promessaResultadoGarantido=false` e `gratuitoLimitado=false` devem permanecer explicitos.
- Frontend `/admin/desempenho` nao pode ter botao de comprar, pagar, impulsionar, exportar, gerar Pix, ajustar credito, processar pagamento, chamar pixel ou tracking externo.
- Dados sinteticos de desempenho ficam em `scripts/local/dados-sinteticos/`, nunca em migration.
- Continuam proibidos producao/VPS, banco de producao, API externa, OpenAI, Efi real, migration/SQL de schema, remote, push e commit sem autorizacao futura.

## Bloco 26 - Anuncie gratis local

- Endpoint publico permitido: `POST /api/public/anunciar`.
- A solicitacao deve nascer nao publica: `PENDENTE_REVISAO`, `PENDENTE`, revisao `ABERTA` e busca `NAO_PUBLICAVEL`.
- Frontend `/anunciar` deve ser progressivo quando o bloco exigir wizard, usar apenas dados sinteticos locais e nao pode usar `localStorage`, `sessionStorage`, scroll lock, botao flutuante ou animacao automatica.
- O backend deve bloquear campos perigosos e retornar `400` para validacao, nunca `500` por erro de payload.
- Gratuito continua util e nao recebe limite comercial artificial de WhatsApp, clique ou contato.
- Continuam proibidos upload real, foto real, video real, documento real, pagamento, credito, Pix/Efi, Premium obrigatorio, envio real, dado real, migration/SQL de schema, producao/VPS, API externa, remote, push e commit depois do checkpoint local.

## Bloco 26.2 - SEO central e Premium preview

- `/anunciar` deve enviar payload somente na revisao final.
- Premium em `/admin/premium` pode ter preview local, mas nao pode comprar, cobrar, gerar Pix, chamar Efi, criar credito, checkout, webhook ou ativar beneficio real.
- SEO passa a usar `docs/v3/SEO-*.md` e `scripts/local/validar-seo-publico-local.ps1`.
- Rotas `/anuncios/[slug]`, `/acompanhantes/[uf]/[cidade]`, `/acompanhantes/[uf]/[cidade]/[bairro]` e `/anunciar` devem ser preservadas.
- Paginas publicas nao devem exibir texto tecnico como "skeleton" ou "API local".
- `noindex` publico local so pode ser removido em cutover aprovado.

## Bloco 27 - SEO publico local

- Cidade deve usar title/H1 `Acompanhantes em [Cidade] - [UF]`.
- Bairro deve usar title/H1 `Acompanhantes em [Bairro], [Cidade] - [UF]`.
- Anuncio deve preservar `/anuncios/[slug]`, usar title/description seguros e linkar cidade/bairro quando houver.
- Breadcrumbs e linkagem interna sao obrigatorios em cidade, bairro e anuncio.
- Home deve apontar para cidade, bairro, anuncio e `/anunciar`.
- Sitemap local deve usar `localUrl`, sem API, admin, rotas fracas ou dominio de producao.
- Robots local permanece bloqueado; remover noindex depende de gate Pro/cutover futuro.
- Continuam proibidos dado real, midia real, producao alterada, migration, SQL, upload, pagamento, Pix/Efi, email/WhatsApp real, importador real, remote e push.

## Bloco 28 - mapa SEO e cutover

Antes de qualquer homologacao/cutover SEO:

- execute `scripts/local/validar-mapa-preservacao-seo-local.ps1`;
- nao versione lista bruta completa de anuncios reais;
- mantenha saida bruta de inventario fora do repositorio;
- preserve `/anuncios/[slug]`, `/acompanhantes/[uf]/[cidade]` e `/acompanhantes/[uf]/[cidade]/[bairro]`;
- nao recrie `/anuncio/[id]`, `/perfil/[slug]`, `/acompanhante/[slug]` ou `/ads/[slug]`;
- nao remova `noindex` local;
- nao configure canonical de producao em ambiente local;
- trate Search Console completo como exportacao manual pendente;
- mantenha o SDD e `docs/v3` como fonte de continuidade para outro chat/ferramenta.

## Bloco 29 - copia sanitizada de producao

Para qualquer trabalho com copia de producao:

- nao use banco de producao como teste;
- nao gere dump novo sem autorizacao expressa separada;
- nao coloque backup/dump/CSV bruto em `C:\topsv3`;
- nao versione backup, dump, CSV bruto, midia, documento ou dado sensivel;
- use apenas caminhos externos em `C:\topsv3-auditoria-local`;
- sanitize CPF, documento, nome civil, e-mail, telefone/WhatsApp, IP/User-Agent, storage, midia e payload financeiro;
- preserve slugs reais apenas dentro do banco sanitizado, nunca em relatorio versionado;
- nao faça pull/instalacao automatica de ferramenta para abrir dump sensivel;
- registre pendencia operacional quando o restore local nao puder ser feito com toolchain ja disponivel.

## Bloco 29.1 - restore sanitizado com cliente compativel

Antes de qualquer restore de backup autorizado:

- conferir SHA-256 esperado;
- diagnosticar cliente PostgreSQL compativel local;
- preferir imagem/cliente PostgreSQL 17.x ja existente localmente;
- nao executar `docker pull` ou instalacao sem autorizacao consciente;
- nao usar producao/VPS como executor;
- nao converter dump em SQL bruto;
- nao versionar backup, dump, SQL bruto, midia, documento, slugs reais em lista bruta, payload financeiro, token, certificado ou segredo.

Se nao houver cliente compativel local, registrar `PENDENTE_CLIENTE_POSTGRES_COMPATIVEL` e gerar pacote bloqueado.

No Bloco 29.2, a unica excecao autorizada foi `docker pull postgres:17`. Todo `docker run` posterior deve usar `--pull=never`. Se o pull falhar por Docker, rede ou permissao, a execucao deve parar sem tentar outro download.

No Bloco 29.3, qualquer recurso Docker criado para restore/sanitizacao deve usar o prefixo `topsv3-bloco29`. E proibido reutilizar, parar, remover, limpar ou conectar container, volume, network ou compose de TopsWI/terceiros. Recursos permitidos: `topsv3-bloco29-net`, `topsv3-bloco29-pg17-bruto`, `topsv3-bloco29-pg17-sanitizado`, `topsv3-bloco29-pgdata-bruto` e `topsv3-bloco29-pgdata-sanitizado`.

No Bloco 29.4, a limpeza destrutiva autorizada se limita aos recursos proprios com prefixo `topsv3-bloco29-`. Restore deve usar `--single-transaction`; raw log de `pg_restore` fica apenas fora do repositorio e relatorio versionado deve conter somente classificacao sanitizada.

No Bloco 29.5, scripts de quarentena devem ficar separados dos scripts de restore final. O restore de quarentena usa somente `--section=pre-data`, `--section=data`, `--no-owner`, `--no-privileges`, `--exit-on-error` e `--single-transaction`; nao restaura `POST_DATA`, nao usa `--disable-triggers`, `--clean` ou `--if-exists`, e nao aprova o banco como staging final.

## Bloco 30 - retomada sem dados reais

- O Bloco 29 permanece materialmente aberto e adiado para pre-staging/cutover.
- O desenvolvimento local deve seguir com dados sinteticos versionaveis e validaveis.
- Dados reais/sanitizados nao sao requisito para o proximo ciclo local.
- Quarentena sanitizada sem `POST_DATA` nao e staging final, nao e base de importacao definitiva e nao valida comportamento transacional final.
- Opcao A permanece obrigatoria para homologacao/cutover: novo backup consistente ou correcao da origem/backup.
- Opcao B e apenas insumo auxiliar agregado de SEO/inventario.
- Opcao C continua bloqueada ate revisao Pro/humana em novo bloco.
- Proibido usar producao, VPS, banco de producao, restore novo, sanitizacao nova, correcao de orfaos, Pix/Efi real, pagamento, upload, e-mail real, WhatsApp real, API externa real, remote ou push.

## Bloco 31 - validacao sintetica local API/SEO

- O checkpoint do Bloco 30 deve estar commitado localmente antes de alterar o Bloco 31.
- E2E sintetico deve usar somente recurso Docker com prefixo `topsv3-e2e-sintetico-*`.
- E proibido parar/remover recurso TopsWI/cripto, `topsv3-bloco29-*` ou terceiros.
- E proibido executar `docker prune`, `docker system prune`, `docker volume prune`, `docker network prune` ou `docker compose down`.
- A fixture `v3-dados-sinteticos.json` pode ser aplicada apenas como overlay sintetico em banco descartavel.
- `BLOQUEADO` nao pode expor WhatsApp publico; listagens normais devem exibir apenas anuncios `LIVRE` publicaveis.
- Scripts API/SEO sinteticos nao podem retornar OK por evidencia antiga quando o backend estiver indisponivel.
- Reutilizacao de evidencia existente so pode ocorrer com parametro explicito `-PermitirEvidenciaExistente`, nunca em validacao padrao, e deve registrar `ALERTA_EVIDENCIA_EXISTENTE_REUTILIZADA`.
- Pro continua obrigatorio antes de homologacao/cutover real com dados reais/sanitizados.

## Bloco 31.1 - hardening dos validadores sinteticos

- `scripts/local/validar-api-publica-sintetica-local.ps1` e `scripts/local/validar-seo-sintetico-local.ps1` devem retornar pendente com exit code 2 quando o backend local estiver indisponivel por padrao.
- `scripts/local/validar-e2e-local-descartavel.ps1` deve usar default Docker `topsv3-e2e-local` e bloquear prefixos vazios, genericos, `cripto`, TopsWI ou fora de `topsv3-*`.
- O wrapper sintetico continua usando explicitamente `topsv3-e2e-sintetico`.
- Bloco 29 segue adiado; quarentena sem `POST_DATA` nao e staging final.

## Bloco 32 - auditoria renderizada publica sintetica

- O checkpoint local dos Blocos 31/31.1 deve existir antes das alteracoes do Bloco 32.
- Auditoria renderizada sintetica deve usar `scripts/local/validar-publico-renderizado-sintetico-local.ps1`.
- Docker, quando usado, deve ficar restrito a `topsv3-render-sintetico-*`.
- Prints versionados so podem conter dados sinteticos locais e nunca midia/documento real.
- Nao fazer redesign, trocar paleta, trocar tipografia, criar botao flutuante, animacao automatica ou scroll lock.
- Validar home, `/anunciar`, cidade, bairro, Brasilia, anuncio livre, anuncio bloqueado, sitemap e robots em desktop/mobile.
- `BLOQUEADO` nao pode expor WhatsApp publico indevidamente.
- Bloco 29 segue adiado; quarentena sem `POST_DATA` nao e staging final.

## Bloco 33 - validacao wizard Anuncie gratis sintetico

- O checkpoint local do Bloco 32.1 deve existir antes das alteracoes do Bloco 33.
- Validar `/anunciar` com `scripts/local/validar-wizard-anunciar-sintetico-local.ps1`.
- Docker, quando usado, deve ficar restrito a `topsv3-wizard-sintetico-*`.
- Prints versionados so podem conter dados sinteticos locais e nunca midia/documento real.
- O wizard nao pode exibir enum/status/snake_case tecnico ao visitante.
- O wizard nao pode criar upload real, pagamento, Pix/Efi, checkout, Premium obrigatorio, e-mail real, WhatsApp real, stores ou autopublicacao.
- Mobile deve permanecer sem scroll horizontal, scroll lock, `document.body.style.overflow`, elemento flutuante solto ou animacao automatica.
- Bloco 29 segue adiado; quarentena sem `POST_DATA` nao e staging final.
