# Bloco 47 - Flyway real via Docker local

## Objetivo

Fazer checkpoint local do Bloco 46, baixar somente a imagem `flyway/flyway` autorizada e validar as migrations V001 a V017 com Flyway real contra PostgreSQL descartavel/local.

## Checkpoint consolidado

- Checkpoint local do Bloco 46: `220c2ba`.
- Mensagem do commit: `chore: registra pendencia instalacao flyway ate bloco 46`.
- Remote: vazio.
- Push: nao executado.

## Pull autorizado

Comando executado:

```powershell
docker pull flyway/flyway
```

Resultado:

- Pull concluido com sucesso.
- Imagem local: `flyway/flyway:latest`.
- Digest informado pelo Docker: `sha256:21a2a696ba834dfaa801aaeb6545f14656626e75cbadfade0e8dbd19626633a4`.
- Nenhuma outra imagem foi baixada neste bloco.

## Validacao Flyway real

Comando:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/validar-flyway-real-local.ps1
```

Resultado:

- `VALIDATION_RESULT=OK_FLYWAY_REAL_LOCAL`.
- Fonte Flyway: imagem Docker `flyway/flyway:latest`.
- Versao observada: Flyway OSS Edition 12.10.0 by Redgate.
- PostgreSQL descartavel: `postgres:17`.
- `flyway info`: OK.
- `flyway migrate`: OK.
- `flyway validate`: OK.
- `flyway info` final: schema em `v017`.
- Migrations aplicadas: 17.

## Recursos Docker

- Prefixo usado: `topsv3-flyway-local`.
- Network descartavel criada e removida: sim.
- Container PostgreSQL descartavel criado e removido: sim.
- Volumes persistentes criados: nao.
- Recursos Docker de outros projetos alterados: nao.

## Limites preservados

- Sem producao, VPS, dados reais, restore ou staging.
- Sem Pix/Efi real, webhook, pagamento real ou API externa indevida.
- Sem migration nova.
- Sem alteracao de SQL de schema.
- Sem push.
- Sem fase posterior iniciada.
