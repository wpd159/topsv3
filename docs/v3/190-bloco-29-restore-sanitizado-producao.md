# Bloco 29 - restore sanitizado de copia de producao

## Objetivo

Preparar o uso de copia/backup da producao para validacao realista da V3, sempre em ambiente local isolado, fora do repositorio e sem versionar dado sensivel.

## Checkpoint anterior

Antes das alteracoes do Bloco 29 foi criado commit local:

```text
a9a50a3 docs: consolida inventario seo preservacao ate bloco 28
```

Nao houve remote e nao houve push.

## Backup autorizado

Backup existente localizado e copiado para pasta externa autorizada:

```text
C:\topsv3-auditoria-local\backups\topsdojob-db-20260529-163722.dump
```

SHA-256:

```text
ca1ab4d33e8ac9f484f9c5cf61589014189407c960d1a296cf4b984817f9cee9
```

O backup bruto nao entrou no repositorio, nao entrou no ZIP e nao teve conteudo impresso.

## Restore local

Restore local isolado nao foi executado nesta rodada.

Motivo:

```text
PENDENTE_CLIENTE_POSTGRES_COMPATIVEL
```

O dump esta em formato PostgreSQL custom com versao de arquivo 1.16. A imagem local disponivel e `postgres:16`, cujo `pg_restore` nao aceita esse formato. Nao foi feito pull de imagem nova, instalacao de cliente ou workaround que abrisse conteudo sensivel do backup.

## Sanitizacao

Sanitizacao real do banco local ficou pendente porque o restore local isolado nao foi concluido.

Campos/tipos planejados para sanitizacao:

- CPF, RG, documento, selfie e documento com foto;
- nome civil;
- e-mail, telefone e WhatsApp reais;
- endereco especifico sensivel;
- IP e user-agent brutos;
- token, senha e certificado;
- storage key, bucket e URL privada de midia;
- payload financeiro e identificadores Pix/Efi sensiveis;
- logs sensiveis.

## SEO com dados sanitizados

Validacao SEO com dados sanitizados ficou pendente do restore/sanitizacao.

Continuam pendentes:

- URLs de anuncio preservaveis;
- URLs noindex/removidas;
- URLs que exigem decisao;
- cidades com conteudo suficiente;
- bairros com conteudo suficiente;
- paginas fracas/vazias;
- classificacao das 45 URLs desconhecidas do Bloco 28.

## Confirmacoes

- Producao alterada: nao.
- Banco de producao usado para teste: nao.
- SQL em banco de producao: nao.
- Dump novo de producao gerado: nao.
- Backup/dump no Git: nao.
- Backup/dump no ZIP: nao.
- Midia real copiada: nao.
- Documentos reais copiados: nao.
- Dados sensiveis versionados: nao.
- Migration/SQL de schema V3: nao.
- Importador real definitivo: nao.
- Remote/push: nao.

## Continuidade no Bloco 29.1

O Bloco 29.1 adiciona verificacao obrigatoria do SHA-256 antes de qualquer restore, diagnostico de cliente PostgreSQL compativel e caminho operacional para restore/sanitizacao quando imagem 17.x existir localmente.

Na execucao atual, o SHA conferiu, mas o restore segue bloqueado porque apenas `postgres:16` esta disponivel localmente. Nenhum `docker pull`, instalacao, VPS/producao, SQL bruto ou exposicao de conteudo do backup foi executado.
