# SDD - Decisoes consolidadas

Este documento consolida decisoes ja assumidas pela V3 e evita que blocos futuros reabram regras sem necessidade.

## Local primeiro

- Desenvolvimento, build, validacao e evidencias acontecem localmente.
- Producao nao e ambiente de desenvolvimento.
- VPS, banco de producao, Efi real, OpenAI/API externa, deploy, remote e push permanecem proibidos sem autorizacao expressa.
- O caminho correto e local -> staging -> dry-run real autorizado -> homologacao validada -> producao com backup e rollback.

## Preservar producao atual

- Quando houver duvida sobre comportamento publico atual correto, preservar a producao atual como base.
- Consulta a producao so pode ocorrer quando os documentos/codigo locais nao forem suficientes.
- Consulta autorizada deve ser somente leitura via `ssh topsdojob`, sem segredo, sem dump, sem escrita, sem restart e sem deploy.
- O dominio publico alvo da V3 e `topsdojob.com`.
- A prioridade de produto e liquidez/base de anuncios, nao receita imediata.

## Visibilidade individual de mídia

- Não existe classificação etária global ativa no anúncio.
- O único enum etário ativo é `VisibilidadeMidia`, com `LIVRE` e `RESTRITA_18`, associado ao ID real da mídia.
- Foto exige decisão individual; vídeo e story são sempre `RESTRITA_18` e o backend rejeita tentativa de `LIVRE`.
- Pendência, rejeição e solicitação de ajuste são estados de moderação, não visibilidades.
- Mídia restrita exige autorização etária real do backend e não expõe original, preload ou metadata antes dela.
- Página, texto, localização, SEO e contato não são bloqueados por mídia restrita.
- A migration `V018__visibilidade_individual_midia.sql` faz o backfill conservador e remove as colunas globais somente depois dele.
- V001 a V018 foram aplicadas e validadas com Flyway OSS 12.10.0 em PostgreSQL 17.10 descartavel; V018 consta como `Success` e os recursos Docker temporarios proprios foram removidos.
- Toda decisão anterior sobre classificação global `LIVRE`/`BLOQUEADO`, inclusive bloqueio de contato, é histórica e está superada.

## Gratuito util

- O gratuito continua funcional.
- Premium e aditivo.
- Nao criar limite comercial artificial de clique, contato ou WhatsApp para forcar Premium.
- Funil `/anunciar` nao exige pagamento, credito, Pix/Efi ou Premium obrigatorio para enviar solicitacao.
- `/anunciar` pode ser wizard progressivo, mas o payload so deve ser enviado na revisao final.
- Bloco 33 validou o wizard `/anunciar` localmente em desktop/mobile com dados sinteticos, sem upload real, pagamento, Pix/Efi, Premium obrigatorio, e-mail real, WhatsApp real, stores ou autopublicacao.
- Bloco 33.1 corrige somente textos publicos/acentuacao do wizard; nao altera regra de negocio, contrato, validacao ou seguranca.

## WhatsApp mediado pelo backend

- Frontend nao monta nem exibe WhatsApp bruto por conta propria.
- Backend valida se o anúncio está público e ativo e medeia o contato.
- Visibilidade da mídia e confirmação de idade não condicionam o contato.
- Telefone ou WhatsApp bruto não aparece no HTML público ou metadata; o endpoint de clique preserva métricas e auditoria mínima.

## Midia segura

- Documento privado nunca e publicavel.
- `urlPublica` permanece nula ate CDN/storage publico aprovado.
- Original `RESTRITA_18` permanece nulo no DTO público sem autorização etária válida.
- DTO publico nao expoe bucket, storage key, provider, hash, etag ou URL privada.
- Upload real continua fora do escopo atual.
- O Bloco 52 define contrato storage/upload/CDN sem executar upload ou acessar storage real.
- Midia publica, midia privada operacional e documento privado devem usar separacao de bucket/container.
- Midia pendente e rejeitada nao podem ter URL publica.
- URL publica so pode ser criada para midia aprovada.
- Documento privado nunca compartilha prefixo publico e nunca vira midia publica.
- Premium/fotos extras e expiracao conjunta de beneficios nao podem apagar fisicamente arquivo sem politica propria.

## Documento de usuario

