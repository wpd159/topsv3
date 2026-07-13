# SDD Tops do Job V3

Documento central de Specification-Driven Development da V3. Ele consolida o estado local do projeto ate o Bloco 60, com checkpoint local `f6189f0` do Bloco 32.1, checkpoint local `b7f5f98` dos Blocos 33/33.1, checkpoint local corrigido `9b677ea` do Bloco 34, checkpoint local `00e1a02` do Bloco 35, checkpoint local `e031ea3` do Bloco 36, checkpoint local `a6f431f` do Bloco 37 corrigido, checkpoint local `587e2df` do Bloco 38, checkpoint local `a3e92c0` do Bloco 39, checkpoint local `f67880a` do Bloco 40 corrigido, checkpoint local `46ed655` do Bloco 41, checkpoint local `09ffcd3` do Bloco 42, checkpoint local `71404a3` do Bloco 43, checkpoint local `0603a59` do Bloco 44, checkpoint local `282802d` do Bloco 45, checkpoint local `220c2ba` do Bloco 46, checkpoint local `7791d11` do Bloco 47, checkpoint local `9bd38f3` do Bloco 48, checkpoint local `037f9b22` do Bloco 49, checkpoint local `8757e48a` do Bloco 50, checkpoint local `eacecaa2` do Bloco 51, checkpoint local `d528f7ed` do Bloco 52, checkpoint local `0fb2b771` do Bloco 53, checkpoint local `30be1db7` do Bloco 54, checkpoint local `4323df90` do Bloco 55, checkpoint local `6aa01a92` do Bloco 56, checkpoint local `a6487f5b` do Bloco 57, checkpoint local `46a1ff7` do Bloco 58, checkpoint local `3111afc` do Bloco 59, E2E/API/SEO sintetico aprovado, auditoria renderizada publica aprovada, validacao sintetica do wizard `/anunciar`, paridade local do wizard com a producao observavel, validacao admin/moderacao sintetica local, validacao Premium/beneficios sintetica local, validacao Age Gate/WhatsApp sintetica local aprovada, validacao de midia/fotos/stories sintetica local aprovada, MVP local sintetico consolidado, matriz de prontidao para homologacao/cutover documentada, gitleaks real instalado/validado localmente no Bloco 44, gitleaks historico completo validado no Bloco 56, Flyway real local diagnosticado como `PENDENTE_FLYWAY_REAL_LOCAL` no Bloco 45, instalacao Flyway registrada como `PENDENTE_FLYWAY_INSTALACAO_LOCAL` no Bloco 46, Flyway real validado via Docker no Bloco 47 com `OK_FLYWAY_REAL_LOCAL`, Auth/RBAC/CSRF local validado no Bloco 48 com `OK_AUTH_RBAC_CSRF_LOCAL`, observabilidade/auditoria local validada no Bloco 49 com `OK_OBSERVABILIDADE_AUDITORIA_LOCAL`, preflight local de homologacao documentado no Bloco 50, contrato de homologacao sem deploy documentado no Bloco 51, contrato storage/upload/CDN documentado no Bloco 52, contratos criticos de homologacao/cutover consolidados no Bloco 53, dossie final do ciclo local criado no Bloco 54, freeze do ciclo local/sintetico registrado no Bloco 55, hardening Pro local de login/protocolo VPS registrado no Bloco 56, paridade visual publica fase 1 iniciada no Bloco 57, transplante visual a partir do clone local de producao iniciado no Bloco 58, transplante visual de cards/grids/detalhe iniciado no Bloco 59 e paridade visual de header/logo/wizard iniciada no Bloco 60, Bloco 29 adiado como gate de pre-staging/cutover, checkpoint local `36b94c6` dos Blocos 29 a 29.6 e base sintetica local validada para continuidade sem dados reais.

## 1. Visao geral

A V3 e uma reconstrucao local e controlada do Tops do Job, com foco em preservar o comportamento publico correto da producao atual, reduzir risco operacional e preparar uma evolucao auditavel.

Direcao do produto:

- dominio publico alvo: `topsdojob.com`;
- foco em liquidez e base de anuncios antes de receita imediata;
- preservacao do que ja funciona em producao;
- local antes de staging/homologacao/producao.

Estado atual:

