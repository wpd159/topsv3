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

## Classificacao binaria

- A V3 usa apenas `LIVRE` e `BLOQUEADO`.
- Estados intermediarios publicos ficam removidos da regra vigente.
- Frontend nao decide classificacao.
- Moderacao futura decide `LIVRE` ou `BLOQUEADO`.
- Nao ha age gate intermediario, blur por categoria intermediaria ou desbloqueio parcial por visitante.
- `LIVRE` aparece sem confirmacao de idade.
- `BLOQUEADO` so pode ser liberado de forma controlada apos confirmacao de idade pelo backend.
- Stories exigem confirmacao de idade.

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
- Backend decide se contato pode ser liberado.
- Conteudo bloqueado nao libera WhatsApp publico como anuncio normal.

## Midia segura

- Documento privado nunca e publicavel.
- `urlPublica` permanece nula ate CDN/storage publico aprovado.
- DTO publico nao expoe bucket, storage key, provider, hash, etag ou URL privada.
- Upload real continua fora do escopo atual.

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

## SEO central

- SEO e prioridade central da V3.
- Preservar `/anuncios/[slug]`, `/acompanhantes/[uf]/[cidade]`, `/acompanhantes/[uf]/[cidade]/[bairro]` e `/anunciar`.
- Nao criar rotas paralelas publicas de anuncio.
- Crescimento local deve mirar `acompanhante em [cidade]`.
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
- Bloco 32 cria checkpoint local `790188b` dos Blocos 31/31.1 e valida rotas publicas principais renderizadas com dados sinteticos. Docker fica restrito a `topsv3-render-sintetico-*`, prints versionados devem conter apenas dados sinteticos e `BLOQUEADO` nao pode expor WhatsApp publico indevido.
- Bloco 32.1 decide que pagina publica nao pode renderizar enum/status/snake_case tecnico ao visitante. `PENDENTE_POLITICA_EXPOSICAO_WHATSAPP_PUBLICO` e `conteudo_autorizado` devem ficar restritos a contrato/codigo, com rotulos publicos humanos na UI. A permissao continua sendo decisao do backend.
- Bloco 33 cria checkpoint local `f6189f0` do Bloco 32.1 e valida `/anunciar` com recurso Docker descartavel `topsv3-wizard-sintetico-*`. Recursos TopsWI/cripto e `topsv3-bloco29-*` permanecem intocados.
- Bloco 33.1 mantem o Bloco 33 materialmente OK e deixa o checkpoint do delta Bloco 33/33.1 pendente para bloco posterior, sem commit e sem push.
- Nao instalar ou baixar ferramenta/imagem automaticamente para abrir dump sensivel.
- Validacao SEO com dados sanitizados depende de restore e sanitizacao concluidos.

## Banco e migrations

- V001 a V017 sao a base de schema auditada ate aqui.
- Validacao estatica e PostgreSQL descartavel passaram.
- Flyway real ainda depende de CLI/imagem disponivel localmente.
- Schema segue dependendo de revisao Pro antes de homologacao/producao.

## Pacote e checkpoint

- ZIP de revisao e checkpoint entre fases.
- Arquivos seguros permanecem staged quando a fase pedir.
- Commit so ocorre com autorizacao expressa.
- Push e remote continuam proibidos nas fases locais.
