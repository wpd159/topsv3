# Relatorio gitleaks real

## Diagnostico inicial

- `where.exe winget`: `C:\Users\WpD\AppData\Local\Microsoft\WindowsApps\winget.exe`.
- `where.exe gitleaks`: nao localizado antes da instalacao.

## Instalacao

Comando executado:

```powershell
winget install --id Gitleaks.Gitleaks -e
```

Resultado:

- Pacote encontrado: `Gitleaks.Gitleaks`.
- Versao instalada: `8.30.1`.
- Hash do instalador verificado pelo `winget`.
- Instalacao concluida com sucesso.
- Alias de linha de comando adicionado.

## Validacao de caminho e versao

Apos recarregar PATH de Machine/User:

- `where.exe gitleaks`: `C:\Users\WpD\AppData\Local\Microsoft\WinGet\Packages\Gitleaks.Gitleaks_Microsoft.Winget.Source_8wekyb3d8bbwe\gitleaks.exe`.
- `gitleaks version`: `8.30.1`.

## Scan real

Comando:

```powershell
gitleaks detect --source . --no-git --redact --verbose
```

Primeira execucao:

- Exit code: `1`.
- Achados: 4.
- Local: `frontend/.next`.
- Classificacao: artefatos de build/cache local ignorados por `.gitignore`.

Acao:

- `frontend/.next` removido apos confirmar que e ignorado por `.gitignore` e nao versionado.

Execucao final:

- Exit code: `0`.
- Bytes escaneados: aproximadamente 4.92 MB.
- Resultado: `no leaks found`.
