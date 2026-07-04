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
2. staging;
3. dry-run real autorizado;
4. homologacao validada;
5. producao com backup e rollback.

Bloqueios:

- fonte real nao autorizada;
- Flyway real pendente;
- gitleaks pendente sem decisao formal;
- CSRF producao pendente;
- auditoria JSON Pro pendente;
- CDN/storage pendente;
- Pix/Efi pendente;
- SEO real pendente;
- deploy/cutover sem rollback.

## Gate de importador real

Antes de fonte real:

- autorizacao formal;
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
- separacao de midia publica e privada;
- regra de documento privado;
- assinatura/expiracao quando aplicavel;
- auditoria de acesso;
- DTO publico sem storage interno;
- validacao mobile de placeholders/midia.

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
