# Contratos de smoke tests da API publica local

## Script

Arquivo:

```text
scripts/local/validar-api-publica-local.ps1
```

O script executa somente contra `http://localhost` ou `http://127.0.0.1`.

## Endpoints testados

Com backend local iniciado pelo e2e, foram testados:

- `GET /api/health`;
- `GET /api/health/readiness`;
- `GET /api/public/seo/rota` com `caminho=/sitemap.xml`;
- `GET /api/public/seo/rota` com `caminho=/robots.txt`;
- `GET /api/public/anuncios/anuncio-sintetico-local`;
- `GET /api/public/acompanhantes/zz/cidade-sintetica`;
- `GET /api/public/acompanhantes/zz/cidade-sintetica/bairro-sintetico`;
- `GET /api/public/seo/rota` com `caminho=/perfil/slug`, esperando 400;
- `GET /api/public/seo/rota` com URL absoluta de producao, esperando 400.

## Validacoes de resposta

O smoke test valida que respostas publicas nao exponham:

- CPF;
- e-mail;
- telefone real;
- contato WhatsApp publico;
- documento privado;
- storage key;
- bucket;
- hash interno;
- pagamento;
- saldo de credito;
- auditoria administrativa.

Tambem valida que:

- `urlPublica` de midia permanece nulo enquanto a CDN real estiver pendente;
- objetos publicos de midia retornam `PENDENTE_URL_PUBLICA_MIDIA_CDN`;
- stories sem idade retornam `IDADE_NAO_CONFIRMADA`;
- stories com idade nao expoem bucket, chave, provider, hash ou URL privada;
- conteudo `BLOQUEADO` com idade continua liberavel pelo backend;
- WhatsApp continua retornando somente no endpoint autorizado.

O marcador tecnico `PENDENTE_POLITICA_EXPOSICAO_WHATSAPP_PUBLICO` e permitido porque informa pendencia de politica e nao contem contato publico.

## Codigos de saida

- `0`: smoke HTTP local passou;
- `1`: falha real de status ou vazamento sensivel;
- `2`: pendencia operacional, por exemplo backend local indisponivel.

## Limites

O smoke test nao consulta producao, nao usa token, nao usa cookie de sessao, nao persiste dados no navegador e nao chama API externa.

## Complemento Bloco 8

O smoke HTTP tambem testa:

- `POST /api/public/anuncios/anuncio-sintetico-local/visualizacao`;
- `POST /api/public/anuncios/anuncio-sintetico-local/clique-whatsapp`;
- ausencia de telefone em endpoints que nao sejam clique WhatsApp;
- retorno de `https://wa.me/5500000000000` somente no endpoint autorizado;
- `IDADE_NAO_CONFIRMADA` para stories sem idade;
- `PENDENTE_URL_PUBLICA_MIDIA_CDN` para midia/CDN pendente;
- ausencia de story publico por padrao;
- ausencia de midia `BLOQUEADO` no detalhe publico.
