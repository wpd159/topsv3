# HOMOLOGACAO - Secrets externos

## Regra central

Secrets de homologacao nunca devem entrar no Git, ZIP de revisao, chat, logs, prints ou relatorios versionados. Este documento lista nomes e responsabilidades, sem valores reais.

## Categorias

| Categoria | Exemplos de variaveis | Regra |
| --- | --- | --- |
| Banco | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Banco isolado; sem permissao em producao. |
| Sessao e idade | `APP_AGE_GATE_SIGNING_VALUE`, `APP_ADMIN_SESSION_COOKIE_SECURE` | Valor real fora do Git; cookie seguro em homologacao. |
| Eventos | `APP_EVENT_HASH_SALT` | Usado para hash/minimizacao; rotacao definida fora do repo. |
| Storage futuro | `STORAGE_ENDPOINT`, `STORAGE_PUBLIC_BUCKET`, `STORAGE_PRIVATE_BUCKET`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY` | Pendente ate bloco de storage/upload real. |
| Pix/Efi futuro | credenciais de homologacao do provedor | Proibidas neste bloco; exigem fase propria. |
| Webhook futuro | credenciais e assinatura de webhook | Proibidas neste bloco; exigem fase propria. |

## Politica operacional

- Valor real nao deve ser escrito em arquivo versionado.
- Valor real nao deve aparecer em log de build, relatorio ou screenshot.
- `.env` real permanece fora do repositorio.
- Exemplos podem existir apenas com placeholders seguros.
- Acesso a secrets deve ser limitado por papel e auditavel.
- Rotacao deve ser planejada antes de qualquer cutover.

## Validacao antes de homologacao

- `scripts/security/verificar-segredos.ps1`
- gitleaks real no ambiente local/controlado.
- Conferencia manual de `.env` real fora do Git.
- Revisao Pro antes de credenciais reais, dados reais/sanitizados ou integracoes externas.
