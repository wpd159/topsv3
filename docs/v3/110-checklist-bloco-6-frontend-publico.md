# Checklist Bloco 6 - Frontend publico integrado a API local

## Preparacao

- [x] Inventario inicial criado fora do repositorio.
- [x] Escopo 100% local confirmado.
- [x] Remote ausente confirmado.

## Backend

- [x] `RotaPublicaGuard` aceita `/sitemap.xml`.
- [x] `RotaPublicaGuard` aceita `/robots.txt`.
- [x] `/perfil/slug` continua rejeitado.
- [x] URL absoluta de producao continua rejeitada.
- [x] Rotas alternativas proibidas continuam bloqueadas.
- [x] Risco de paginacao publica esparsa corrigido.
- [x] Nenhuma query nativa criada.
- [x] Nenhuma migration criada.
- [x] Nenhum SQL alterado.

## Frontend

- [x] Cliente API publico criado.
- [x] Tipos frontend criados.
- [x] Pagina `/anuncios/[slug]` integrada a API local.
- [x] Pagina `/acompanhantes/[uf]/[cidade]` integrada a API local.
- [x] Pagina `/acompanhantes/[uf]/[cidade]/[bairro]` integrada a API local.
- [x] Fallback de backend indisponivel criado.
- [x] Visual atual preservado.
- [x] Nenhuma nova identidade visual criada.
- [x] Nenhum dado real usado.
- [x] Nenhuma imagem real usada.
- [x] WhatsApp publico nao exposto sem politica.
- [x] Storage key/hash/bucket nao expostos.
- [x] Nenhuma rota publica alternativa criada.

## Proibicoes

- [x] Nenhum banco de producao acessado.
- [x] Nenhuma API externa acessada.
- [x] Nenhum admin funcional criado.
- [x] Nenhuma autenticacao real criada.
- [x] Nenhuma sessao/token real criada.
- [x] Nenhum Pix/Efi funcional criado.
- [x] Nenhum financeiro funcional criado.
- [x] Nenhum endpoint de moderacao real criado.
- [x] Nenhum importador real iniciado.
- [x] Nenhum remote configurado.
- [x] Nenhum push executado.
- [x] Nenhum commit executado.

## Validacoes

- [x] Backend build executado.
- [x] Backend testes executados.
- [x] Frontend lint executado.
- [x] Frontend build executado.
- [x] Scanners finais executados.
- [x] ZIP final criado e validado.

## Pendencias preservadas

- [x] `PENDENTE_POLITICA_EXPOSICAO_WHATSAPP_PUBLICO`.
- [x] `PENDENTE_URL_PUBLICA_MIDIA_CDN`.
- [x] Nenhuma fase posterior iniciada.

## Complemento Bloco 7

- [x] Integracao frontend/API validada em e2e local descartavel.
- [x] Backend local iniciou contra PostgreSQL descartavel.
- [x] Smoke HTTP da API publica passou.
- [x] Frontend lint/build continuou OK.
