# Checklist da Fase 1B

## Correções Herdadas da Fase 1A

- [x] imagens Docker sem tag `latest`.
- [x] `infra/local/.env.local.example` criado.
- [x] `EFI_PIX_MOCK_MODE=true` configurado.
- [x] `APP_ENV=local` configurado.
- [x] `APP_CANONICAL_DOMAIN=http://localhost` configurado.
- [x] Compose local consome `infra/local/.env.local.example`.
- [x] scripts locais principais criados.
- [x] scripts antigos mantidos como aliases.
- [x] docs da Fase 1A padronizados.

## Skeleton Backend

- [x] `backend/pom.xml` criado.
- [x] classe Spring Boot principal criada.
- [x] health controller local criado.
- [x] `application.yml` local criado.
- [x] nenhuma migration criada.
- [x] nenhum SQL criado.
- [x] nenhuma entidade JPA de negócio criada.
- [x] nenhum repository de domínio criado.
- [x] nenhum service de negócio criado.

## Skeleton Frontend

- [x] `frontend/package.json` criado.
- [x] `frontend/next.config.mjs` criado.
- [x] layout mínimo criado.
- [x] página local de skeleton criada.
- [x] página local de health criada.
- [x] nenhuma jornada pública real criada.
- [x] nenhum painel admin funcional criado.

## Contratos

- [x] diretório `contracts/` criado.
- [x] OpenAPI local de health criado.
- [x] nenhum contrato de domínio fora do escopo criado.

## Validações

- [x] script `validar-skeleton-local.ps1` criado.
- [x] validação do skeleton executada.
- [x] scanners de segurança executados.
- [x] testes dos hooks executados.
- [x] testes do pacote executados.
- [x] `git diff --check` executado.
- [x] `git diff --cached --check` executado.

## Política Local

- [x] nenhum remote configurado.
- [x] nenhum push executado.
- [x] nenhum commit executado.
- [x] nenhuma ferramenta instalada automaticamente.
- [x] nenhuma dependência baixada automaticamente.
- [x] nenhuma conexão com produção ou Efí real.
- [x] ZIP de revisão gerado na Área de Trabalho.
