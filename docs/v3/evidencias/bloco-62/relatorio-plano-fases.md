# Relatorio - Plano de fases para migracao do frontend

## Objetivo

Substituir gradualmente o frontend V3 atual pela base visual/funcional de `C:\clone\topsdojob-frontend`, sem mexer em backend, banco, producao, dados reais, Pix/Efi, upload real, webhook real ou push.

## Fase 63 - Matriz de migracao

- Criar matriz rota por rota.
- Mapear dependencias NPM necessarias.
- Definir adapters V3 para cada chamada antiga.
- Separar o que sera copiado, reimplementado ou descartado.
- Validar que HML permanece noindex/nofollow.

Saida esperada: plano executavel de troca sem alteracao funcional real.

## Fase 64 - Shell visual de producao

- Incorporar layout, CSS global, fonte, header, footer e assets aprovados.
- Manter dados sinteticos e adapters V3.
- Validar home, header e mobile.
- Nao migrar detalhe nem wizard ainda se o risco estiver alto.

Saida esperada: V3 com cara de producao no shell publico.

## Fase 65 - Rotas publicas e SEO

- Migrar estrutura das rotas publicas.
- Adaptar metadata, canonical, robots e sitemap para HML/local.
- Garantir que URLs antigas continuem mapeadas.
- Bloquear canonical de producao em HML.

Saida esperada: rotas publicas com estrutura de producao e SEO seguro.

## Fase 66 - Listagens, filtros, cards e grids

- Migrar cards e grids.
- Migrar filtros visuais.
- Adaptar listagem cidade e bairro para DTO V3.
- Garantir que clique WhatsApp nao use fluxo real antigo.

Saida esperada: listagens com paridade visual e dados V3.

## Fase 67 - Detalhe do anuncio

- Migrar layout do detalhe.
- Adaptar midia, placeholders, stories e CTA.
- Garantir LIVRE/BLOQUEADO decidido pelo backend.
- Garantir que BLOQUEADO nao exponha midia sensivel nem WhatsApp sem regra V3.

Saida esperada: detalhe visualmente proximo da producao e seguro na V3.

## Fase 68 - Wizard /anunciar

- Migrar experiencia visual do wizard.
- Substituir stores e chamadas por contrato V3.
- Manter upload real, pagamento, Premium obrigatorio e autopublicacao desabilitados.
- Validar textos, mobile e pos-envio.

Saida esperada: wizard com UX de producao e comportamento V3 sintetico/controlado.

## Fase 69 - Admin e areas privadas

- Decidir o que permanece da V3 atual.
- Nao misturar auth antigo com RBAC V3.
- Reestilizar admin somente se houver fase dedicada.
- Validar CSRF, cookies, logs e auditoria.

Saida esperada: admin local seguro sem regressao de auth/RBAC.

## Fase 70 - HML controlado

- Rodar build/lint/testes.
- Publicar somente com decisao expressa.
- Validar HML com dados sinteticos.
- Manter noindex/nofollow.
- Registrar bloqueios para dados reais, importacao real, Pix/Efi, upload real e cutover.

Saida esperada: HML navegavel com frontend baseado na producao, sem uso real.

## Ordem recomendada

1. Matriz de migracao.
2. Shell visual.
3. Rotas e SEO.
4. Cards/listagens/filtros.
5. Detalhe do anuncio.
6. Wizard.
7. Admin/privado.
8. HML controlado.

## Condicoes de parada

Parar a migracao se for detectado:

- dependencia de endpoint de producao;
- canonical ou sitemap apontando para producao em HML;
- uso de upload real;
- uso de pagamento, Pix/Efi ou webhook real;
- exposicao de storage key, bucket, URL privada ou documento privado;
- scroll lock ou instabilidade mobile;
- necessidade de backend/banco fora do escopo.
