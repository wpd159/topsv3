# Mapa dos módulos do admin

## Visão geral

O admin da V3 será organizado por módulos. A Fase 1C.6B cria apenas o mapa estrutural local, com páginas placeholder e navegação entre módulos.

## Módulos previstos

| Módulo | Rota | Perfis futuros | Escopo futuro |
| --- | --- | --- | --- |
| Visão geral | `/admin` | ADMIN, MODERADOR, COMERCIAL | Entrada estrutural do admin e indicadores futuros. |
| Moderação | `/admin/moderacao` | ADMIN, MODERADOR | Revisão de anúncios, mídia e decisões de conteúdo. |
| Anúncios | `/admin/anuncios` | ADMIN, MODERADOR, COMERCIAL | Gestão futura de anúncios e estados operacionais. |
| Usuários | `/admin/usuarios` | ADMIN | Contas, papéis, permissões e sessões. |
| Mídia | `/admin/midia` | ADMIN, MODERADOR | Arquivos, documentos, revisão e vínculos. |
| Premium | `/admin/premium` | ADMIN, COMERCIAL | Benefícios, ativações e destaques. |
| Créditos | `/admin/creditos` | ADMIN, COMERCIAL | Saldos, movimentos e ajustes auditáveis. |
| Financeiro | `/admin/financeiro` | ADMIN | Pagamentos, conciliação e Pix em fases futuras. |
| SEO | `/admin/seo` | ADMIN, COMERCIAL | URLs, metadados, sitemap, robots e conteúdo público. |
| Banners | `/admin/banners` | ADMIN, COMERCIAL | Espaços, versões e publicação revisada. |
| Comercial | `/admin/comercial` | ADMIN, COMERCIAL | Pipeline comercial e acompanhamento. |
| Suporte | `/admin/suporte` | ADMIN, MODERADOR, COMERCIAL | Tickets, mensagens e atendimento. |
| Backup | `/admin/backup` | ADMIN | Políticas, execuções e testes de restauração. |
| Auditoria | `/admin/auditoria` | ADMIN | Eventos auditáveis e comandos críticos. |

## Regras estruturais

- Toda página admin deve ser `noindex`.
- Rotas admin não entram no sitemap público.
- Rotas admin não podem chamar backend de domínio até a fase autorizada.
- Ações críticas futuras devem exigir autenticação, RBAC, autorização e auditoria.
- Dados reais não devem ser usados em skeleton local.
- Produção, remote, push e commit continuam proibidos nesta fase.

## Textos SEO do painel atual

Textos SEO do painel administrativo atual serão tratados pelo importador de dados em fase futura. A Fase 1C.6B não continua captura manual de textos da produção.

## Complemento Bloco 12

RBAC local minimo criado para preparar os modulos:

- `ADMIN`;
- `MODERADOR`;
- `COMERCIAL`;
- `USUARIO`.

As permissoes estao documentadas em `docs/v3/53-matriz-permissoes-rbac.md` e `docs/v3/54-mapa-admin-permissoes.md`.

Nenhum modulo administrativo ganhou acao real neste bloco.

## Complemento Bloco 14

O mapa estrutural passa a ter leitura local agregada para visao geral, anuncios, moderacao, midia, metricas e status do sistema.

Essas leituras nao mudam o mapa de modulos e nao liberam acao critica. Modulos de usuarios, premium, creditos, financeiro, SEO, banners, comercial, suporte, backup e auditoria seguem sem funcionalidade real neste bloco.

## Complemento Bloco 26

O fluxo `Anuncie gratis` e publico e nao adiciona modulo administrativo novo.

A solicitacao local criada pelo endpoint publico fica visivel no modulo de moderacao existente como anuncio pendente e revisao aberta. Isso nao libera nova acao admin, nao publica automaticamente e nao altera o mapa de permissoes.
