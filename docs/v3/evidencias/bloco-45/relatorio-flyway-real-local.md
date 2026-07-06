# Relatorio Flyway real local - Bloco 45

## Resultado

- VALIDATION_RESULT=PENDENTE_FLYWAY_REAL_LOCAL
- Detalhe: Flyway CLI/imagem local nao encontrados; instalacao e docker pull nao autorizados.
- Fonte Flyway: indisponivel
- Versao Flyway: indisponivel
- Imagem PostgreSQL local selecionada: postgres:17
- Diretorio de migrations: `backend/src/main/resources/db/migration`
- Migrations encontradas: 17

## Recursos Docker

- Prefixo permitido: `topsv3-flyway-local`
- Network criada: False
- Network removida: False
- Container criado: False
- Container removido: False
- Network: `topsv3-flyway-local-net-20260706160608`
- Container: `topsv3-flyway-local-pg17-20260706160608`

## Passos
- Nenhum passo operacional executado.

## Comandos

### Comando

- Comando: `docker info`
- Exit code: `0`
- Stdout:

```text
Docker daemon local disponivel.
```

## Limites preservados

- Sem dados reais.
- Sem producao, VPS, restore, staging, Pix/Efi real, webhook ou API externa.
- Sem instalacao automatica de Flyway.
- Sem `docker pull`.
- Sem migration nova.
