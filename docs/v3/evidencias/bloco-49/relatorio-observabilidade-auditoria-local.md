# Relatorio observabilidade/auditoria local - Bloco 49

- Gerado em: 2026-07-06 18:48:13 -03:00
- Base local validada: http://127.0.0.1:18149
- Resultado: OK_OBSERVABILIDADE_AUDITORIA_LOCAL
- Dados usados: sinteticos locais.
- Producao/VPS/dados reais/restore/staging/API externa: nao utilizados.
- Valores de cabecalhos, cookies, credenciais e segredos: nao registrados.
- Log local inspecionado: arquivo temporario do backend E2E, sem incluir conteudo bruto.

## Resultado dos cenarios
- OK - request id propagado em health: Cabecalho e corpo contem requestId sintetico.
- OK - health sem dados sigilosos: Resposta health sanitizada.
- OK - request id invalido substituido: Header invalido nao foi reutilizado.
- OK - erro 400 status esperado: Status HTTP: 400.
- OK - erro 400 com request id na resposta: Cabecalho X-Request-Id propagado.
- OK - erro 400 corpo seguro: Corpo de erro com status, codigo e requestId.
- OK - erro 400 sem dados sigilosos: Corpo sem segredo, documento, contato bruto, IP bruto ou stack trace.
- OK - erro 401 status esperado: Status HTTP: 401.
- OK - erro 401 com request id na resposta: Cabecalho X-Request-Id propagado.
- OK - erro 401 corpo seguro: Corpo nao lido pelo cliente PowerShell; writer de seguranca validado estaticamente.
- OK - erro 401 sem dados sigilosos: Corpo sem segredo, documento, contato bruto, IP bruto ou stack trace.
- OK - login moderador para 403: Sessao sintetica criada para teste de RBAC.
- OK - login sem dados sigilosos: Resposta de login sem segredo ou cookie.
- OK - erro 403 status esperado: Status HTTP: 403.
- OK - erro 403 com request id na resposta: Cabecalho X-Request-Id propagado.
- OK - erro 403 corpo seguro: Corpo nao lido pelo cliente PowerShell; writer de seguranca validado estaticamente.
- OK - erro 403 sem dados sigilosos: Corpo sem segredo, documento, contato bruto, IP bruto ou stack trace.
- OK - erro 404 status esperado: Status HTTP: 404.
- OK - erro 404 com request id na resposta: Cabecalho X-Request-Id propagado.
- OK - erro 404 corpo seguro: Corpo de erro com status, codigo e requestId.
- OK - erro 404 sem dados sigilosos: Corpo sem segredo, documento, contato bruto, IP bruto ou stack trace.
- OK - log local contem request id obs-bloco49-health-0850e736f33f: Linha de log encontrada sem registrar conteudo bruto.
- OK - log local contem request id c30416a4-11ca-4c2f-acf2-e7495e49d497: Linha de log encontrada sem registrar conteudo bruto.
- OK - log local contem request id obs-bloco49-400-3af11ebd5f5c: Linha de log encontrada sem registrar conteudo bruto.
- OK - log local contem request id obs-bloco49-401-b3ec61635ff2: Linha de log encontrada sem registrar conteudo bruto.
- OK - log local contem request id obs-bloco49-login-ae0994a7b2e5: Linha de log encontrada sem registrar conteudo bruto.
- OK - log local contem request id obs-bloco49-403-18a13d47e3c1: Linha de log encontrada sem registrar conteudo bruto.
- OK - log local contem request id obs-bloco49-404-b6daa680dc8f: Linha de log encontrada sem registrar conteudo bruto.
- OK - logs dos requests sem dados sigilosos: Linhas filtradas por requestId nao expoem cabecalho, IP bruto, UA bruto ou stack trace.
- OK - logs dos requests com formato minimo: Log HTTP local inclui evento, status e duracao.
- OK - logger HTTP minimo: RequestIdFilter nao registra IP bruto, cabecalho sensivel ou query string.
- OK - writer seguranca padronizado: 401/403 usam resposta JSON padronizada com requestId.
- OK - erro 500 sanitizado por codigo: Handler inesperado retorna erro generico e loga classe.
- OK - auditoria sem IP e UA brutos: Auditoria local nao persiste IP bruto nem user-agent bruto.
- OK - sanitizer moderacao mascara contato: Motivos de moderacao passam por mascara.
- OK - sanitizer readonly remove segredo: Textos admin readonly removem segredo e dados pessoais.
- OK - outbox sanitizado por allowlist: Outbox admin usa chaves permitidas e bloqueia fragmentos sensiveis.
- OK - eventos moderacao rastreaveis: Acoes admin gravam evento com requestId.

## Pendencias de producao

- Definir formato final de logs estruturados JSON antes de homologacao/producao.
- Definir hashing real de IP e user-agent, retencao e acesso operacional.
- Validar pipeline centralizado de logs, alertas e mascaramento em ambiente HTTPS.
- Revisar auditoria JSON com Pro antes de usar dados reais ou producao.