- ambiente 100% local;
- backend Spring Boot com dominio, persistencia JPA, API publica local e admin local;
- frontend Next.js com rotas publicas preservadas, admin local e funil publico `/anunciar`;
- SEO operacional de preservacao com inventario publico sanitizado, mapa de URLs, baseline Search Console e plano de cutover;
- protocolo de copia sanitizada de producao com backup bruto sempre fora do repositorio;
- migrations Flyway V001 a V017 criadas e validadas estaticamente;
- PostgreSQL descartavel usado para validacoes locais;
- Bloco 31 validado com E2E/API/SEO sintetico local e descartavel;
- Bloco 31.1 endurece validadores para nao aprovarem por evidencia antiga quando o backend local estiver indisponivel;
- Bloco 32 valida rotas publicas principais renderizadas com dados sinteticos, desktop/mobile, SEO local e prints versionados sinteticos, mas a revisao visual humana reprovou textos tecnicos visiveis;
- Bloco 32.1 corrige a UI publica para nao exibir enums/status/snake_case internos, sem mudar regra de seguranca;
- Bloco 33 cria checkpoint local do Bloco 32.1 e valida o wizard `/anunciar` em desktop/mobile com dados sinteticos;
- Bloco 33.1 corrige apenas textos publicos/acentuacao do wizard, sem alteracao funcional, regra de negocio, seguranca, contrato ou fluxo real;
- Bloco 34 cria checkpoint local dos Blocos 33/33.1 e ajusta a V3 local para paridade do wizard `/anunciar` com a producao observavel, sem ultrapassar age gate, sem dados reais e sem efeitos externos;
- Bloco 35 cria checkpoint local corrigido do Bloco 34 em `9b677ea` e valida admin/moderacao sintetica local com PostgreSQL descartavel, backend/frontend locais, prints desktop/mobile, auditoria sanitizada e outbox sem envio externo;
- Bloco 36 cria checkpoint local do Bloco 35 em `00e1a02` e valida Premium/beneficios sinteticos locais, mantendo gratuito util e Premium aditivo sem Pix/Efi real, checkout, pagamento, credito real ou webhook;
- Bloco 37 cria checkpoint local do Bloco 36 em `e031ea3` e limpa copy visivel publica/admin que parecia bastidor tecnico/local/sintetico, sem alterar regra de negocio, backend funcional, banco, DTO, rota, Premium, pagamento ou fluxo;
- Bloco 38 cria checkpoint local do Bloco 37 corrigido em `a6f431f` e troca rótulos públicos redundantes de status/acesso por copy natural, sem alterar regra de negócio, backend, banco, DTO, rota, contrato, autorização, Premium ou pagamento;
- Histórico superado: o Bloco 39 criou checkpoint local do Bloco 38 em `587e2df` e validou a regra global `LIVRE`/`BLOQUEADO` então vigente;
- Bloco 40 cria checkpoint local do Bloco 39 em `a3e92c0` e valida mídia/fotos/stories sintéticos, placeholders seguros e sanitização de mídia admin/publica, sem upload real, CDN/storage real, dados reais, produção, restore, Pix/Efi real, pagamento, API externa ou push;
- Bloco 41 cria checkpoint local do Bloco 40 corrigido em `f67880a` e consolida o MVP local sintetico, revalidando os fluxos principais ja cobertos sem criar funcionalidade nova;
- Bloco 42 cria checkpoint local do Bloco 41 em `46ed655` e documenta a matriz de prontidao para homologacao/cutover, separando pronto localmente, pendente antes de homologacao, bloqueante antes de producao, exige Pro, exige dados reais/sanitizados e exige decisao humana;
- Bloco 43 cria checkpoint local do Bloco 42 em `09ffcd3`, diagnostica gitleaks real como pendente no PATH e corrige o pacote para nunca gerar `Objetivo` vazio no `RESUMO-ENTREGA.md`;
- Bloco 44 cria checkpoint local do Bloco 43 em `71404a3`, instala `gitleaks` 8.30.1 via `winget`, limpa somente o artefato ignorado `frontend/.next` e valida scan real sem leaks no repositorio fonte;
- Bloco 45 cria checkpoint local do Bloco 44 em `0603a59` e adiciona gate Flyway real local; como Flyway CLI/imagem nao estavam disponiveis, registra `PENDENTE_FLYWAY_REAL_LOCAL` sem instalacao, sem `docker pull`, sem recurso Docker e sem aplicar migrations por Flyway;
- Bloco 46 cria checkpoint local do Bloco 45 em `282802d` e tenta instalar Flyway somente pelo pacote exato `Redgate.Flyway`; como o pacote nao foi encontrado no `winget search`, registra `PENDENTE_FLYWAY_INSTALACAO_LOCAL` sem instalar por caminho alternativo;
- Bloco 47 cria checkpoint local do Bloco 46 em `220c2ba`, baixa somente `flyway/flyway` autorizado e valida migrations V001 a V017 com Flyway OSS 12.10.0 em PostgreSQL descartavel, resultado `OK_FLYWAY_REAL_LOCAL`;
- Bloco 48 cria checkpoint local do Bloco 47 em `7791d11` e valida Auth/RBAC/CSRF local com dados sinteticos, cobrindo login, cookie, logout, bloqueio sem sessao, RBAC, fallback `/api/**`, CORS local e status de CSRF, resultado `OK_AUTH_RBAC_CSRF_LOCAL`;
- Bloco 49 cria checkpoint local do Bloco 48 em `9bd38f3`, corrige propagacao de request-id antes da seguranca, endurece o writer de erro 401/403 e valida observabilidade/auditoria local com `OK_OBSERVABILIDADE_AUDITORIA_LOCAL`;
- Bloco 50 cria checkpoint local do Bloco 49 em `037f9b22` e valida preflight local de homologacao com `OK_PREFLIGHT_HOMOLOGACAO_LOCAL`, separando pronto localmente, pendente para homologacao e bloqueante para producao;
- Bloco 51 cria checkpoint local do Bloco 50 em `8757e48a` e documenta contrato de homologacao/staging sem deploy, sem staging real e sem dados reais;
- Bloco 52 cria checkpoint local do Bloco 51 em `eacecaa2` e documenta contrato storage/upload/CDN, separando midia publica, midia privada e documento privado, sem upload/storage/CDN real;
- Bloco 53 cria checkpoint local do Bloco 52 em `d528f7ed` e consolida contratos criticos de importacao real/dry-run, SEO cutover, financeiro/Pix/Efi/webhooks, backup/rollback, monitoramento operacional e Go/No-Go, sem executar fluxos reais;
- Bloco 54 cria checkpoint local do Bloco 53 em `0fb2b771` e consolida o dossie final do ciclo local/sintetico com status `MVP_LOCAL_SINTETICO_VALIDADO`;
- Bloco 55 cria checkpoint local do Bloco 54 em `30be1db7` e marca o ciclo local/sintetico como `CICLO_LOCAL_SINTETICO_FECHADO`;
- Bloco 56 parte do checkpoint local final do Bloco 55 em `4323df90`, adiciona lockout ao login admin, troca ID de sessao no login bem-sucedido, valida gitleaks historico completo e cria protocolo documental de VPS isolada, sem acessar VPS, producao, restore ou dados reais novos;
- Complementacao do Bloco 56 registra `BLOQUEADO_PARIDADE_VISUAL_PRODUCAO`: a V3 local ainda nao tem paridade visual com a producao publica atual, entao homologacao/cutover nao estao aprovados;
- Bloco 57 usa o checkpoint do Bloco 56 em `6aa01a92` e executa paridade visual publica fase 1 para shell, home, header, containers, botoes, cards e listagens de cidade/bairro; o gate visual permanece aberto ate revisao completa;
- Bloco 58 cria checkpoint local do Bloco 57 em `a6487f5b`, identifica `C:\clone\topsdojob-frontend` como fonte visual local de producao, registra inventario/plano de transplante e aplica apenas a primeira base visual global/header de baixo risco; `C:\clone` segue somente leitura e o gate visual permanece aberto;
- Bloco 59 cria checkpoint local do Bloco 58 em `46a1ff7`, audita cards/grid/detalhe no clone e adapta placeholders, cards, grid e detalhe publico da V3 sem alterar regra funcional ou copiar fetch/auth/upload/pagamento da producao;
- Bloco 60 cria checkpoint local do Bloco 59 em `3111afc`, usa a logo real local em `/logo.webp` no header publico e aproxima o wizard `/anunciar` da referencia do clone, sem alterar regra funcional, backend, banco, upload, pagamento, Pix/Efi ou API externa;
- Complementacao de deploy HML do Bloco 60 cria workflow GitHub Actions + SSH, Compose HML, Nginx `v3.esle.cloud` com `noindex/nofollow/noarchive` e robots `Disallow: /`, mas nao executa deploy, push, SSH, VPS, producao, dados reais, Pix/Efi real, webhook real, upload real, e-mail real ou WhatsApp real;
- Bloco 61 registra HML online em `https://v3.esle.cloud`, HTTP 80 redirecionando para HTTPS 443, certificado LetsEncrypt, `X-Robots-Tag` noindex/nofollow/noarchive, robots `Disallow: /` e `GET /api/health` com `UP`, sem producao, dados reais, Pix/Efi real, webhook real, upload real, push ou fase posterior;
- Bloco 63.2 diagnostica que o HML ainda reflete codigo anterior porque o commit local `159fd91c` do Bloco 63.1 esta `ahead` de `origin/main` e nao foi pushado/deployado; a correcao local remove `Setor Bueno` do header global, adiciona favicon real do clone e mantem `C:\clone` e producao somente leitura;
- Bloco 63.3 continua o acabamento do shell publico com menu mobile fechado por padrao, microinteracoes em cards/botoes e secao de confianca da producao, sem push e sem criar relatorios novos;
- Patch 63.4 ajusta o menu mobile para exibir somente acoes de conta/publicacao, padroniza microinteracoes de botoes publicos e impede que `PUBLICAR SEU ANUNCIO` navegue direto para `/anunciar` sem login;
- Patch 63.5 adiciona guarda frontend temporaria para rotas sensiveis (`/admin`, `/anunciar` e areas privadas), cria entrada publica `/entrar`, redireciona publicacao para login e registra pendencia de validacao de sessao profunda no frontend; enforcement de API admin segue no backend;
- Patch 63.5 tambem corrige a busca do hero como input digitavel; enquanto nao houver rota final de busca, o submit usa stub publico seguro em /acompanhantes/go/goiania com parametro busca.
- Patch 63.6 recria no frontend V3 o aviso publico 18+/cookies da producao, com aceite persistido em cookie/localStorage, configuracao de preferencias e rotas publicas para termos, privacidade, cookies e verificacao etaria; o modal nao usa scroll lock nem `document.body.style.overflow`.
- Patch 63.R reseta o shell publico para comportamento real da producao: header/footer usam modais de login/registro/publicacao, paginas soltas `/entrar` e `/registrar` foram removidas, age gate voltou ao padrao de producao e a guarda de rotas privadas redireciona para a home com modal de entrada.
- Patch 63.7 preserva o front real aprovado como base visual e remove remendos conflitantes; header, footer, login modal, age gate, cookies, CTAs e menu mobile seguem baseados no clone/producao, enquanto somente o modal de criar conta fica reestruturado em HTML/CSS/React simples para reduzir risco de travamento em Android antigo.
- Patch 63.8 supera a decisao do Patch 63.R quanto ao cadastro: apos multiplas correcoes no modal (portal isolado em `document.body`, isolamento de camada/stacking context, remocao de overlay/backdrop-filter/scroll-lock), o bug de composicao/mancha visual em Chrome Android antigo persistiu em teste real. Login continua modal; cadastro deixa de ser modal e passa a pagina real em `/registrar`, isolada em route group proprio (`(auth-routes)`), sem Header, Footer, Hero, botao flutuante, overlay, portal ou Dialog; todo "Criar conta" (header, footer, login modal) passa a navegar para `/registrar`.
- Patch 63.9 encerra a serie de correcoes/diagnosticos incrementais do cadastro iniciada no Patch 63.8 (teste em 2 etapas e matriz de bisect `diagnostico-next*`/`diagnostico-register-*`): apos o bisect localizar o gatilho real (mensagem de "Confirmar senha" montada/desmontada a cada tecla), a decisao final e substituir integralmente a implementacao antiga (`RegisterForm`, `RegisterFormSteps`, `RegisterModal`) por um cadastro novo, criado do zero em `src/features/auth/register/` (`register-page-form.tsx`, `register-validation.ts`, `register-api.ts`, `register.module.css`), renderizado em `src/app/(auth-routes)/registrar/page.tsx`. Fluxo em 2 etapas com renderizacao condicional real (`currentStep === 1/2`, sem CSS para esconder etapa), campos nativos sem componente `Input`/`Checkbox` compartilhado, sem checklist dinamico, sem toggle de olho na senha, sem date picker nativo (mascara DD/MM/AAAA convertida para ISO so no adapter), checagem de duplicidade cancelavel via `AbortController` e nunca no mount. Todas as rotas/paginas/props de diagnostico criadas durante a investigacao foram removidas.
- Patch 63.10 supera a decisao do Patch 63.9 quanto a pagina e ao fluxo em duas etapas: o cadastro volta a ser modal de etapa unica, com estrutura visual, proporcoes, textos, icones, estados e efeitos reproduzidos do `register-modal.tsx`/`register-form.tsx` de `C:\clone\topsdojob-frontend`, mas com logica reescrita sem copiar a implementacao antiga. Todos os campos ficam simultaneamente visiveis; data de nascimento permanece em texto mascarado `DD/MM/AAAA`; mensagens, checklist e regioes dinamicas permanecem montados em DOM estavel; duplicidade usa requisicoes cancelaveis e o submit continua no adapter publico existente. `/registrar` apenas redireciona para a Home com `register=1`, Header e LoginModal alternam os modais e o CTA de publicacao continua abrindo login para usuario nao autenticado.
- Congelamento do bug grafico do cadastro: esta decisao supera as conclusoes causais anteriores. A reproducao foi confirmada em combinacao especifica de Chrome Android/dispositivo, com dois traces DevTools mantidos fora do repositorio. A corrupcao surge na composicao inicial do modal, antes de mudanca posterior de viewport/teclado, com frames descartados e carga concentrada no compositor/GPU; nao ha evidencia de falha de React, API, validacao ou payload como causa da mancha. A limitacao fica aceita como risco conhecido e a correcao e postergada para permitir continuidade do projeto. Reabrir somente diante de impacto mensuravel na matriz de dispositivos suportados, mudanca relevante de Chrome/Android/driver GPU ou A/B controlado que isole um gatilho corrigivel sem alterar o visual aprovado.
- Auth publico essencial passa a usar exclusivamente `POST /api/public/auth/register`, `POST /api/public/auth/login`, `GET /api/public/auth/me`, `POST /api/public/auth/logout` e `GET /api/public/usuarios/verificar-duplicidade`. A sessao e mantida por cookie `JSESSIONID` HttpOnly, Secure em HML e SameSite Lax; o frontend usa `credentials: include`, inicializa CSRF por `/auth/me` e envia `X-XSRF-TOKEN` nas operacoes mutaveis. Nenhum fluxo publico usa `/api/admin/auth/*`, localStorage ou sessionStorage.
- O cadastro publico valida e-mail, telefone, senha/confirmacao, data de nascimento, maioridade e aceites obrigatorios, persistindo usuario, hash BCrypt e papel `USUARIO` no modelo V3 existente, sem migration nova. Evidencia juridica versionada dos aceites e persistencia da data de nascimento permanecem pendencias explicitas de fase propria.
- dados reais, producao, VPS, banco de producao, Efi real e APIs externas fora de uso.

