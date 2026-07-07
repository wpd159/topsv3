# Relatorio HML online - Bloco 61

## Status

HML online em `https://v3.esle.cloud`.

## Evidencias online

- `HEAD https://v3.esle.cloud/`: HTTP 200.
- Header `X-Robots-Tag`: `noindex, nofollow, noarchive`.
- `GET https://v3.esle.cloud/robots.txt`: HTTP 200.
- Conteudo de `robots.txt`:

```text
User-agent: *
Disallow: /
```

- `GET https://v3.esle.cloud/api/health`: HTTP 200.
- Resposta sanitizada de health:

```json
{"status":"UP","app":"topsdojob-v3-backend","requestId":"<request-id-hml>"}
```

## Redirect HTTP

- `HEAD http://v3.esle.cloud/`: HTTP 301.
- `Location`: `https://v3.esle.cloud/`.
- Header `X-Robots-Tag`: `noindex, nofollow, noarchive`.

## Limites

As validacoes online foram somente leitura contra HML. Nao houve acao administrativa, formulario, upload, pagamento, webhook, Pix/Efi real, dado real, producao alterada ou push.