- Documento pode ser mantido enquanto houver anuncio vinculado ou finalidade operacional legitima.
- `retencao_ate` nao e obrigatorio e deve poder ser nulo.
- Politica de retencao deve permitir opcoes como `ENQUANTO_HOUVER_ANUNCIO`, `DATA_DEFINIDA`, `RETENCAO_JURIDICA` e `MANUAL`.
- Acesso a documento deve ser auditavel.
- Documento validado deve registrar `validado_por` e `validado_em`.
- Documento removido/expurgado deve registrar data de remocao ou expurgo.
- Expurgo automatico nao foi criado nesta etapa.

## Admin e RBAC

- Admin local usa sessao/cookie.
- `ADMIN`, `MODERADOR`, `COMERCIAL` e `USUARIO` sao os papeis base.
- Frontend admin nao guarda credencial em localStorage/sessionStorage.
- `/api/admin/**` exige sessao, exceto login.
- Acoes criticas exigem permissao, auditoria, motivo quando aplicavel e fase expressa.
- Login admin deve aplicar lockout: 5 falhas em 15 minutos bloqueiam por 15 minutos por login hash e IP hash.
- Login inexistente tambem conta para o lockout.
- Durante bloqueio, login admin retorna `429` com erro generico.
- Login admin bem-sucedido deve trocar o ID da sessao antes de salvar o `SecurityContext`.
- O Bloco 48 valida localmente login, cookie HttpOnly/SameSite, logout, bloqueio sem sessao, RBAC `ADMIN`/`MODERADOR`, fallback `/api/**`, CORS local e status de CSRF com dados sinteticos.
- CSRF local pode permanecer desabilitado apenas para smoke controlado; homologacao/producao exigem revisao Pro de CSRF real, HTTPS, cookie seguro, CORS definitivo e politica de sessao.

## Observabilidade e auditoria

- Request-id deve existir em respostas e logs locais.
- `RequestIdFilter` deve rodar antes da seguranca para cobrir 401/403.
- Logs locais de request devem registrar apenas metodo, status, duracao e requestId.
- Logs nao devem registrar cookie, credencial, documento, contato, IP bruto, user-agent bruto ou stack trace publico.
- Auditoria administrativa local deve registrar requestId e snapshots JSON sanitizados.
- IP e user-agent brutos nao devem ser persistidos em auditoria; hashing real e retencao final ficam para homologacao/producao.
- O Bloco 49 valida observabilidade/auditoria local com dados sinteticos e mantem revisao Pro obrigatoria para logs estruturados JSON finais e auditoria com dados reais.

## Preflight de homologacao

- O Bloco 50 cria preflight local/documental para homologacao/staging sem executar ambiente real.
- `OK_PREFLIGHT_HOMOLOGACAO_LOCAL` significa apenas que nao ha falha local concreta nos contratos verificados.
- Pronto localmente nao equivale a pronto para homologacao, cutover ou producao.
- Staging/homologacao exigem ambiente proprio, secrets fora do Git, CORS definitivo, CSRF revisado, Flyway/gitleaks repetidos e decisao humana.
- Producao continua bloqueada por Bloco 29/restore completo, backup/rollback testado, SEO real, storage/upload real, Pix/Efi/webhooks, importador real, auditoria JSON final e LGPD.

## Contrato de homologacao sem deploy

- O Bloco 51 define contrato documental de homologacao/staging sem criar ambiente real.
- Homologacao deve usar `APP_ENV=homologacao`, dominio proprio, banco isolado e secrets fora do Git.
- CORS deve ser lista explicita; wildcard com credenciais permanece proibido.
- Cookies nao-locais devem usar `Secure`, `HttpOnly` e `SameSite`.
- CSRF deve estar habilitado e testado em ambiente nao-local antes de dados reais/sanitizados.
- Storage/CDN/upload real, Pix/Efi real, webhooks, importador real e financeiro real continuam dependentes de blocos proprios.
- Backup/rollback, monitoramento e auditoria JSON sanitizada sao gates antes de cutover.

## Contratos criticos de homologacao/cutover