## 2. Escopo e limites

Escopo atual:

- rotas publicas preservadas;
- leitura publica local;
- metricas publicas locais;
- confirmacao local de idade;
- admin local com sessao/RBAC;
- moderacao local minima;
- outbox local e preview sanitizado;
- Premium, creditos, pagamentos e desempenho em leitura local;
- funil local "Anuncie gratis" em wizard progressivo para criar solicitacao nao publica.

Limites permanentes ate aprovacao futura:

- nao usar dados reais;
- nao acessar producao como bancada de teste;
- nao executar deploy;
- nao executar push;
- nao configurar remote sem autorizacao;
- nao usar banco de producao;
- nao usar Efi real;
- nao iniciar importador real;
- nao criar nova migration sem fase expressa.

## 3. Arquitetura local

A arquitetura local esta organizada em:

- `backend/`: Spring Boot, dominio, persistencia JPA, aplicacao e web;
- `frontend/`: Next.js, rotas publicas, admin e cliente API local;
- `infra/local/`: compose e variaveis locais;
- `scripts/local/`: validacoes, e2e descartavel e operacao local;
- `scripts/security/`: scanners de codificacao, arquivos proibidos e secrets;
- `contracts/openapi/`: contrato OpenAPI local;
- `docs/v3/`: SDD, runbooks, decisoes, checklists e evidencias.

## 4. Rotas publicas e SEO

Rotas publicas preservadas:

- `/`;
- `/anuncios/[slug]`;
- `/acompanhantes/[uf]/[cidade]`;
- `/acompanhantes/[uf]/[cidade]/[bairro]`;
- `/anunciar`;
- paginas institucionais locais.

Regras:

- `/anuncios/[slug]` e o contrato publico absoluto da pagina de anuncio;
- rotas alternativas como `/perfil`, `/ads`, `/anuncio` e `/acompanhante` permanecem proibidas;
- ambiente local usa `noindex`;
- canonical de producao nao deve ser emitido em ambiente local;
- textos publicos nao devem expor termos internos como "skeleton", "API local", enums, status tecnico, snake_case ou UPPER_SNAKE_CASE;
- SEO final depende de revisao humana antes de homologacao/producao.
- SEO e prioridade estrutural da V3 e possui documentos proprios em `docs/v3/SEO-*.md`.
- O SEO atual deve ser preservado quando compativel; quando houver conflito comprovado, prevalece o SEO pretendido documentado, sem manter implementacoes concorrentes.
- Google e Bing sao os buscadores prioritarios. O crescimento organico local mira acompanhante/acompanhantes por cidade, UF e bairro; `job` e `jobs` sao termos complementares e nunca substituem a intencao local principal nem a marca Tops do Job.
- Canonical, sitemap, robots, HTML inicial, links rastreaveis, dados estruturados compativeis e estados HTTP reais fazem parte do contrato de cada rota publica.
- Os seis contratos publicos de catalogo foram consolidados sob `/api/public`: listagens por estado, cidade e bairro, agregado real de cidade, detalhe por slug e descoberta hierarquica de localidades. O frontend usa somente `public-catalog-api.ts`; Home, rotas preservadas e sitemap recebem dados reais do backend, sem alias do clone, fallback vazio ou URL artificial. Homologacao SEO continua condicionada aos gates de dados autorizados, mapa final, 301, canonical, robots e Search Console.
- O runner operacional unico de homologacao separa tres acoes explicitas: `app.hml-admin-provision.enabled=true` provisiona ADMIN por `stdin`; `app.hml-fixture.enabled=true` reconcilia dados sem ler credencial; `app.hml-fixture-owner-credential.enabled=true` atualiza por `stdin` apenas a credencial BCrypt do proprietario canonico. Todas permanecem restritas ao profile `homologacao`, desabilitadas no startup normal e recusadas fora desse ambiente.
- Uma melhoria futura de descricao com IA sera opcional e acionada expressamente pela anunciante. O fluxo exigira backend, previa editavel, aceitar/editar/rejeitar, preservacao do original, fidelidade factual, ausencia de dados sensiveis e funcionamento normal quando a IA estiver indisponivel; nenhuma integracao OpenAI e autorizada nesta fase.
- Mudancas visuais publicas de SEO exigem `scripts/local/validar-layout-publico-renderizado.ps1`.
- Mapa de preservacao SEO e cutover exigem `scripts/local/validar-mapa-preservacao-seo-local.ps1`.
- Lista bruta completa de URLs reais de anuncios nao deve ser versionada.
- Inventario bruto SEO fica fora do repositorio.

## 5. Visibilidade individual de mídia e idade

A regra global de classificação etária do anúncio foi superada. O anúncio não possui classificação etária ativa; cada mídia é moderada por seu ID real com `VisibilidadeMidia`:

- foto: `LIVRE` ou `RESTRITA_18`, com decisão explícita antes da aprovação;
- vídeo: sempre `RESTRITA_18`;
- story: sempre `RESTRITA_18`;
- rejeição, pendência e solicitação de ajuste permanecem estados de moderação, nunca valores de visibilidade.

O backend é a fonte da decisão. Mídia `RESTRITA_18` não entrega URL original sem autorização etária válida reconhecida pelo backend; placeholder seguro permanece obrigatório enquanto storage/CDN protegido não estiver disponível. Título, descrição, cidade, bairro, página pública, breadcrumb, SEO textual e contato não dependem da idade ou da visibilidade da galeria. A confirmação local usa cookie HttpOnly assinado, sem CPF, documento, conta, localStorage ou sessionStorage.

Registros anteriores sobre anúncio global `LIVRE`/`BLOQUEADO` são históricos e estão expressamente superados por esta seção e pela migration `V018__visibilidade_individual_midia.sql`.

