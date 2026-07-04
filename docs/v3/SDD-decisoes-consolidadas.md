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