- O Bloco 53 consolida contratos documentais para importacao real/dry-run, SEO real/cutover, financeiro/Pix/Efi/webhooks, backup/rollback, monitoramento operacional e Go/No-Go.
- Importacao real exige fonte autorizada, dry-run, relatorio de divergencias, rollback e Pro antes de dados reais/sanitizados operacionais.
- SEO real exige mapa final de URLs, resolucao das 45 URLs desconhecidas se ainda pendentes, 301, canonical, sitemap, robots, Search Console e rollback SEO.
- Financeiro/Pix/Efi/webhooks exigem homologacao propria, idempotencia, conciliacao, ledger, rollback financeiro e logs sem payload sensivel.
- Cutover exige matriz Go/No-Go objetiva, backup/rollback testado, monitoramento minimo e decisao humana registrada.

## Dossie final do ciclo local

- O Bloco 54 consolida o ciclo local/sintetico com status `MVP_LOCAL_SINTETICO_VALIDADO`.
- Esse status e local e nao autoriza homologacao real, cutover ou producao.
- Revisao Pro/humana deve ocorrer antes de qualquer uso operacional de dados reais/sanitizados, restore completo, staging real, Pix/Efi real, webhook real, importador real, storage/CDN real ou producao.
- O Bloco 56 registra o `site.zip` manual como artefato excepcional e confidencial de auditoria, nao como pacote oficial.
- Eventual VPS de restore integral deve seguir o protocolo documental `docs/v3/HOMOLOGACAO-vps-restore-integral-protocolo.md` e producao nunca pode ser bancada.
- O dossie final passa a ser entrada obrigatoria para decidir proximos blocos de homologacao.

## Moderacao

- `REPROVAR` exige motivo.
- Motivo deve ser sanitizado e limitado.
- `SOLICITAR_AJUSTE` e intermediaria, nao finaliza revisao e nao grava decisao final.
- E-mail, contato e documento devem ser mascarados na auditoria.
- `requestIdCliente` permanece reservado, sem promessa falsa de idempotencia.

## Outbox

- Outbox local pode ser lido e simulado somente em ambiente local.
- Preview e sanitizado e nao executa envio.
- Sem SMTP externo, WhatsApp real, worker, scheduler, retry real ou API externa.

## Financeiro

- Creditos, pagamentos, Premium e desempenho estao em leitura local/sanitizada.
- Preview Premium administrativo local nao e compra, nao ativa beneficio e nao altera credito.
- Nenhum Pix real, webhook real, conciliacao real, credito real, estorno real, checkout real ou cobranca real foi criado.
- Efi real depende de homologacao futura e credenciais seguras.
- Efi e o provedor ativo futuro.
- Mercado Pago e legado.
- Tabela legada de Mercado Pago pode conter evidencias Efi.
- Provedor deve ser decidido por evidencia, nao por nome historico de tabela.
- Creditos sao inteiros.
- Dinheiro usa numeric/BigDecimal.

## Importador

- Fases atuais criaram apenas contratos, dicionario, saneamento e dry-run estrutural.
- Fonte real depende de gate formal.
- Nenhum dump ou arquivo real de entrada foi lido.
- Nenhuma importacao real foi iniciada.

## Visual e mobile