As migrations V001 a V020 foram aplicadas e validadas com Flyway OSS 12.10.0 em PostgreSQL 17.10 descartavel, com `migrate`, `validate` e `info` aprovados; V020 consta como `Success` e o resultado vigente e `OK_FLYWAY_REAL_LOCAL`.

### Stories de usuario e composicao administrativa

Stories criados pelos anunciantes sao funcionalidade vigente e permanecem integralmente sujeitos ao fluxo atual de creditos, saldo, duracao, inicio, expiracao, renovacao, ordem, status, moderacao, protecao etaria, contratos, metricas, auditoria e telas. Nenhuma implementacao administrativa pode ocultar, substituir, apagar, reduzir, reordenar ou converter esses Stories, nem alterar creditos, saldos ou periodos contratados.

O feed publico implementado coexiste com duas origens: `USUARIO`, preservada como esta, e `ADMINISTRATIVO`, formada dinamicamente pelas midias aprovadas de um anuncio selecionado. A origem administrativa nao consome creditos, nao duplica arquivos, nao cria Story pago falso e nao usa a tabela de Stories pagos com outro significado. `StoryFeedPublicoService` e o compositor unico do feed global: entrega primeiro o grupo administrativo e depois os grupos de usuario na ordem recebida do backend, deduplicando por `arquivo_midia_id` sem modificar o Story pago.

A migration V019 cria a fonte singleton `story_selecao_administrativa`, sem lista de midias. Ativacao, substituicao e desativacao sao atomicas, exclusivas de `ADMIN` e auditadas. Novas midias publicaveis entram e midias rejeitadas, removidas ou invalidas saem por resolucao dinamica. Todas as midias no formato Story exigem confirmacao etaria, sem alterar sua visibilidade original fora do feed; contato do anuncio publico permanece independente.

Antes de remover qualquer codigo relacionado a Stories, e obrigatorio comprovar que ele pertence exclusivamente as ideias canceladas de fixacao permanente no topo, promocao administrativa por creditos, Story administrativo pago falso ou duplicacao fisica de midias, e que nao atende ao fluxo vigente dos anunciantes.

## 6. WhatsApp, contato e metricas

WhatsApp publico e sempre mediado pelo backend.

Regras:

- frontend nao renderiza WhatsApp bruto por conta propria;
- clique de WhatsApp passa pelo endpoint publico local;
- metricas minimizam IP, User-Agent e referer por hash;
- gratuito continua util e nao recebe limite comercial artificial de clique, contato ou WhatsApp;
- anúncio público e ativo mantém contato mesmo sem foto livre ou antes da confirmação de idade;
- anúncio pausado, rejeitado, removido ou não publicado não libera contato;
- telefone bruto não aparece no payload geral, HTML, metadata ou JSON-LD.

## 7. Premium e beneficios

Premium e aditivo e preserva o gratuito util.

Regras:

- Premium nao limita o gratuito artificialmente;
- beneficios sao leitura/calculo local nesta etapa;
- `/admin/premium` pode exibir preview local de combinacao de beneficio/periodo sem efeito real;
- nao ha compra, checkout, cobranca, Pix/Efi funcional ou ativacao real por dinheiro;
- dados Premium locais ficam sinteticos e fora de migrations.

## 8. Creditos, pagamentos e Efi

Creditos, ledger, pagamentos e Pix/Efi estao em leitura local ou mock seguro.

Regras:

- nenhum Pix real;
- nenhuma conciliacao real;
- nenhum webhook real;
- nenhum QR Code ou copia e cola real;
- nenhum valor sensivel bruto em DTO admin;
- creditos sao inteiros;
- dinheiro usa tipo numerico/BigDecimal;
- Efi e o provedor ativo futuro;
- Mercado Pago e legado;
- tabelas legadas de Mercado Pago podem conter evidencias Efi e o provedor deve ser decidido por evidencia;
- Efi real permanece proibida ate fase expressa de homologacao com credenciais e revisao Pro.

## 9. Importador e fonte real

O importador esta preparado estruturalmente, sem uso real.

Regras:

- sem dump;
- sem arquivo real de entrada;
- sem importacao real;
- sem ETL real;
- sem acesso a banco;
- fonte real futura exige gate, autorizacao formal e revisao Pro.

## 10. Admin e RBAC

Admin local usa sessao/cookie e RBAC minimo.

Papeis:

- `ADMIN`;
- `MODERADOR`;
- `COMERCIAL`;
- `USUARIO`.

Regras:

- `/api/admin/**` exige sessao, exceto login;
- frontend admin usa `credentials: include`;
- sem localStorage/sessionStorage;
- sem credencial pre-preenchida;
- acoes criticas exigem permissao, auditoria e fase expressa.

## 11. Moderacao

Moderacao local minima cobre decisoes de anuncio e midia conforme blocos autorizados.

Regras:

- `REPROVAR` exige motivo;
- motivo e sanitizado e limitado;
- `SOLICITAR_AJUSTE` e intermediaria e nao finaliza revisao;
- auditoria mascara e-mail, contato e documento;
- auditoria JSON completa permanece pendencia Pro antes de homologacao/producao.

## 12. Outbox e comunicacoes

Outbox e local e seguro.

Estado atual:

- leitura admin de outbox;
- preview sanitizado;
- simulacao local de processamento apenas em `APP_ENV=local`;
- sem envio externo;
- sem worker;
- sem scheduler;
- sem SMTP externo;
- sem WhatsApp real.

## 13. Midia, storage e CDN

Midia publica continua restrita.

Regras:

- `urlPublica` permanece nula enquanto CDN/storage publico nao estiver aprovado;
- DTO publico nao expoe bucket, storage key, provider, hash, etag ou URL privada;
- documento privado nunca e midia publica;
- upload real esta fora do escopo atual;
- placeholders publicos devem ser neutros e nao podem simular midia real.
- contrato storage/upload/CDN de homologacao fica em `docs/v3/HOMOLOGACAO-storage-upload-cdn.md`;
- midia pendente/rejeitada nao tem URL publica;
- documento privado usa separacao propria e nunca compartilha prefixo publico;
- URL publica so pode existir para midia aprovada.

## 14. Visual, UI e mobile

A V3 preserva o visual atual do Tops do Job.

Melhorias leves permitidas:

- espacamento;
- alinhamento;
- hierarquia visual;
- contraste;
- legibilidade;
- responsividade;
- estados vazios e loading estavel;
- organizacao de CTA.

Proibido:

- redesign;
- nova identidade visual;
- nova paleta;
- nova tipografia;
- animacao automatica;
- floating button solto;
- scroll lock;
- `document.body.style.overflow`;
- elemento mobile solto, sobreposto ou fora do fluxo.

## 15. Seguranca

Seguranca do repositorio:

- scanners de codificacao, arquivos proibidos e secrets;
- bloqueio de dumps, backups, uploads, credenciais, certificados e archives;
- gitleaks real validado localmente no Bloco 44, com fallback local ainda obrigatorio como defesa secundaria;
- ZIPs de revisao fora do Git;
- sem remote/push nas fases locais.

Seguranca de aplicacao:

- DTO publico sem entidade JPA exposta;
- logs sem dados sensiveis;
- request id transversal;
- admin por sessao local;
- deny-all para `/api/**` desconhecida.

## 16. Banco e migrations

Migrations V001 a V017 existem para auditoria e foram validadas localmente.

Estado:

- validacao SQL estatica OK;
- validacao PostgreSQL descartavel OK por SQL ordenado;
- Flyway real local validado via Docker no Bloco 47 com `OK_FLYWAY_REAL_LOCAL`;
- schema segue dependente de revisao Pro antes de fases de homologacao/producao.

Nova migration ou alteracao de SQL de schema so pode ocorrer em fase expressa.

## 17. Ambientes

Ambientes previstos:

- local: permitido para desenvolvimento e validacao;
- staging/homologacao: futuro, depende de gates;
- producao: intocada nesta etapa.

Regras:

- `APP_ENV=local` somente em profile local;
- base default deve falhar fechada;
- producao nao e bancada de teste;
- consulta a producao, quando estritamente necessaria, deve ser somente leitura, documentada e sem exposicao de segredo.

Caminho correto:

1. local;
2. staging;
3. dry-run real autorizado;
4. homologacao validada;
5. producao com backup e rollback.

## 18. Criterios de go-live futuro

Antes de homologacao/producao:

