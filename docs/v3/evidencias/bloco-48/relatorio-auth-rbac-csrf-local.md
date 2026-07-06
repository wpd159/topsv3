# Relatorio Auth/RBAC/CSRF local - Bloco 48

- Gerado em: 2026-07-06 19:10:56 -03:00
- Base local validada: http://127.0.0.1:18148
- Resultado: OK_AUTH_RBAC_CSRF_LOCAL
- Dados usados: sinteticos locais.
- Producao/VPS/dados reais/restore/staging/API externa: nao utilizados.
- Valores de cookie, credencial e tokens: nao registrados.

## Resultado dos cenarios
- OK - health local acessivel: Status HTTP: 200.
- OK - CORS local restrito e coerente: Status HTTP: 200; origem liberada localhost; credenciais: true.
- OK - API desconhecida bloqueada: Status HTTP: 401; sem resposta publica normal.
- OK - rota admin sem sessao bloqueada: Status HTTP: 401.
- OK - erro sem sessao sem stack trace: Resposta sanitizada sem token, cookie ou stack trace.
- OK - login invalido recusado: Status HTTP: 401.
- OK - login invalido sem stack trace: Erro padronizado e sanitizado.
- OK - login admin local OK: Status HTTP: 200.
- OK - cookie de sessao HttpOnly e SameSite: Cookie de sessao presente; atributos HttpOnly/SameSite=Lax verificados sem registrar valor.
- OK - login admin sem vazamento sensivel: Resposta de login nao expoe hash, cookie, token ou stack trace.
- OK - sessao admin reconhecida: Status HTTP: 200; papel ADMIN presente.
- OK - me admin sem segredo: DTO administrativo nao expoe credencial ou cookie.
- OK - ADMIN com permissoes esperadas: Status HTTP: 200; permissoes esperadas encontradas.
- OK - ADMIN acessa status esperado: Status HTTP: 200.
- OK - status admin sem segredo: Resposta administrativa sem cookie, token ou stack trace.
- OK - logout admin local OK: Status HTTP: 200.
- OK - sessao invalida apos logout: Status HTTP: 401.
- OK - login moderador local OK: Status HTTP: 200.
- OK - login moderador sem vazamento sensivel: Resposta de login sanitizada.
- OK - MODERADOR sem acesso a configuracao sensivel: Status HTTP: 403.
- OK - MODERADOR sem acesso financeiro creditos: Status HTTP: 403.
- OK - MODERADOR sem acesso financeiro pagamentos: Status HTTP: 403.
- OK - MODERADOR acessa moderacao esperada: Status HTTP: 200.
- OK - MODERADOR sem dados sensiveis em moderacao: Resposta sem identificador documental privado, credencial, cookie ou stack trace.
- OK - CSRF local documentado no codigo: Ambiente local usa CSRF desabilitado para smoke controlado.
- OK - CSRF nao-local usa repositorio CSRF: Configuracao nao-local possui repositorio CSRF.
- OK - fallback /api bloqueado por seguranca: Fallback de API e demais rotas negados.
- OK - cookie HttpOnly no YAML: Cookie de sessao configurado como HttpOnly.
- OK - cookie SameSite Lax no YAML: Cookie de sessao configurado com SameSite=Lax.
- OK - cookie seguro fora do local: Default nao-local mantem cookie seguro.
- OK - cookie local sem Secure por localhost: Local permite cookie de sessao em HTTP apenas para localhost.
- OK - UI admin sem cookie/storage/stack bruto: Arquivos com padrao inseguro: 0.

## CSRF

- Local: CSRF desabilitado para smoke controlado com PostgreSQL descartavel e localhost.
- Nao-local: SecurityConfig contem repositorio CookieCsrfTokenRepository; revisao Pro antes de homologacao/producao permanece obrigatoria.
- Pendencia de producao: validar token CSRF real com frontend, cookie seguro HTTPS, CORS final e politica de sessao em ambiente homologado.

## Pendencias
- Nenhuma falha local encontrada no escopo Auth/RBAC/CSRF do Bloco 48.