- Preservar visual atual do Tops do Job.
- Melhorias leves sao permitidas quando reduzem atrito e nao descaracterizam o site.
- Proibido redesign, nova paleta, nova tipografia, animacao automatica e elemento mobile flutuante indevido.
- Proibido scroll lock e `document.body.style.overflow`.
- 2026-07-08: autorizada reconstrucao fiel do frontend publico usando `C:\clone\topsdojob-frontend` como especificacao final ("preservar visual atual" passa a significar "fidelidade ao clone real", nao ao CSS de patch acumulado). Fase 0 (snapshot local `snapshot/frontend-pre-clone-2026-07-08`) e Fase 1 (dependencias de UI reais do clone: Tailwind v4, Radix, shadcn deps, Heroicons/Lucide, Framer Motion, etc.) concluidas. Preflight do Tailwind permanece desligado (so `theme.css` + `utilities.css`) ate a troca real dos componentes nas proximas fases, para nao alterar visual antes da hora.
- 2026-07-08 (Fase 5, modais): auditado `contracts/openapi/topsdojob-v3-local.yaml` e `backend/src/main/java/.../web` — so existe `AdminAuthController` (`/api/admin/auth/*`); nao ha endpoint publico real de login/cadastro/recuperar-senha/confirmar-conta. Modais de login/criar conta/recuperar senha/confirmar conta portados fielmente do clone e apontados para `/api/public/auth/*` (convencao ja usada no restante do contrato publico) como adapter seguro — falha graciosamente (sem tocar endpoint de admin) ate o backend implementar. Modal de criar conta usa `modal={false}` no Radix Dialog (unica excecao estrutural, visual identico) para nao aplicar scroll-lock/focus-trap que trava Android antigo.
- 2026-07-09: supera a decisao da Fase 5 (2026-07-08) e do Patch 63.R quanto ao cadastro. O Patch 63.R havia mantido o cadastro em modal para paridade visual com producao; apos multiplas correcoes no modal (portal isolado em `document.body`, isolamento de camada/stacking context via `isolation`/`contain`, remocao de overlay/backdrop-filter/scroll-lock), o bug de composicao/mancha visual em Chrome Android antigo persistiu em teste real. Decisao consolidada: login continua modal; cadastro deixa de ser modal e passa a pagina real em `/registrar`, isolada em route group proprio (`(auth-routes)`), sem Header, Footer, Hero, botao flutuante, overlay, portal ou Dialog; todo "Criar conta" (header, footer, login modal) passa a navegar para `/registrar`. O adapter de submit do cadastro nao foi alterado por esta decisao (segue `/auth/register`/`/usuarios/verificar-duplicidade`, divergente da convencao `/api/public/auth/*` registrada acima).
- 2026-07-09 (patch raiz Android): teste real em video no Chrome Android antigo confirmou que o ghosting/mancha em `/registrar` comecava ao abrir/fechar o seletor nativo do campo `input type="date"` (bitmap do calendario preso no compositor grafico), nao mais o layout/overlay ja corrigido acima. Decisao: proibido `input type="date"` em data de nascimento nos fluxos voltados ao usuario (cadastro, KYC do wizard de anuncio, completar perfil); filtros administrativos de periodo continuam usando `input type="date"` normalmente, por serem outra superficie sem o mesmo historico de bug. Criado utilitario puro `frontend/src/lib/date/birth-date.ts` (mascara/parse/validacao/idade minima comparando partes numericas de dia/mes/ano, sem `Date.parse` em string BR e sem depender de timezone) e componente reutilizavel `frontend/src/components/forms/birth-date-field.tsx` (input de texto com mascara DD/MM/AAAA), usados nos tres fluxos citados. Formato visual (DD/MM/AAAA) e formato de estado/API (ISO `YYYY-MM-DD`) ficam separados, convertidos apenas na fronteira do componente; o cadastro passa a enviar `dataNascimento` (ISO) no payload de `/auth/register`, campo que antes era validado mas nao incluido no JSON.
- 2026-07-10 (decisao final do cadastro): apos o teste em 2 etapas ainda falhar em Android real, o bisect por bloco isolou o gatilho exato — a mensagem de erro de "Confirmar senha" era montada/desmontada no DOM a cada tecla digitada. Em vez de seguir corrigindo/dividindo a implementacao antiga, a decisao final e substitui-la integralmente por um cadastro criado do zero. Removidos do codigo ativo: `RegisterForm`, `RegisterFormSteps`, `RegisterModal`, toda a matriz de rotas `diagnostico-next*`/`diagnostico-register-*`, os HTMLs estaticos `diagnostico-renderizacao-*` e as props `diagnosticView`/`diagnosticSkipLegalContentLoad`. Nova arquitetura em `src/features/auth/register/` (`register-page-form.tsx` + `register-validation.ts` + `register-api.ts` + `register.module.css`), renderizada em `src/app/(auth-routes)/registrar/page.tsx` (route group proprio, sem Header/Footer/Hero — chrome publico extraido para `src/components/layout/public-chrome.tsx` e aplicado via `(public-routes)/layout.tsx` e `(private-routes)/layout.tsx`, com `src/app/layout.tsx` raiz reduzido a html/body/providers globais). Fluxo em 2 etapas com renderizacao condicional real (nunca as duas etapas montadas simultaneamente), inputs nativos sem componente compartilhado, sem checklist dinamico, sem toggle de senha, sem date picker nativo, erro de confirmacao de senha validado so no blur/avancar (nunca por tecla), checagem de duplicidade cancelavel via `AbortController` e nunca no mount. Endpoint publico de cadastro continua nao existindo no contrato (`/auth/register` seguido como convencao provisoria, falhando honestamente sem simular sucesso).
- 2026-07-10 (cadastro modal de etapa unica): decisao acima superada apenas na superficie e composicao do cadastro. A pagina e o fluxo em duas etapas foram removidos; `RegisterModal` e `RegisterForm` foram recriados do zero com o resultado visual exato do clone como especificacao, sem reutilizar sua logica. O modal usa todos os campos em uma etapa, data mascarada sem seletor nativo, DOM estavel para mensagens/checklist, foco rosa `#FC1EAD`, checagem de duplicidade cancelavel e o adapter publico existente. `/registrar` redireciona para a Home com abertura do modal; Header e LoginModal apenas coordenam abertura/alternancia, sem mudanca visual. O `Dialog` fica em modo nao modal para evitar scroll lock/focus trap do Radix no Chrome Android antigo, preservando overlay, dimensoes, sombra e animacao visuais do clone.

