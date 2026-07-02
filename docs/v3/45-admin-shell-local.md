# Admin shell local

## Objetivo

Criar a estrutura visual e de navegação local do admin da V3, sem autenticação funcional, sem dados reais, sem backend de domínio e sem ações reais.

O shell admin desta fase é apenas estrutural. Ele não pode ser tratado como painel administrativo funcional.

Ele também não representa a identidade visual final do admin. As classes e cards locais são placeholders estruturais; qualquer consolidação visual futura deve preservar o visual atual do Tops do Job e aguardar fonte visual confiável do legado.

## Rotas criadas

```text
/admin
/admin/moderacao
/admin/anuncios
/admin/usuarios
/admin/midia
/admin/premium
/admin/creditos
/admin/financeiro
/admin/seo
/admin/banners
/admin/comercial
/admin/suporte
/admin/backup
/admin/auditoria
```

Todas as rotas usam metadata `noindex` local por meio dos helpers já existentes.

## Componentes criados

```text
frontend/src/modules/admin/shell/AdminShell.tsx
frontend/src/modules/admin/shell/AdminModuleCard.tsx
frontend/src/modules/admin/shell/AdminPlaceholderPage.tsx
frontend/src/modules/admin/shell/adminModules.ts
```

## Limites

Esta fase não cria:

- autenticação real;
- login real;
- RBAC funcional;
- dados reais;
- consulta a backend;
- ações de aprovar, excluir, pagar, ativar, moderar ou alterar;
- integração externa;
- Pix/Efí;
- financeiro real;
- moderação real;
- migration;
- SQL;
- banco;
- entidade JPA;
- repository;
- service de negócio.

## Fases futuras

Autenticação, permissões e RBAC para `ADMIN`, `MODERADOR` e `COMERCIAL` serão implementados em fase futura.

Moderação real, financeiro, créditos, Pix, auditoria persistente, backup operacional e textos SEO administráveis também ficam para fases futuras.

Textos SEO do painel administrativo atual serão migrados pelo importador de dados, não por captura manual de produção.

## Complemento Bloco 12

O shell admin passa a consumir a autenticacao administrativa local minima:

- `POST /api/admin/auth/login`;
- `POST /api/admin/auth/logout`;
- `GET /api/admin/auth/me`;
- `GET /api/admin/auth/permissions`.

Continua sem dados reais, sem acao administrativa critica, sem moderacao real, sem financeiro/Pix e sem importador real.

## Complemento Bloco 14

O shell admin passa a consumir tambem resumos locais somente leitura:

- `/api/admin/visao-geral`;
- `/api/admin/anuncios/resumo`;
- `/api/admin/moderacao/resumo`;
- `/api/admin/midias/resumo`;
- `/api/admin/metricas/resumo`;
- `/api/admin/sistema/status`.

As chamadas usam `credentials: "include"` e exibem apenas contadores/estados agregados. O shell continua sem botoes funcionais de aprovacao, rejeicao, exclusao, pagamento, credito, upload, Pix ou moderacao real.

O frontend nao usa localStorage/sessionStorage e apenas reflete o estado autenticado retornado pelo backend.
