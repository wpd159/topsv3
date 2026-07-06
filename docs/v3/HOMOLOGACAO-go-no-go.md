# HOMOLOGACAO - Matriz Go/No-Go

## Principio

Go/No-Go deve ser objetivo, auditavel e conservador. Pronto localmente nao equivale a pronto para homologacao real ou producao.

## Go para homologacao

- Contrato de ambiente aprovado.
- Secrets externos definidos fora do Git.
- CORS/cookies/CSRF nao-local validados.
- Flyway real repetido no ambiente.
- Gitleaks real repetido.
- Banco de homologacao isolado.
- Storage/upload/CDN real definido quando o escopo exigir.
- Backup/rollback testado.
- Monitoramento minimo ativo.
- Pro aplicado quando houver dados reais/sanitizados.

## No-Go para homologacao

- Remote/push indevido.
- Secret versionado.
- Dados reais no repositorio.
- Restore incompleto tratado como staging final.
- Quarentena sem `POST_DATA` promovida a base final.
- CORS wildcard com credenciais.
- CSRF nao-local pendente.
- Documento privado publicavel.
- Storage key ou URL privada em DTO publico.
- Logs com dado sensivel bruto.

## Go para cutover

- Homologacao validada.
- SEO real validado antes/depois.
- Backup antes do cutover.
- Rollback com responsavel e tempo maximo definido.
- Monitoramento e alertas ativos.
- Financeiro/Pix/Efi/webhooks validados em homologacao quando no escopo.
- Importacao real validada com dry-run e relatorio de divergencias aprovado.
- Revisao Pro/humana final.

## No-Go para producao

- Bloco 29/restore completo pendente quando dados reais/sanitizados forem necessarios.
- 45 URLs desconhecidas de SEO sem decisao, se ainda existirem.
- Falha de conciliacao financeira.
- Webhook sem idempotencia.
- Documento privado exposto.
- Midia rejeitada/pendente com URL publica.
- Sem rollback testado.
- Sem monitoramento.
- Auditoria JSON final pendente quando houver dados reais.
- Pendencia LGPD/juridica bloqueante.

## Criterios de rollback

- Incidente de dado sensivel.
- Erro 5xx persistente.
- Indisponibilidade de paginas publicas criticas.
- Login/admin indisponivel.
- Falha de pagamento/webhook quando financeiro estiver habilitado.
- Perda SEO anormal conforme limite aprovado.
- Importacao com divergencia critica nao prevista.

## Decisao humana

Mesmo com checks tecnicos OK, cutover exige decisao humana registrada. Pro e obrigatorio antes de dados reais/sanitizados operacionais, financeiro real, Pix/Efi real, webhook real, importador real ou producao.
