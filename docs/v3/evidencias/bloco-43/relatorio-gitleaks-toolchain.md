# Relatorio gitleaks/toolchain

## Diagnostico executado

### `where.exe gitleaks`

Resultado: nao localizado no PATH.

```text
INFORMACOES: nao foi possivel localizar arquivos para o(s) padrao(oes) especificado(s).
```

### `gitleaks version`

Resultado: comando nao reconhecido.

```text
gitleaks: termo nao reconhecido como cmdlet, funcao, arquivo de script ou programa operavel.
```

## Status

`PENDENTE_GITLEAKS_REAL_NO_PATH`

## Decisao operacional

- Nao instalar automaticamente.
- Manter fallback local apenas como protecao secundaria.
- Exigir gitleaks real ou decisao formal antes de homologacao/producao.

## Comando manual recomendado

```powershell
winget install --id Gitleaks.Gitleaks -e
gitleaks version
gitleaks detect --source . --no-git --redact --verbose
```

O comando acima nao foi executado neste bloco.