- revisao Pro de schema;
- validacao Flyway real em ambiente descartavel;
- staging validado;
- backup e rollback documentados;
- importacao final validada quando houver fonte real;
- politica final de midia/CDN;
- politica final de retencao documental;
- auditoria JSON revisada;
- CSRF/admin revisado;
- secrets reais fora do Git;
- Efi homologada com credenciais seguras;
- testes e2e aprovados;
- plano de rollback;
- revisao SEO/visual final preservando producao atual.

## 19. Pendencias criticas

Pendencias principais:

- revisao Pro de schema;
- fonte real autorizada;
- Flyway real quando toolchain/imagem estiver disponivel;
- gitleaks instalado ou decisao formal de fallback;
- CDN/storage publico aprovado;
- politica juridica final de retencao;
- auditoria JSON Pro;
- CSRF admin para ambientes nao locais;
- fonte real autorizada para importador futuro;
- homologacao Efi futura;
- importacao real;
- SEO real;
- deploy/cutover;
- backup e rollback;
- revisao visual final com fonte atual da producao.

## 20. Historico de blocos

Historico resumido:

- Fases 0.x: SDD, protecoes Git, scanners e pacote;
- Fase 1A-1C: infraestrutura local, skeleton e contratos;
- Fase 1D: migrations V001 a V017;
- Fase 2A-2G: importador estrutural e gates de fonte real;
- Blocos 3-4: dominio e persistencia JPA;
- Histórico superado dos Blocos 5-10: API pública, frontend público, E2E, métricas, idade e UX da regra global antiga;
- Blocos 11-15: midia segura, auth/admin e read-only detalhado;
- Blocos 16-20: moderacao, outbox e templates;
- Bloco 21: paridade visual e mobile estavel;
- Blocos 22-25: Premium, creditos, pagamentos e prova de resultado read-only;
- Bloco 26: funil local Anuncie gratis;
- Bloco 26.1: correcao visual minima de `/anunciar` e consolidacao do SDD central;
- Bloco 26.2: wizard progressivo de `/anunciar`, Premium preview local e SEO central;
- Bloco 27: SEO publico local de cidade, bairro e anuncio;
- Bloco 27.1: correcao visual obrigatoria, breadcrumbs sem links quebrados e gate renderizado;
- Bloco 28: inventario SEO de preservacao, mapa de URLs, baseline Search Console e plano de cutover SEO.
- Bloco 29: backup autorizado localizado, restore local isolado planejado e pendencia de cliente PostgreSQL compativel registrada.
- Bloco 29.1: SHA-256 do backup autorizado conferido; scripts endurecidos para cliente PostgreSQL 17.x, restore, sanitizacao e validacao agregada; execucao atual segue bloqueada por `PENDENTE_CLIENTE_POSTGRES_COMPATIVEL`.
- Bloco 29.2: `docker pull postgres:17` autorizado e executado, mas bloqueado por Docker daemon indisponivel; scripts agora exigem `docker run --pull=never` nos fluxos operacionais.
- Bloco 29.3: Docker daemon local OK, `postgres:17` baixado, `pg_restore -l` OK, recursos `topsv3-bloco29-*` criados e TopsWI/terceiros preservados; restore bruto falhou em `FALHA_PG_RESTORE_RAW`.
- Bloco 29.4: recursos `topsv3-bloco29-*` limpos/recriados, restore reexecutado com `--single-transaction`, falha repetida classificada como `CONSTRAINT/FK` em `POST_DATA`, sem restauracao parcial.
- Bloco 29.5: restore de quarentena sem `POST_DATA`, sanitizacao imediata e diagnosticos agregados, sem aprovar staging final.
- Bloco 29.6: consolidacao documental e hardening dos scripts de quarentena, sem novo restore, sem nova sanitizacao e sem aprovar staging final.
- Bloco 30: checkpoint local `36b94c6`, gate Bloco 29 adiado, retomada com fixture sintetica local e validacao de dados sinteticos.
- Bloco 33: checkpoint local `f6189f0`, wizard `/anunciar` validado com dados sinteticos, sem upload real, pagamento, Pix/Efi, Premium obrigatorio, e-mail real, WhatsApp real ou autopublicacao.
- Bloco 33.1: correcao textual publica do wizard `/anunciar`; checkpoint do delta Bloco 33/33.1 criado no Bloco 34 em `b7f5f98`.
- Bloco 34: paridade local do wizard com producao observavel; checkpoint corrigido criado no Bloco 35 em `9b677ea`.
- Bloco 35: admin/moderacao sintetica validada localmente com `topsv3-admin-sintetico-*`, sem dados reais, sem producao, sem VPS e sem envio externo.
- Bloco 36: checkpoint local do Bloco 35 criado em `00e1a02`; Premium/beneficios sinteticos validados localmente com `topsv3-premium-sintetico-*`, sem dados reais, sem producao, sem VPS e sem financeiro real.
- Bloco 37: correcao final de copy renderizada remove `Metadados publicos locais para ANUNCIO`, `Autorizacao`, permissoes cruas e descricoes de autorizacao sem acento dos prints publicos/admin, sem alterar regra de negocio, backend funcional, banco, rotas, DTOs, contratos, Premium ou pagamento.
- Bloco 38: checkpoint local do Bloco 37 corrigido criado em `a6f431f`; `Fluxo autorizado` e `Autorização autorizada` deixam de aparecer como pares públicos, substituidos por `Status / Conteúdo disponível` e `Acesso / permitido`.
- Histórico superado do Bloco 39: checkpoint `587e2df` validou a regra global então vigente; não representa a decisão ativa após V018.
- Bloco 40: checkpoint local do Bloco 39 criado em `a3e92c0`; midia publica sintetica valida gratuito ate 2 fotos, Premium com midia extra aditiva, placeholders seguros, stories com idade sem URL real e admin de midia sanitizado.
- Bloco 41: checkpoint local do Bloco 40 corrigido criado em `f67880a`; MVP local sintetico consolidado com validacoes de publico renderizado, SEO sintetico, wizard, admin/moderacao, Premium/beneficios, Age Gate/WhatsApp, midia/fotos/stories e E2E sintetico.
- Bloco 42: checkpoint local do Bloco 41 criado em `46ed655`; matriz objetiva de prontidao para homologacao/cutover criada sem executar producao, restore, dados reais, staging, Pix/Efi, webhook, importador real ou API externa.
- Bloco 43: checkpoint local do Bloco 42 criado em `09ffcd3`; gate gitleaks/toolchain diagnosticado com `PENDENTE_GITLEAKS_REAL_NO_PATH` e empacotador corrigido para objetivo de entrega nao ficar vazio.
- Bloco 44: checkpoint local do Bloco 43 criado em `71404a3`; `gitleaks` real 8.30.1 instalado via `winget` e executado com resultado final sem leaks no repositorio fonte.
- Bloco 45: checkpoint local do Bloco 44 criado em `0603a59`; Flyway real local diagnosticado como `PENDENTE_FLYWAY_REAL_LOCAL` porque CLI/imagem Flyway nao estavam disponiveis localmente.
- Bloco 46: checkpoint local do Bloco 45 criado em `282802d`; pacote exato `Redgate.Flyway` nao encontrado via `winget`, mantendo `PENDENTE_FLYWAY_INSTALACAO_LOCAL`.
- Bloco 47: checkpoint local do Bloco 46 criado em `220c2ba`; `docker pull flyway/flyway` autorizado, Flyway OSS 12.10.0 validou V001 a V017 em PostgreSQL 17 descartavel com `OK_FLYWAY_REAL_LOCAL`.
- Bloco 48: checkpoint local do Bloco 47 criado em `7791d11`; `scripts/local/validar-auth-rbac-csrf-local.ps1` validou Auth/RBAC/CSRF local com PostgreSQL descartavel, dados sinteticos, cookie HttpOnly/SameSite, RBAC e CORS local com `OK_AUTH_RBAC_CSRF_LOCAL`.
- Bloco 49: checkpoint local do Bloco 48 criado em `9bd38f3`; `scripts/local/validar-observabilidade-auditoria-local.ps1` validou request-id, logs locais, erros sanitizados e auditoria local com `OK_OBSERVABILIDADE_AUDITORIA_LOCAL`.
- Bloco 50: checkpoint local do Bloco 49 criado em `037f9b22`; `scripts/local/validar-preflight-homologacao-local.ps1` documenta contratos e gates para homologacao/staging sem executar staging real e retorna `OK_PREFLIGHT_HOMOLOGACAO_LOCAL`.
- Bloco 51: checkpoint local do Bloco 50 criado em `8757e48a`; contrato documental de homologacao criado com ambiente, secrets externos, CORS, cookies, CSRF, banco isolado, storage, logs, auditoria, rollback e monitoramento, sem deploy.
- Bloco 52: checkpoint local do Bloco 51 criado em `eacecaa2`; contrato tecnico de storage/upload/CDN criado para homologacao, sem upload real, storage real, CDN real, R2/S3 real ou API externa.
- Bloco 53: checkpoint local do Bloco 52 criado em `d528f7ed`; contratos criticos de homologacao/cutover consolidados sem importacao real, deploy, staging real, producao, restore, Pix/Efi real, webhook real ou API externa real.
- Bloco 54: checkpoint local do Bloco 53 criado em `0fb2b771`; dossie final do ciclo local/sintetico criado para revisao Pro/humana antes de qualquer homologacao real.
- Bloco 55: checkpoint local do Bloco 54 criado em `30be1db7`; ciclo local/sintetico fechado e congelado para revisao Pro/humana, sem autorizar homologacao real.
- Bloco 56: checkpoint final do Bloco 55 criado em `4323df90`; hardening Pro local de login admin, session fixation, gitleaks historico e protocolo VPS documental, sem producao, VPS, restore, dados reais novos, remote ou push.
- Bloco 57: checkpoint do Bloco 56 usado como base em `6aa01a92`; paridade visual publica fase 1 ajusta shell/header, home, cards e listagens de cidade/bairro, mantendo `BLOQUEADO_PARIDADE_VISUAL_PRODUCAO` aberto para revisao completa.

