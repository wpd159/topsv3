# Bloco 28 - Inventario SEO de preservacao

## Objetivo

O Bloco 28 consolida a camada operacional de SEO antes de qualquer homologacao ou cutover da V3.

O foco nao e nova tela, nova regra comercial ou deploy. O foco e preservar trafego atual, mapear URLs publicas, registrar baseline do Search Console, preparar 301/canonical/sitemap/robots e deixar o projeto continuavel apenas com SDD e `docs/v3`.

## Checkpoint anterior

Antes de alterar arquivos do Bloco 28 foi criado commit local de checkpoint do Bloco 27.1:

```text
8575fa5 fix: estabiliza seo publico renderizado ate bloco 27.1
```

Nao houve remote e nao houve push.

## Consulta de producao somente leitura

A producao publica foi consultada somente por endpoints publicos:

- `https://topsdojob.com/robots.txt`;
- `https://topsdojob.com/sitemap.xml`;
- URLs publicas do sitemap, limitadas a metadados de title, description, canonical, robots e H1.

Nada foi alterado em producao. Nao houve login, painel admin, formulario, banco, SQL, dump, midia, segredo, deploy, restart, git pull ou migration.

## Saida bruta externa

A lista bruta completa de URLs do sitemap foi gravada fora do repositorio:

```text
C:\topsv3-auditoria-local\seo\bloco-28
```

Esse diretorio nao deve ser versionado nem incluido no ZIP. Dentro do repositorio ficam apenas contagens, padroes, amostras sanitizadas e relatorios resumidos.

## Contagem sanitizada observada

| Tipo | Total |
| --- | ---: |
| home | 1 |
| cidade | 29 |
| bairro | 21 |
| anuncio | 62 |
| institucional | 1 |
| outros | 0 |
| proibido/admin/api | 0 |
| desconhecido | 45 |

## Padroes preservados

| Tipo | Padrao atual | Direcao V3 |
| --- | --- | --- |
| Home | `/` | preservar |
| Cidade | `/acompanhantes/[uf]/[cidade]` | preservar quando houver conteudo util |
| Bairro | `/acompanhantes/[uf]/[cidade]/[bairro]` | preservar quando houver conteudo suficiente |
| Anuncio | `/anuncios/[slug]` | preservar slug publico quando anuncio for indexavel |
| Institucional | paginas publicas sem login | manter ou redirecionar conforme mapa |
| Admin/API | nao observado no sitemap | manter fora do sitemap e noindex |

## Decisoes

- Lista bruta de anuncios reais nao deve ser versionada.
- Slug publico de anuncio deve ser tratado como sensivel para documentacao versionada.
- Search Console nao sera acessado automaticamente; exportacao completa fica para acao manual futura.
- Cutover SEO fica bloqueado ate mapa completo aprovado.
- Ambiente local continua `noindex` e nao deve emitir canonical de producao.
- SDD e docs SEO sao a fonte de continuidade para outro chat/ferramenta.

## Artefatos criados

- `scripts/local/seo-inventario-producao-publica.ps1`;
- `scripts/local/validar-mapa-preservacao-seo-local.ps1`;
- `docs/v3/SEO-inventario-producao-sanitizado.md`;
- `docs/v3/SEO-mapa-preservacao-urls-v3.md`;
- `docs/v3/SEO-plano-redirecionamentos-301.md`;
- `docs/v3/SEO-canonical-sitemap-robots-cutover.md`;
- `docs/v3/SEO-baseline-search-console-template.md`;
- `docs/v3/SEO-matriz-risco-perda-trafego.md`;
- `docs/v3/SEO-cidades-prioritarias.md`;
- `docs/v3/evidencias/bloco-28/`.

## Proibicoes mantidas

Nao houve producao alterada, banco de producao, dado privado, midia real, migration, SQL, upload, e-mail real, WhatsApp real, pagamento, credito, Pix/Efi, checkout, webhook, importador real, API externa funcional, remote, push ou commit depois das alteracoes do Bloco 28.
