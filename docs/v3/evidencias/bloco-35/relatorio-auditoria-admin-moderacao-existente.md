# Relatorio - auditoria de rotas admin/moderacao existentes

## Rotas frontend existentes

- `/admin`;
- `/admin/anuncios`;
- `/admin/auditoria`;
- `/admin/backup`;
- `/admin/banners`;
- `/admin/comercial`;
- `/admin/creditos`;
- `/admin/desempenho`;
- `/admin/financeiro`;
- `/admin/midia`;
- `/admin/moderacao`;
- `/admin/premium`;
- `/admin/seo`;
- `/admin/suporte`;
- `/admin/usuarios`.

O shell admin local renderiza sessao/RBAC, resumos, moderacao, outbox e cards de modulos. A rota `/admin/moderacao` reutiliza o shell com pagina de modulo em preparacao.

## Endpoints admin existentes

- `POST /api/admin/auth/login`;
- `POST /api/admin/auth/logout`;
- `GET /api/admin/auth/me`;
- `GET /api/admin/auth/permissions`;
- `GET /api/admin/visao-geral`;
- `GET /api/admin/anuncios/resumo`;
- `GET /api/admin/moderacao/resumo`;
- `GET /api/admin/midias/resumo`;
- `GET /api/admin/metricas/resumo`;
- `GET /api/admin/sistema/status`;
- `GET /api/admin/anuncios`;
- `GET /api/admin/anuncios/{id}`;
- `GET /api/admin/anuncios/{id}/midias`;
- `GET /api/admin/midias`;
- `GET /api/admin/midias/{id}`;
- `GET /api/admin/moderacao/revisoes`;
- `GET /api/admin/moderacao/revisoes/{id}`;
- `POST /api/admin/moderacao/revisoes/{id}/decidir`;
- `POST /api/admin/midias/{id}/decidir`;
- `POST /api/admin/anuncios/{id}/remeter-revisao`;
- `GET /api/admin/outbox`;
- `GET /api/admin/outbox/{id}`;
- `GET /api/admin/outbox/{id}/preview`;
- `POST /api/admin/outbox/{id}/simular-processamento-local`;
- endpoints read-only de Premium, creditos, pagamentos e desempenho documentados no OpenAPI local.

## Acoes locais/sinteticas existentes

- Login admin local com cookie/sessao, sem token bearer.
- Leitura/listagem/detalhe administrativo local.
- Decisao local de revisao: aprovar, reprovar e solicitar ajuste.
- Decisao local de midia: aprovar e reprovar.
- Remeter anuncio para revisao.
- Outbox read-only com preview sanitizado.
- Simulacao local de processamento de outbox restrita a ADMIN.

## Protecoes observadas

- Endpoints admin exigem sessao/RBAC.
- COMERCIAL e USUARIO sinteticos sao bloqueados nas acoes moderatorias.
- MODERADOR acessa moderacao e decide revisao/midia, mas nao simula outbox.
- REPROVAR exige motivo.
- `SOLICITAR_AJUSTE` nao cria decisao final indevida.
- Auditoria mascara e-mail, contato e documento.
- Outbox nao envia e-mail, WhatsApp, SMS, webhook, SMTP, fila externa ou API externa.
- O anuncio criado por `/api/public/anunciar` fica `PENDENTE_REVISAO`, com revisao aberta e sem autopublicacao.

## Acoes proibidas neste bloco

- Hard delete.
- Upload real.
- E-mail real.
- WhatsApp real.
- Pagamento, Pix/Efi ou checkout real.
- Worker/scheduler real.
- API externa.
- Producao/VPS/banco de producao.
- Restore, sanitizacao real ou uso da quarentena como staging.

## Pendencias objetivas

- Autenticacao/RBAC de producao ainda exige fase e revisao Pro.
- Auditoria JSON para homologacao/producao continua pendencia Pro.
- Bloco 29 permanece aberto e adiado para pre-staging/cutover.
- Quarentena sem `POST_DATA` nao e staging final.
