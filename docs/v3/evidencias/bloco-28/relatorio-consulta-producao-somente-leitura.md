# Relatorio - consulta de producao somente leitura

## Necessidade

A consulta foi obrigatoria no Bloco 28 para proteger trafego atual antes de homologacao/cutover.

## Consultas executadas

Executadas pelo script local:

```text
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/seo-inventario-producao-publica.ps1 -BaseUrl "https://topsdojob.com" -OutputDir "C:\topsv3-auditoria-local\seo\bloco-28" -SanitizedReport "docs/v3/evidencias/bloco-28/relatorio-inventario-producao-sanitizado.md"
```

O script acessa somente:

- `https://topsdojob.com/robots.txt`;
- `https://topsdojob.com/sitemap.xml`;
- sitemaps filhos publicos, se existirem;
- metadados publicos limitados de amostras do sitemap.

## Evidencia obtida

- `robots.txt`: HTTP 200.
- `sitemap.xml`: HTTP 200.
- 1 home.
- 29 URLs de cidade.
- 21 URLs de bairro.
- 62 URLs de anuncio.
- 1 URL institucional.
- 0 URLs admin/API no sitemap.

## Limites respeitados

- Sem login.
- Sem painel admin.
- Sem formulario.
- Sem banco.
- Sem SQL.
- Sem dump.
- Sem midia real.
- Sem segredo.
- Sem certificados.
- Sem tokens.
- Sem senhas.
- Sem deploy.
- Sem restart.
- Sem git pull.
- Sem migration.

## Confirmacao

Nada foi alterado em producao.