Detalhes e rastreabilidade ficam em `docs/v3/SDD-indice-rastreabilidade.md`.

## 21. Bloco 27 - SEO publico local

O Bloco 27 prepara as paginas publicas locais para SEO de cidade, bairro e anuncio, mantendo ambiente local seguro com `noindex` e canonical via `localUrl`.

Padroes consolidados:

- cidade: `Acompanhantes em [Cidade] - [UF] | Tops do Job`;
- bairro: `Acompanhantes em [Bairro], [Cidade] - [UF] | Tops do Job`;
- anuncio: title e description seguros a partir do anuncio quando a API local responder;
- breadcrumbs em cidade, bairro e anuncio;
- linkagem home -> cidade/bairro/anuncio/anunciar;
- linkagem cidade -> bairro/anuncio/anunciar;
- linkagem bairro -> cidade/anuncio/anunciar;
- linkagem anuncio -> cidade/bairro/anunciar.

A producao foi consultada somente leitura para observar robots, sitemap, title, canonical, description, robots e H1. Nada foi alterado em producao.

Continuam proibidos cutover, dado real, midia real, banco de producao, migration, SQL, upload, email/WhatsApp real, pagamento, credito, Pix/Efi, checkout, webhook, importador real, API externa, remote e push.

## 22. Bloco 27.1 - gate visual renderizado

O Bloco 27.1 corrige a reprovacao visual dos prints do Bloco 27. A causa registrada foi a ausencia de um gate renderizado que medisse largura real de shell, H1, breadcrumbs e wizard; os validadores anteriores aprovavam sinais textuais, mas nao detectavam mini-coluna.

Correcoes consolidadas:

- `.public-shell` com largura responsiva explicita;
- H1, breadcrumbs, botoes e wizard sem `overflow-wrap:anywhere`;
- breadcrumbs sem links para `/acompanhantes` e `/acompanhantes/[uf]` enquanto essas rotas nao existirem;
- textos publicos sem linguagem tecnica como "SEO local", "Texto SEO local", "skeleton local" ou "V3";
- evidencias novas em `docs/v3/evidencias/bloco-27-1/`;
- script `scripts/local/validar-layout-publico-renderizado.ps1`.

O Bloco 27 nao deve ser considerado aprovado visualmente sem esse gate OK.

## 23. Bloco 28 - preservacao SEO operacional

O Bloco 28 protege o trafego atual antes de qualquer homologacao/cutover.

Estado consolidado:

- commit local de checkpoint do Bloco 27.1: `8575fa5`;
- producao publica consultada somente leitura por robots, sitemap e metadados limitados;
- nada foi alterado em producao;
- saida bruta completa do inventario fica fora do repositorio em `C:\topsv3-auditoria-local\seo\bloco-28`;
- documentos versionados usam contagens, padroes e amostras sanitizadas;
- lista bruta de anuncios reais nao foi versionada;
- Search Console completo permanece pendente de exportacao manual;
- cutover SEO fica bloqueado ate mapa completo aprovado.

Contagem sanitizada observada:

- home: 1;
- cidade: 29;
- bairro: 21;
- anuncio: 62;
- institucional: 1;
- proibido/admin/api: 0;
- desconhecido: 45.

Gates:

- `scripts/local/seo-inventario-producao-publica.ps1`;
- `scripts/local/validar-mapa-preservacao-seo-local.ps1`;
- `scripts/local/validar-seo-publico-local.ps1`;
- `scripts/local/validar-layout-publico-renderizado.ps1`.

## 24. Bloco 29 - copia sanitizada de producao

O Bloco 29 prepara validacao realista da V3 com copia/backup de producao, sem usar producao como bancada e sem versionar dado sensivel.

Estado consolidado:

- commit local de checkpoint do Bloco 28: `a9a50a3`;
- backup existente localizado e copiado para pasta externa;
- caminho externo: `C:\topsv3-auditoria-local\backups\topsdojob-db-20260529-163722.dump`;
- SHA-256 do backup: `ca1ab4d33e8ac9f484f9c5cf61589014189407c960d1a296cf4b984817f9cee9`;
- backup bruto nao entrou no Git, ZIP, chat ou relatorio versionado com conteudo;
- restore local isolado ficou pendente por cliente PostgreSQL compativel ausente;
- sanitizacao e validacao SEO com dados sanitizados ficaram pendentes do restore.

Decisao tecnica:

- nao fazer pull/instalacao automatica de PostgreSQL 17;
- nao converter dump em SQL bruto fora de fluxo revisado;
- nao usar servidor de producao como executor de restore;
- registrar `PENDENTE_CLIENTE_POSTGRES_COMPATIVEL` sem fingir restore.

Gates novos:

- `scripts/local/producao-localizar-backup-autorizado.ps1`;
- `scripts/local/producao-restore-local-isolado.ps1`;
- `scripts/local/producao-sanitizar-db-local.ps1`;
- `scripts/local/validar-dados-producao-sanitizados-local.ps1`;
- `scripts/local/validar-seo-com-dados-sanitizados-local.ps1`.

Pendencias:

- disponibilizar cliente/imagem PostgreSQL compativel com custom format 1.16 em ambiente local autorizado;
- executar restore local isolado;
- executar sanitizacao;
- validar contagens reais sanitizadas;
- validar SEO com dados sanitizados;
- classificar as 45 URLs desconhecidas do Bloco 28.

## 25. Bloco 29.1 - gate de restore sanitizado

O Bloco 29.1 tenta fechar o restore/sanitizacao com seguranca local. Aprovacao completa exige cliente PostgreSQL 17.x local, restore isolado, banco bruto local, banco sanitizado local, sanitizacao real e validacao SEO agregada.

Estado desta execucao:

- SHA-256 do backup autorizado conferido e aprovado;
- cliente PostgreSQL 17.x local nao encontrado;
- apenas `postgres:16` local observado para PostgreSQL;
- `docker pull postgres:17` documentado como acao manual sugerida, sem execucao automatica;
- restore, sanitizacao e SEO com dados sanitizados continuam pendentes.

Proibicoes preservadas: sem producao, sem VPS, sem SQL em producao, sem dump novo, sem SQL bruto, sem backup no Git/ZIP, sem dado sensivel versionado, sem remote, sem push e sem commit nao autorizado.

## 26. Bloco 29.2 - PostgreSQL 17 autorizado

O Bloco 29.2 autorizou somente o download local da imagem oficial `postgres:17`.

Estado desta execucao:

- scripts de diagnostico/restore endurecidos com `docker run --pull=never`;
- `docker pull postgres:17` executado;
- pull falhou porque o Docker daemon local nao estava disponivel;
- `pg_restore -l`, restore, banco bruto, banco sanitizado, sanitizacao e SEO com dados sanitizados nao foram executados;
- status permanece `BLOQUEADO_PENDENTE_CLIENTE_POSTGRES_COMPATIVEL`.

Proibicoes preservadas: sem producao, sem VPS, sem banco de producao, sem SQL em producao, sem dump novo, sem SQL bruto versionado, sem backup no Git/ZIP, sem Pix/Efi real, sem pagamento, sem upload, sem e-mail/WhatsApp real, sem API externa real, sem remote, sem push e sem commit nao autorizado.