- 2026-07-10 (congelamento do bug grafico do cadastro): esta decisao supera as conclusoes causais anteriores. A reproducao foi confirmada em combinacao especifica de Chrome Android/dispositivo. Dois traces DevTools, mantidos fora do repositorio, mostram corrupcao ja na composicao inicial do modal, antes da mudanca posterior de viewport/teclado, com frames descartados e tarefas concentradas no compositor/GPU. Nao ha evidencia de React, API, validacao ou payload como causa da mancha. Portal, pagina isolada, transform, animacao, checklist, logo, data e Hero permanecem descartados como causas exclusivas. Decisao: aceitar a limitacao como risco conhecido, postergar nova correcao grafica e seguir para a auditoria/implementacao do Auth publico. Reabrir apenas se houver impacto mensuravel na matriz de dispositivos suportados, regressao em navegadores antes estaveis, mudanca relevante de Chrome/Android/driver GPU ou A/B controlado que isole um gatilho corrigivel sem redesenhar o frontend.

## Copy visivel

- Telas publicas/admin nao devem exibir bastidor tecnico como `local`, `sintetico`, `mock`, `fixture`, `smoke test`, `descartavel` ou `API local`.
- Dados de validacao que aparecem em tela devem usar linguagem neutra, como `Anúncio de demonstração`, `Perfil de demonstração`, `Cidade de demonstração` e `Área administrativa`.
- Slugs, IDs, classes CSS, nomes de scripts e parametros internos podem preservar termos tecnicos quando nao forem copy renderizada.
- Validadores renderizados devem reprovar copy de bastidor visivel antes de novo checkpoint.

## SEO central

