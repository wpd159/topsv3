# SDD Tops do Job V3

Documento central de Specification-Driven Development da V3. Ele consolida o estado local do projeto ate o Bloco 40, com checkpoint local `f6189f0` do Bloco 32.1, checkpoint local `b7f5f98` dos Blocos 33/33.1, checkpoint local corrigido `9b677ea` do Bloco 34, checkpoint local `00e1a02` do Bloco 35, checkpoint local `e031ea3` do Bloco 36, checkpoint local `a6f431f` do Bloco 37 corrigido, checkpoint local `587e2df` do Bloco 38, checkpoint local `a3e92c0` do Bloco 39, E2E/API/SEO sintetico aprovado, auditoria renderizada publica aprovada, validacao sintetica do wizard `/anunciar`, paridade local do wizard com a producao observavel, validacao admin/moderacao sintetica local, validacao Premium/beneficios sintetica local, validacao Age Gate/WhatsApp sintetica local aprovada, validacao de midia/fotos/stories sintetica local no Bloco 40, Bloco 29 adiado como gate de pre-staging/cutover, checkpoint local `36b94c6` dos Blocos 29 a 29.6 e base sintetica local validada para continuidade sem dados reais.

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
- Bloco 39 cria checkpoint local do Bloco 38 em `587e2df` e valida Age Gate/WhatsApp sintético local para `LIVRE`/`BLOQUEADO`, sem produção, dados reais, restore, Pix/Efi real, pagamento, API externa ou push;
- Bloco 40 cria checkpoint local do Bloco 39 em `a3e92c0` e valida mídia/fotos/stories sintéticos, placeholders seguros e sanitização de mídia admin/publica, sem upload real, CDN/storage real, dados reais, produção, restore, Pix/Efi real, pagamento, API externa ou push;
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
- SEO e prioridade central da V3 e possui documentos proprios em `docs/v3/SEO-*.md`.
- O alvo de crescimento organico local e `acompanhante em [cidade]`.
- Mudancas visuais publicas de SEO exigem `scripts/local/validar-layout-publico-renderizado.ps1`.
- Mapa de preservacao SEO e cutover exigem `scripts/local/validar-mapa-preservacao-seo-local.ps1`.
- Lista bruta completa de URLs reais de anuncios nao deve ser versionada.
- Inventario bruto SEO fica fora do repositorio.

## 5. Classificacao, idade e conteudo bloqueado

A classificacao publica da V3 e binaria:

- `LIVRE`: pode ser exibido publicamente;
- `BLOQUEADO`: nao aparece como anuncio publico normal; detalhe, stories e contato so podem ser avaliados apos confirmacao de idade pelo backend e conforme politica de midia/contato.

Regras:

- frontend nao decide classificacao;
- backend e a fonte da decisao;
- `LIVRE` aparece sem confirmacao de idade;
- `BLOQUEADO` depende de confirmacao de idade pelo backend para qualquer liberacao controlada;
- stories exigem confirmacao de idade;
- nao existe gradacao publica intermediaria;
- nao existe age gate intermediario por categoria;
- nao existe blur por categoria intermediaria;
- confirmacao de idade local usa cookie HttpOnly assinado, sem CPF, documento, conta, localStorage ou sessionStorage.

## 6. WhatsApp, contato e metricas

WhatsApp publico e sempre mediado pelo backend.

Regras:

- frontend nao renderiza WhatsApp bruto por conta propria;
- clique de WhatsApp passa pelo endpoint publico local;
- metricas minimizam IP, User-Agent e referer por hash;
- gratuito continua util e nao recebe limite comercial artificial de clique, contato ou WhatsApp;
- conteudo `BLOQUEADO` nao pode liberar WhatsApp publico como anuncio normal.

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
- gitleaks opcional quando instalado, fallback local obrigatorio;
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
- Flyway CLI/imagem real ainda pendente quando nao disponivel localmente;
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
- Blocos 5-10: API publica, frontend publico, e2e, metricas, idade e UX de conteudo bloqueado;
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
- Bloco 39: checkpoint local do Bloco 38 criado em `587e2df`; Age Gate/WhatsApp sintetico local valida `LIVRE` sem age gate, `BLOQUEADO` protegido antes da idade, menor de 18 anos bloqueado, data invalida com 400 e WhatsApp mediado pelo backend.
- Bloco 40: checkpoint local do Bloco 39 criado em `a3e92c0`; midia publica sintetica valida gratuito ate 2 fotos, Premium com midia extra aditiva, placeholders seguros, stories com idade sem URL real e admin de midia sanitizado.

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
- `BLOQUEADO` nao expos WhatsApp publico;
- pendente/rejeitado nao ficaram publicados;
- dados reais nao foram usados.

O Bloco 29 permanece adiado para pre-staging/cutover. A quarentena sem `POST_DATA` continua proibida como staging final.