## 27. Bloco 29.3 - Docker local e restore isolado

O Bloco 29.3 autorizou iniciar Docker Desktop local ja instalado, executar `docker pull postgres:17` e usar a imagem local somente em recursos Docker exclusivos do Tops do Job V3.

Estado desta execucao:

- Docker Desktop iniciado e Docker daemon local validado;
- diagnostico `docker ps -a`, `docker network ls`, `docker volume ls` e `docker image ls postgres` executado antes de criar recursos do Tops V3;
- recursos de TopsWI/terceiros foram apenas observados e preservados;
- `docker pull postgres:17` concluiu;
- `pg_restore -l` com `postgres:17` passou;
- `docker run` operacional manteve `--pull=never` e nomes com prefixo `topsv3-bloco29`;
- network, volumes e containers exclusivos do Tops V3 foram criados;
- restore bruto falhou com `FALHA_PG_RESTORE_RAW`;
- container bruto ficou com restauracao parcial estrutural detectada por contagem agregada de 77 tabelas;
- container sanitizado foi criado, mas permaneceu sem tabelas restauradas;
- sanitizacao, validacao de dados e SEO com dados sanitizados nao foram executados.

Recursos Docker permitidos/criados:

- `topsv3-bloco29-net`;
- `topsv3-bloco29-pg17-bruto`;
- `topsv3-bloco29-pg17-sanitizado`;
- `topsv3-bloco29-pgdata-bruto`;
- `topsv3-bloco29-pgdata-sanitizado`.

Proibicoes preservadas: sem producao, sem VPS, sem banco de producao, sem SQL em producao, sem dump novo, sem SQL bruto versionado, sem backup no Git/ZIP, sem Pix/Efi real, sem pagamento, sem upload, sem e-mail/WhatsApp real, sem API externa real, sem remote, sem push, sem commit nao autorizado, sem `docker prune`, sem `docker compose down` e sem alteracao em recurso TopsWI/terceiro.

## 28. Bloco 29.4 - diagnostico seguro do restore bruto

O Bloco 29.4 autorizou limpar e recriar exclusivamente os recursos Docker proprios `topsv3-bloco29-*` deixados parcialmente populados pela tentativa anterior.

Estado desta execucao:

- recursos `cripto*`/TopsWI foram detectados e preservados;
- nao houve `docker prune`, `docker system prune`, `docker volume prune`, `docker network prune` ou `docker compose down`;
- containers, volumes e network proprios `topsv3-bloco29-*` foram limpos e recriados;
- `pg_restore -l` com `postgres:17` continuou OK;
- restore bruto foi reexecutado com `--single-transaction`;
- falha repetida classificada de forma sanitizada como `CONSTRAINT/FK` na fase `POST_DATA`;
- raw log ficou fora do repositorio em area de auditoria local;
- bancos bruto e sanitizado ficaram com 0 tabelas apos a falha, evitando nova restauracao parcial;
- sanitizacao, validacao de dados e SEO com dados sanitizados nao foram executados.

Decisao: nao aplicar flags adicionais por suposicao. A continuidade exige revisao humana/Pro do log bruto externo, sem versionar log bruto, slugs, dados reais ou payload sensivel.

Proibicoes preservadas: sem producao, sem VPS, sem banco de producao, sem SQL em producao, sem dump novo, sem SQL bruto versionado, sem log bruto versionado, sem backup no Git/ZIP, sem Pix/Efi real, sem pagamento, sem upload, sem e-mail/WhatsApp real, sem API externa real, sem remote, sem push e sem commit nao autorizado.

## 29. Bloco 29.5 - restore de quarentena sem POST_DATA

O Bloco 29.5 investiga a falha `POST_DATA / CONSTRAINT-FK` por meio de uma restauracao de quarentena sem `POST_DATA`.

Estado planejado:

- criar/recriar somente `topsv3-bloco29-pg17-quarentena` e `topsv3-bloco29-pgdata-quarentena`;
- preservar recursos `cripto*`/TopsWI;
- executar restore com `--section=pre-data`, `--section=data`, `--no-owner`, `--no-privileges`, `--exit-on-error` e `--single-transaction`;
- nao restaurar constraints/FKs/indexes/finalizacoes de `POST_DATA`;
- sanitizar imediatamente a quarentena;
- gerar diagnostico agregado de FK/orfandade e SEO agregado;
- manter o banco de quarentena fora da aprovacao de staging final.

Decisao: a quarentena pode apoiar inventario, SEO e analise de consistencia, mas nao substitui backup consistente nem restore completo aprovado.

Resultado da execucao:

- restore de quarentena sem `POST_DATA`: OK;
- sanitizacao e validacao de dados sensiveis: OK, total sensivel 0;
- FKs previstas no TOC: 76;
- URLs de anuncio preservaveis estimadas: 520;
- banco de quarentena aprovado como staging final: nao;
- decisao A/B/C permanece para revisao humana/Pro.

## 30. Bloco 29.6 - consolidacao do diagnostico de quarentena

O Bloco 29.6 consolida documentalmente o diagnostico do Bloco 29.5 e endurece os scripts para impedir uso de recursos Docker fora dos nomes autorizados.

Estado desta execucao:

- README passa a refletir o estado real do Bloco 29.5/29.6;
- checklist 29.5 fica consistente com as validacoes reais;
- 4 erros agregados de sanitizacao sao classificados como `ALERTA_SANITIZACAO_AGREGADA_NAO_CLASSIFICADA`;
- scripts de quarentena exigem nomes exatos para container, volume, network e banco autorizados;
- nao houve novo restore, nova sanitizacao, correcao de orfaos ou restauracao de `POST_DATA`.

Decisao consolidada:

- Opcao A e obrigatoria para homologacao/cutover: obter novo backup consistente ou corrigir origem/backup antes de staging final;
- Opcao B e permitida apenas como insumo auxiliar de SEO/agregados, inventario e analise de consistencia;
- Opcao C permanece bloqueada ate revisao Pro/humana em novo bloco, com mapeamento seguro, reversivel e sanitizado;
- banco de quarentena nao pode ser usado como base de importacao definitiva;
- banco de quarentena nao pode validar comportamento transacional final;
- Bloco 29 nao esta fechado materialmente;
- Bloco 29.5 esta aprovado apenas como diagnostico de quarentena sanitizada.

## 31. Bloco 30 - retomada sem dados reais

O Bloco 30 encerra operacionalmente a frente do Bloco 29 como gate adiado e permite continuar a V3 sem depender de backup/restauracao de producao no ciclo atual.

Checkpoint local:

- commit: `36b94c6`;
- mensagem: `docs: consolida diagnostico quarentena ate bloco 29.6`;
- remote: vazio;
- push: nao executado.

Decisao:

- Bloco 29 permanece materialmente aberto e reservado para pre-staging/cutover;
- dados reais/sanitizados nao sao necessarios para o proximo ciclo de desenvolvimento local;
- producao continua proibida como bancada de teste;
- banco de producao continua proibido;
- novo backup consistente ou correcao da origem/backup continua obrigatorio antes de homologacao/cutover real;
- quarentena sanitizada sem `POST_DATA` pode apoiar apenas SEO/agregados/inventario, nunca staging final.

Base sintetica local:

- fixture versionavel: `backend/src/test/resources/fixtures/v3-dados-sinteticos.json`;
- gerador de relatorio: `scripts/local/gerar-dados-sinteticos-v3-local.ps1`;
- validador: `scripts/local/validar-dados-sinteticos-v3-local.ps1`;
- cidades: 5;
- bairros: 9;
- anuncios: 12;
- beneficios Premium: 6;
- metricas agregadas: 6.

## 32. Bloco 31 - validacao sintetica API/SEO local

O Bloco 31 consolida o Bloco 30 em commit local e valida a V3 com dados sinteticos em ambiente descartavel/controlado.

Checkpoint local do Bloco 30:

- commit: `2acc60b`;
- mensagem: `docs: retoma v3 com base sintetica local no bloco 30`;
- remote: vazio;
- push: nao executado.

Validacao sintetica:

- script E2E: `scripts/local/validar-e2e-sintetico-local.ps1`;
- API publica: `scripts/local/validar-api-publica-sintetica-local.ps1`;
- SEO sintetico: `scripts/local/validar-seo-sintetico-local.ps1`;
- fixture: `backend/src/test/resources/fixtures/v3-dados-sinteticos.json`;
- Docker usado: sim, apenas container/rede temporarios `topsv3-e2e-sintetico-*`;
- imagem local: `postgres:17`;
- volumes persistentes criados: nao;
- container/rede temporarios removidos: sim.

Resultado:

- migrations V001-V017 aplicadas em PostgreSQL descartavel;
- seed sintetico legado aplicado;
- fixture do Bloco 30 aplicada como overlay;
- backend local iniciou em profile local;
- smoke legado API publica aprovado;
- endpoints `demo-*` da fixture aprovados;
- SEO sintetico aprovado;
- Histórico superado: o gate global então vigente não expôs WhatsApp público;
- pendente/rejeitado nao ficaram publicados;
- dados reais nao foram usados.

O Bloco 29 permanece adiado para pre-staging/cutover. A quarentena sem `POST_DATA` continua proibida como staging final.

## 33. Auth publico essencial

- Contrato backend: `backend/src/main/java/br/com/topsdojob/v3/web/publico/auth/PublicAuthController.java`.
- Servico e sessao: `backend/src/main/java/br/com/topsdojob/v3/application/publico/auth/PublicAuthenticationService.java`.
- Principal publico separado do admin: `backend/src/main/java/br/com/topsdojob/v3/security/publico/PublicUserPrincipal.java`.
- Adapter frontend unico: `frontend/src/lib/public-auth-api.ts`.
- Cadastro, login, `/me`, logout e duplicidade possuem testes de servico e contrato frontend.
- Recuperacao/redefinicao de senha, confirmacao/reenvio de conta e 2FA publico nao fazem parte desta fase.
- O bloco `Confiança e segurança` e seus quatro baloes foram removidos do rodape; logo, texto institucional, links legais e redes sociais foram preservados.

## 34. Area autenticada essencial do anunciante

- Rotas integradas nesta fase: `/painel` e `/minha-conta`.
- A guarda combina a presenca do cookie `JSESSIONID` no middleware com validacao real de `GET /api/public/auth/me` no layout privado.
- O painel usa somente nome, e-mail, telefone e status retornados pela sessao publica; metricas, anuncios, creditos e recomendacoes nao sao simulados.
- O perfil reutiliza `GET /api/public/auth/me` e adiciona `PATCH /api/public/auth/me`, com `credentials: include` e CSRF pelo adapter unico `frontend/src/lib/public-auth-api.ts`.
- Somente nome de usuario e telefone sao editaveis no schema atual. E-mail permanece somente leitura; cidade, descricao, senha, 2FA e exclusao de conta permanecem pendentes de contratos proprios.
- Nenhuma migration foi necessaria.

## 35. Meus anuncios autenticado

- Contratos V3: `GET /api/public/minha-conta/anuncios`, `GET /api/public/minha-conta/anuncios/{slug}` e `PATCH /api/public/minha-conta/anuncios/{slug}`.
- O backend resolve o anunciante exclusivamente pelo `PublicUserPrincipal` da sessao `JSESSIONID`; nenhum identificador de usuario e aceito do frontend.
- A colecao retorna status operacional e de moderacao reais, slug, titulo, localizacao sanitizada e capa processada pela politica publica de midia segura.
- Slug inexistente retorna `404`; anuncio de outro usuario retorna `403`; ausencia de sessao retorna `401`.
- O frontend usa somente `frontend/src/lib/meus-anuncios-api.ts` na listagem, detalhe e edicao. Falha HTTP permanece visivel, o formulario e preservado em erro de validacao e o envio duplo e bloqueado.
- Criacao e edicao compartilham integralmente `frontend/src/features/anuncio-wizard/anuncio-wizard.tsx`; a rota de edicao instancia `mode="edit"` com o slug, hidrata as mesmas etapas e salva pelo `PATCH` autenticado. Nao existe formulario ou editor paralelo.
- Criacao e edicao carregam UF, cidade e bairro exclusivamente por `GET /api/public/localidades`, via `frontend/src/lib/public-catalog-api.ts`; o wizard nao consulta mais `/localidades/estados` nem mantem adapter de localidades proprio.
- O rascunho do wizard usa chave local composta apenas pelo ID estavel da sessao, modo e slug (`wizard:{usuarioId}:create` ou `wizard:{usuarioId}:edit:{slug}`). A edicao consulta primeiro o backend e so restaura rascunho cuja versao de origem coincide com o anuncio carregado; o salvamento limpa somente a chave corrente.
- A edicao permite titulo, descricao, categoria, preco, UF, cidade, bairro, locais de atendimento, servicos e WhatsApp ja persistidos. Proprietario, slug, status, moderacao, publicacao, Premium, creditos, metricas, beneficios, datas publicadas e visibilidade de midia nao integram o request.
- A regra vigente de moderacao e preservada: a edicao remete o anuncio para `PENDENTE_REVISAO`/`PENDENTE`, cria ou atualiza a revisao aberta e bloqueia com `409` enquanto houver revisao em analise. Slug, `publicado_em` e `ultima_publicacao_em` permanecem inalterados.
- As midias existentes sao listadas em ordem e somente para leitura; midia restrita ou nao publicavel nunca recebe URL. Upload, exclusao fisica, reordenacao e alteracao de visibilidade permanecem fora desta fase.
- Horarios/disponibilidade permanecem pendentes porque o modelo V3 atual nao possui persistencia canonica para esses campos. Nenhuma migration, exclusao, Premium, credito, pagamento, metrica ou OpenAI foi implementado nesta fase.

## 36. Refinamentos visuais publicos

- Home, listagens publicas, cards, detalhe e rodape receberam refinamentos de alinhamento, densidade, responsividade e neon rosa discreto, sem alterar URLs, canonical, sitemap ou regras de contato.
- `anunciaDesde` foi acrescentado aos contratos publicos existentes de card e detalhe. A origem e a menor data `publicado_em` confiavel entre todos os anuncios da anunciante, calculada no backend em consulta agregada; criacao da conta, renovacao e apenas o anuncio atual nao sao usados como fonte.
- O frontend apenas formata `anunciaDesde` como mes/ano e omite o texto quando o backend retorna `null`.
- A V020 reconstrui no schema V3 os dados estruturados vigentes de atendimento e servicos: `anuncio_local_atendimento` aceita `A_COMBINAR`, `HOTEL_MOTEL` e `MEU_LOCAL`; `anuncio_servicos` preserva os servicos canonicos, incluindo `ANAL`.
- O selo `Com local` deriva exclusivamente da presenca de `MEU_LOCAL`; `Faz anal` deriva exclusivamente de `ANAL`. E proibido inferir esses selos de endereco, descricao, categoria ou qualquer texto livre.
- O rodape duplicado orfao e a rotacao automatica de perfis relacionados foram removidos. O link de retorno ao topo agora fica no fluxo normal do rodape.
- A V020 foi necessaria porque o modelo vigente ja possui essas colecoes, mas o schema V3 limpo ainda nao as reconstruia. Os contratos publicos existentes foram ampliados sem endpoint paralelo.

## 37. Desbloqueios do catalogo publico no HML

- A V021 cria a fonte canonica `categoria_home`, pois a auditoria confirmou que nenhuma entidade ou tabela V3 equivalente existia. `GET /api/public/categorias-home` retorna somente categorias ativas, ordenadas, com caminho de imagem publico relativo e sem metadado interno de storage.
- Home e filtros usam exclusivamente `frontend/src/lib/public-catalog-api.ts`; textos, imagens, ordem, status e destinos deixam de ter uma segunda fonte estatica no frontend.
- A V021 tambem persiste `usuario.data_nascimento`, anteriormente apenas validada e descartada no cadastro. A data permanece privada e o DTO de anuncio recebe somente `idade` e `idadeOculta`.
- A idade e calculada no backend pela data confiavel e pela data UTC atual. Ela aparece por padrao e so e omitida quando `OCULTAR_IDADE` esta vigente e possui pagamento comprovado por compra ou credito; expiracao faz a idade reaparecer automaticamente.
- O fixture unico `HmlStoriesFixtureService` reconcilia a data de nascimento e os conjuntos estruturados existentes: anuncio A possui `MEU_LOCAL` e `ANAL`; anuncio B nao possui nenhum dos dois. O anuncio A recebe `OCULTAR_IDADE` pago e vigente; o anuncio B concentra os casos expirado, cortesia, administrativo e gratuito que nao ocultam idade. Reexecucao preserva usuario, credencial, anuncios, midias, Story, locais, servicos e beneficios sem duplicacao.
- O runner HML unico aceita a acao explicita `app.hml-fixture-owner-credential.enabled=true` para atualizar por `stdin` somente a credencial BCrypt do proprietario canonico da fixture. A acao e restrita ao profile `homologacao`, nao altera ADMIN, nao cria usuario/anuncio e preserva o hash quando o valor informado ja confere.