- SEO e prioridade central da V3.
- Preservar o SEO atual quando compativel com o objetivo; em conflito comprovado, prevalece o SEO pretendido, removendo helpers, canonical e schemas superados em vez de manter fontes concorrentes.
- Preservar `/anuncios/[slug]`, `/acompanhantes/[uf]/[cidade]`, `/acompanhantes/[uf]/[cidade]/[bairro]` e `/anunciar`.
- Nao criar rotas paralelas publicas de anuncio.
- Google e Bing sao prioritarios. Crescimento local deve mirar acompanhante/acompanhantes por cidade, UF e bairro; `job` e `jobs` sao complementares e nao substituem os termos locais principais nem a marca Tops do Job.
- Search Console baseline deve ser registrado antes de cutover.
- `noindex` local so pode ser removido em cutover aprovado.
- Paginas publicas finais nao devem exibir "skeleton" ou "API local".
- Cidade deve usar title/H1 `Acompanhantes em [Cidade] - [UF]`.
- Bairro deve usar title/H1 `Acompanhantes em [Bairro], [Cidade] - [UF]`.
- Anuncio deve preservar `/anuncios/[slug]`, com title/description seguros e links para cidade/bairro quando houver.
- Breadcrumbs sao obrigatorios em cidade, bairro e anuncio.
- Sitemap local deve usar `localUrl` e nao incluir API, admin, rotas fracas ou dominio de producao.
- Consulta a producao para SEO so pode ser publica/somente leitura e documentada.
- Bloco 28 adiciona inventario SEO publico sanitizado, mapa de preservacao de URLs, plano de 301, canonical/sitemap/robots e baseline Search Console.
- Lista bruta completa de URLs reais de anuncios nao deve ser versionada.
- Saida bruta de inventario SEO fica fora do repositorio.
- Search Console completo deve ser exportado manualmente em fase futura.
- Cutover SEO fica bloqueado ate mapa completo aprovado.
- Canonical e schemas publicos devem usar uma unica origem configurada por ambiente; HML permanece integralmente `noindex` e nao pode contaminar producao.
- O reset fiel do frontend preservou seis fetches legados do clone que nao correspondem aos endpoints publicos V3. Eles ficam documentados como pendencias funcionais nao bloqueantes para o fechamento estrutural, sem simular sucesso; nao criar aliases legados nem paginas artificiais. A adaptacao aos contratos V3 e o contrato de descoberta de localidades exigem revisao propria antes de aprovar o SEO dinamico.
- Melhoria futura de descricao com IA sera opcional, iniciada por acao expressa da anunciante e executada somente pelo backend. Deve oferecer previa, edicao, aceite ou rejeicao, manter o original, nao inventar fatos, nao enviar senha/documento/telefone/e-mail e nao impedir criacao/publicacao quando indisponivel. OpenAI nao e implementada nesta fase.
- Nesse fluxo futuro, a IA podera corrigir ortografia, clareza e organizacao e usar somente cidade, bairro e atributos efetivamente informados. Nao podera presumir servicos, disponibilidade, endereco, acesso, seguranca, limpeza, discricao, conforto, caracteristicas fisicas, precos ou atendimento. O texto sera gerado na criacao/edicao, nunca por visualizacao, e salvo apenas apos confirmacao. O contrato devera limitar tamanho, taxa, custo e timeout, versionar prompt, usar resposta estruturada/moderacao e registrar falhas sem conteudo sensivel.
- SDD e docs `docs/v3` sao fonte obrigatoria de continuidade para outros chats/ferramentas.

## Copia sanitizada de producao

