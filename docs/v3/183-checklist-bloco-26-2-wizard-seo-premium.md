# Checklist Bloco 26.2 - wizard, SEO e Premium

## Preliminar

- [x] Inventario inicial criado antes das alteracoes.
- [x] Inventario salvo fora do repositorio.
- [x] Inventario em UTF-8 com BOM.
- [x] Inventario nao entrou no Git.
- [x] Consulta a producao feita somente leitura quando necessaria.
- [x] Consulta a producao documentada.
- [x] Nenhuma alteracao feita em producao.

## `/anunciar`

- [x] Formulario unico substituido por wizard progressivo.
- [x] Uma etapa por vez.
- [x] Botao Voltar.
- [x] Botao Continuar.
- [x] Validacao por etapa.
- [x] Envio apenas na revisao final.
- [x] Etapa de sucesso.
- [x] Sem upload.
- [x] Sem pagamento.
- [x] Sem Premium obrigatorio.
- [x] Sem publicacao automatica.
- [x] Sem `localStorage`.
- [x] Sem `sessionStorage`.
- [x] Sem `document.body.style.overflow`.
- [x] Sem scroll lock.

## Premium

- [x] Wizard Premium local criado.
- [x] Escolha de anuncio de exemplo.
- [x] Escolha de beneficios.
- [x] Escolha de periodo.
- [x] Revisao.
- [x] Resultado local.
- [x] Sem compra.
- [x] Sem Pix.
- [x] Sem Efi.
- [x] Sem credito real.
- [x] Sem checkout.
- [x] Sem webhook.
- [x] Sem ativacao real por dinheiro.
- [x] Sem promessa de resultado.
- [x] Gratuito preservado.

## SEO

- [x] SEO documentado como prioridade central.
- [x] Mapa de preservacao de URLs criado.
- [x] Plano cidade/bairro criado.
- [x] Checklist cutover criado.
- [x] Baseline Search Console registrado.
- [x] Script `scripts/local/validar-seo-publico-local.ps1` criado.
- [x] Rota `/anuncios/[slug]` preservada.
- [x] Rotas cidade/bairro preservadas.
- [x] Rotas alternativas proibidas continuam ausentes.
- [x] Sitemap local sem dominio de producao.
- [x] Sitemap local sem API.
- [x] Robots local seguro.
- [x] Admin noindex preservado.

## Visual e mobile

- [x] Sem redesign.
- [x] Sem nova paleta.
- [x] Sem nova tipografia.
- [x] Sem animacao automatica.
- [x] Sem elemento flutuante solto.
- [x] Sem `position: fixed`.
- [x] Sem `position: absolute`.
- [x] Sem `position: sticky`.
- [x] Sem `100vw`.
- [x] Sem scroll lock.
- [x] Sem `document.body.style.overflow`.

## Evidencias obrigatorias

- [x] 16 prints de `/anunciar` desktop/mobile gerados.
- [x] 12 prints de Premium desktop/mobile gerados.
- [x] Relatorio de prints criado em `docs/v3/evidencias/bloco-26-2/`.

## Validacoes

- [x] `scripts/security/verificar-codificacao.ps1`.
- [x] `scripts/security/verificar-arquivos-proibidos.ps1`.
- [x] `scripts/security/verificar-segredos.ps1`.
- [x] `scripts/local/validar-ui-mobile-estatica.ps1`.
- [x] `scripts/local/validar-rotas-publicas-seo-local.ps1`.
- [x] `scripts/local/validar-seo-publico-local.ps1`.
- [x] `scripts/local/validar-api-publica-local.ps1`.
- [x] `scripts/local/validar-persistencia-jpa-estatica.ps1`.
- [x] `scripts/local/validar-migrations-sql-estatico.ps1`.
- [x] `scripts/local/validar-fonte-importacao-local.ps1`.
- [x] backend compile/test quando possivel sem download.
- [x] frontend lint/build quando possivel sem download.
- [x] E2E local descartavel e smoke HTTP.
- [x] `git diff --check`.
- [x] `git diff --cached --check`.
- [x] `git status --short`.
- [x] `git remote -v`.

## Proibicoes

- [x] Sem producao alterada.
- [x] Sem VPS alterada.
- [x] Sem banco de producao.
- [x] Sem dado real.
- [x] Sem migration.
- [x] Sem SQL de schema.
- [x] Sem upload real.
- [x] Sem email real.
- [x] Sem WhatsApp real.
- [x] Sem pagamento.
- [x] Sem credito.
- [x] Sem Pix/Efi real.
- [x] Sem checkout.
- [x] Sem webhook.
- [x] Sem importador real.
- [x] Sem API externa.
- [x] Sem OpenAI.
- [x] Sem remote.
- [x] Sem push.
- [x] Sem commit.
