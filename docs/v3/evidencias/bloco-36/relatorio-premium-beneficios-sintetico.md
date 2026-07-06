# Relatorio Premium/beneficios sintetico local

- Resultado: OK_PREMIUM_BENEFICIOS_SINTETICO_LOCAL
- Backend local: http://127.0.0.1:18136
- Frontend local: http://127.0.0.1:18336
- Smoke base executado: sim
- Dados reais: nao
- Producao/VPS/API externa: nao
- Pix/Efi real, checkout, pagamento, credito real ou webhook: nao
- Compra/ativacao real: nao
- Promessa de contratacao: nao
- Plano gratuito: validado como util e sem limite comercial artificial
- Premium: validado como aditivo

## Checks

- OK: smoke base de API/admin/moderacao - exit=0
- OK: login admin local sintetico - status=200
- OK: publico premium ativo visivel - status=200
- OK: publico premium aditivo - beneficios=Midia extra, Destaque, Stories
- OK: publico premium sem promessa ou acao real - sem checkout/Pix/Efi/pagamento/credito/webhook real ou promessa
- OK: publico gratuito visivel e util - status=200
- OK: publico gratuito sem beneficio artificial - beneficios=
- OK: publico gratuito sem promessa ou acao real - sem checkout/Pix/Efi/pagamento/credito/webhook real ou promessa
- OK: gratuito sem limite comercial de clique/WhatsApp - status=200
- OK: admin premium ativo - status=200
- OK: admin premium sem compra real - read-only
- OK: admin gratuito sem limite contato - gratuitoLimitadoPorContato=False
- OK: admin beneficios destaque e fotos extra - codigos=FOTOS_EXTRA, DESTAQUE, STORIES
- OK: admin beneficio vencendo - status=VENCENDO, VENCENDO, VENCENDO
- OK: admin beneficio expirado por grupo conjunto - beneficio ANUNCIO_TOPO deve expirar junto com grupo expirado
- OK: admin plano gratuito continua consultavel - status=200
- OK: admin consistencia expiracao conjunta - codigos=BENEFICIO_EXPIRADO_ANTES_DO_GRUPO, GRUPO_EXPIRADO_COM_BENEFICIO_ATIVO, GRUPO_SEM_BENEFICIOS
- OK: admin consistencia beneficio antes do grupo - codigos=BENEFICIO_EXPIRADO_ANTES_DO_GRUPO, GRUPO_EXPIRADO_COM_BENEFICIO_ATIVO, GRUPO_SEM_BENEFICIOS
- OK: admin consistencia grupo sem beneficios - codigos=BENEFICIO_EXPIRADO_ANTES_DO_GRUPO, GRUPO_EXPIRADO_COM_BENEFICIO_ATIVO, GRUPO_SEM_BENEFICIOS
- OK: admin vencendo janela sete dias - janela=7; codigos=FOTOS_EXTRA, RELATORIO, DESTAQUE, STORIES
- OK: premium sem metodo POST - status=400
- OK: premium sem metodo PUT - status=400
- OK: premium sem metodo PATCH - status=400
- OK: premium sem metodo DELETE - status=400
- OK: UI publica/admin premium - exit=0

## Fluxos Premium/beneficios cobertos

- Anuncio gratuito publico visivel.
- Premium ativo publico/admin.
- Premium expirado por grupo expirado.
- Beneficio vencendo.
- Expiracao conjunta e inconsistencias sinteticas.
- Endpoint admin read-only sem POST/PUT/PATCH/DELETE operacional.
- UI publica/admin sem enum tecnico visivel.

## Saida do smoke base

- Validacao smoke HTTP da API publica local
- BaseUrl=http://127.0.0.1:18136
- Total de verificacoes: 1089
- Verificacoes OK: 1089
- Verificacoes com falha: 0
- VALIDATION_RESULT=OK_API_PUBLICA_LOCAL

## Resultado do wrapper descartavel

- Relatorio E2E descartavel: C:\topsv3\docs\v3\evidencias\bloco-36\relatorio-e2e-premium-beneficios-sintetico.md
- Exit code E2E: 0
- Prefixo Docker usado: topsv3-premium-sintetico-*
- Recurso topsv3-bloco29 usado: nao
- TopsWI/cripto alterado: nao