- Producao serve para observar; copia local serve para testar.
- Banco de producao nunca e ambiente de teste.
- Backup bruto nunca entra no repositorio, ZIP, chat ou relatorio versionado com conteudo.
- Backup autorizado deve ficar fora de `C:\topsv3`.
- Restore local isolado e sanitizacao sao etapa obrigatoria antes de homologacao com dados realistas.
- Bloco 29 localizou backup autorizado, mas restore ficou pendente por cliente PostgreSQL compativel ausente.
- Bloco 29.1 reforcou que o SHA-256 do backup deve bater antes de qualquer restore e que a execucao completa so pode ocorrer com cliente/imagem PostgreSQL 17.x ja disponivel localmente, sem `docker pull` automatico.
- Bloco 29.2 autorizou exclusivamente `docker pull postgres:17`; todos os `docker run` posteriores devem usar `--pull=never`. Como o Docker daemon estava indisponivel, restore/sanitizacao permaneceram bloqueados.
- Bloco 29.3 autorizou iniciar Docker Desktop local ja instalado e usar `postgres:17` local. Todos os recursos Docker criados para restore/sanitizacao devem usar prefixo `topsv3-bloco29`; recursos TopsWI/terceiros nao podem ser reutilizados, parados, removidos ou alterados.
- O Bloco 29.3 nao aprovou restore/sanitizacao: `pg_restore -l` passou, mas o restore bruto falhou com `FALHA_PG_RESTORE_RAW`. Sanitizacao e SEO com dados sanitizados permanecem bloqueados ate decisao segura sobre os recursos proprios parcialmente populados.
- Bloco 29.4 autorizou limpar/recriar somente recursos `topsv3-bloco29-*` e tornou `--single-transaction` obrigatorio no restore. A falha se repetiu com diagnostico sanitizado `CONSTRAINT/FK` em `POST_DATA`; nao aplicar flags adicionais por suposicao e nao executar sanitizacao sem revisao humana/Pro.
- Bloco 29.5 autoriza restore de quarentena sem `POST_DATA` apenas para diagnostico, sanitizacao imediata e SEO agregado. O banco de quarentena nao pode ser promovido a staging final e nao substitui restore completo consistente.
- Bloco 29.6 consolida que a Opcao A e obrigatoria para homologacao/cutover: obter novo backup consistente ou corrigir origem/backup antes de staging final. A Opcao B fica permitida somente como insumo auxiliar de SEO/agregados. A Opcao C fica bloqueada ate revisao Pro/humana em novo bloco, com mapeamento seguro, reversivel e sanitizado.
- Bloco 30 registra que a frente Bloco 29 nao sera perseguida agora. O gate de restore completo com dados reais/sanitizados fica adiado para pre-staging/cutover. O desenvolvimento local da V3 segue com base sintetica versionavel e validada, sem depender de backup/restauracao de producao.
- Bloco 31 valida localmente API e SEO com base sintetica em ambiente descartavel. O Docker permitido neste bloco fica restrito ao prefixo `topsv3-e2e-sintetico-*`; recursos TopsWI/cripto e `topsv3-bloco29-*` nao podem ser alterados.
- Bloco 31.1 decide que validadores sinteticos de API/SEO nao podem aprovar por evidencia antiga quando o backend local estiver indisponivel. Por padrao devem retornar pendente com exit code 2; reutilizacao de evidencia existente exige parametro explicito e alerta documentado.
- O E2E local descartavel usa prefixo default `topsv3-e2e-local`; o wrapper sintetico continua forcando `topsv3-e2e-sintetico`.
- Histórico superado do Bloco 32: o checkpoint `790188b` validou a regra global então vigente; Docker permaneceu restrito a `topsv3-render-sintetico-*` e prints somente sintéticos.
- Bloco 32.1 decide que pagina publica nao pode renderizar enum/status/snake_case tecnico ao visitante. `PENDENTE_POLITICA_EXPOSICAO_WHATSAPP_PUBLICO` e `conteudo_autorizado` devem ficar restritos a contrato/codigo, com rotulos publicos humanos na UI. A permissao continua sendo decisao do backend.
- Bloco 33 cria checkpoint local `f6189f0` do Bloco 32.1 e valida `/anunciar` com recurso Docker descartavel `topsv3-wizard-sintetico-*`. Recursos TopsWI/cripto e `topsv3-bloco29-*` permanecem intocados.
- Bloco 33.1 mantem o Bloco 33 materialmente OK; o checkpoint local dos Blocos 33/33.1 foi criado no Bloco 34 em `b7f5f98`, sem remote e sem push.
- Bloco 34 reorienta o escopo para paridade visual/funcional do wizard `/anunciar` com a producao observavel. Admin/moderacao do anuncio criado pelo wizard fica adiado para o Bloco 35.
- Bloco 34 decide que nao se completa age gate em producao sem autorizacao explicita. A consulta publica a `/anunciar` ficou limitada ao redirecionamento `/?next=/anunciar` e aos elementos publicos observaveis antes do aceite.
- Bloco 34 mantem o wizard local sem stores, upload real, pagamento real, Pix/Efi real, e-mail real, WhatsApp real e publicacao automatica.
- Bloco 35 corrige a copy publica de bastidor do wizard, cria checkpoint local corrigido do Bloco 34 em `9b677ea` e valida admin/moderacao sintetica local com `topsv3-admin-sintetico-*`.
- Bloco 35 decide que a UI admin pode formatar rótulos de enums/status para humanos, sem alterar DTOs, contratos, RBAC, backend ou regras. `UPPER_SNAKE_CASE` nao deve aparecer no texto renderizado admin validado pelo bloco.
- Bloco 35 confirma que recursos `cripto-*`/TopsWI podem ser detectados por diagnostico, mas nao podem ser parados, removidos, alterados ou usados.
- Bloco 36 cria checkpoint local do Bloco 35 em `00e1a02` e valida Premium/beneficios sinteticos como leitura/adicao local, sem promessa de contratacao, sem limitar gratuito e sem Pix/Efi real, checkout, pagamento, credito real ou webhook.
- Bloco 36 decide que a UI admin de Premium deve formatar codigos de beneficio/status/consistencia como rotulos humanos, mantendo DTOs e contratos tecnicos intactos.
- Bloco 37 decide que copy tecnica/sem acento detectada em prints deve ser bloqueada por validadores renderizados. `Metadados publicos locais`, enum `ANUNCIO`, `Autorizacao`, `admin configurar`, `anuncio ler` e `Preparar autorizacao` nao podem aparecer como texto visivel ao usuario.
- Bloco 38 decide que pares redundantes de status publico tambem sao copy tecnica de apresentacao. `Fluxo autorizado` e `Autorização autorizada` devem ser substituidos por copy natural sem alterar regra de autorizacao.
- Bloco 56 registra `BLOQUEADO_PARIDADE_VISUAL_PRODUCAO`: a V3 local nao pode seguir para homologacao/cutover enquanto parecer skeleton tecnico frente a producao publica atual.
- Bloco 57 decide executar paridade visual publica por fases. A fase 1 pode ajustar shell/header, home, cards e listagens de cidade/bairro, mas nao encerra o gate visual sem revisao completa de detalhe de anuncio, wizard, admin quando aplicavel e aprovacao humana/Pro.
- Bloco 58 decide usar `C:\clone\topsdojob-frontend` como fonte visual local de producao em modo somente leitura. O transplante deve adaptar visual para a arquitetura V3, nao copiar fetches, auth, modais, scroll lock, dados reais, storage ou regras funcionais da producao.
- Bloco 59 decide que cards, grids e detalhe público podem aproximar a composição visual da produção, mas o contato continua mediado e o frontend não decide WhatsApp, visibilidade de mídia ou autorização.
- Bloco 60 decide que o header publico deve usar a logo real local em `/logo.webp` e que o wizard `/anunciar` pode aproximar visualmente card, progresso, botoes e espacamentos da producao, sem alterar fluxo funcional, upload, pagamento, Pix/Efi, auth, backend ou banco.
- A complementacao de deploy HML do Bloco 60 decide que `v3.esle.cloud` deve permanecer `noindex/nofollow/noarchive`, com robots `Disallow: /`, usuario `topsv3`, secrets fora do Git e Pix/Efi em mock. O workflow pode ser versionado, mas deploy/push/VPS dependem de autorizacao operacional posterior.
- Nao instalar ou baixar ferramenta/imagem automaticamente para abrir dump sensivel.
- Validacao SEO com dados sanitizados depende de restore e sanitizacao concluidos.

