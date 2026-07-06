# Bloco 46 - Instalacao e validacao Flyway real local

## Objetivo

Fazer checkpoint local do Bloco 45 e tentar instalar/validar Flyway real local via `winget`, somente se o pacote exato `Redgate.Flyway` existir.

## Checkpoint consolidado

- Checkpoint local do Bloco 45: `282802d`.
- Mensagem do commit: `chore: registra pendencia flyway real ate bloco 45`.
- Remote: vazio.
- Push: nao executado.

## Diagnostico controlado

Comandos executados:

```powershell
where.exe winget
where.exe flyway
winget search --id Redgate.Flyway -e --accept-source-agreements
```

Resultado:

- `winget`: localizado em `C:\Users\WpD\AppData\Local\Microsoft\WindowsApps\winget.exe`.
- `flyway`: nao localizado no PATH antes da tentativa.
- Pacote exato `Redgate.Flyway`: nao encontrado pelo `winget search`.

## Decisao da execucao

Como o pacote exato `Redgate.Flyway` nao foi encontrado:

- `winget install` nao foi executado.
- Chocolatey nao foi tentado.
- Scoop nao foi tentado.
- Download manual nao foi tentado.
- `docker pull` nao foi executado.
- Validacao por `psql` nao foi usada como substituto de Flyway real.

Status registrado: `PENDENTE_FLYWAY_INSTALACAO_LOCAL`.

## Validacao Flyway real local

O validador existente foi executado novamente:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/validar-flyway-real-local.ps1
```

Resultado:

- `VALIDATION_RESULT=PENDENTE_FLYWAY_REAL_LOCAL`.
- `$LASTEXITCODE=2`.
- Flyway CLI: indisponivel.
- Imagem Flyway local: indisponivel.
- Imagem PostgreSQL local: `postgres:17`.
- Recursos Docker criados: nenhum.
- Recursos Docker removidos: nenhum.

## Limites preservados

- Sem producao, VPS, dados reais, restore ou staging.
- Sem Pix/Efi real, webhook, pagamento real ou API externa.
- Sem migration nova.
- Sem alteracao de SQL de schema.
- Sem push.
- Sem fase posterior iniciada.