## MVP local sintetico consolidado

- O Bloco 41 consolida o MVP local sintetico como base de continuidade local, nao como autorizacao de homologacao, cutover ou producao.
- A consolidacao cobre apenas dados sinteticos versionaveis e ambientes descartaveis/controlados.
- Publico renderizado, SEO sintetico, wizard `/anunciar`, admin/moderacao, Premium/beneficios, Age Gate/WhatsApp, midia/fotos/stories e E2E sintetico devem permanecer gates antes de novas mudancas que afetem esses fluxos.
- Qualquer uso de dados reais/sanitizados, restore completo, financeiro real, Pix/Efi real, webhook, API externa, storage/CDN real, upload real, importador real, staging final, homologacao ou producao continua dependente de bloco proprio, revisao Pro quando aplicavel e autorizacao expressa.

## Matriz de prontidao para homologacao/cutover

- O Bloco 42 decide que prontidao local nao equivale a prontidao de homologacao ou producao.
- A matriz de prontidao deve classificar cada frente como pronta localmente, pendente antes de homologacao, bloqueante antes de producao, exige Pro, exige dados reais/sanitizados ou exige decisao humana.
- Bloco 29, restore completo, staging/homologacao, Flyway real, gitleaks real, CSRF/auth/RBAC de producao, CDN/storage, upload real, importador real, financeiro, Pix/Efi/webhooks, SEO real, backup/rollback, monitoramento, auditoria JSON e LGPD permanecem gates explicitos.
- Proximos blocos devem seguir a ordem segura documentada em `docs/v3/HOMOLOGACAO-ordem-proximos-blocos.md`, sem pular para producao, staging final ou dados reais sem autorizacao expressa.

## Banco e migrations

- V001 a V017 sao a base de schema auditada ate aqui.
- Validacao estatica e PostgreSQL descartavel passaram.
- Flyway real foi validado localmente via Docker no Bloco 47 com migrations V001 a V017 em PostgreSQL descartavel.
- Schema segue dependendo de revisao Pro antes de homologacao/producao.

## Pacote e checkpoint

- ZIP de revisao e checkpoint entre fases.
- Arquivos seguros permanecem staged quando a fase pedir.
- Commit so ocorre com autorizacao expressa.
- Push e remote continuam proibidos nas fases locais.
