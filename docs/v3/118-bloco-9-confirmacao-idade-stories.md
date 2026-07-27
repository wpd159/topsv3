# Bloco 9 - Confirmacao de idade e stories

> Registro historico. O fluxo simplificado descrito neste bloco foi substituido
> pelo age gate completo por escopo da V034. Os endpoints `/api/public/idade/*`
> e o cookie `topsv3_idade_confirmada` nao fazem parte do contrato ativo.
> O fluxo vigente usa aceite global independente e verificacao reforcada com
> nascimento, CPF, aceites, risco e fallback documental privado.

## Escopo

O Bloco 9 adiciona confirmacao de idade publica local por declaracao, sem CPF, sem documento e sem conta de usuario.

Endpoints criados:

- `POST /api/public/idade/confirmar`;
- `GET /api/public/idade/status`;
- `GET /api/public/anuncios/{slug}/stories`.

## Cookie local

A confirmacao bem-sucedida emite cookie HttpOnly:

```text
topsv3_idade_confirmada
```

Propriedades:

- assinado com HMAC-SHA-256;
- `SameSite=Lax`;
- `Secure` fora de ambiente local;
- expiracao curta de 6 horas;
- nao gravado em banco;
- nao armazenado em localStorage ou sessionStorage.

## Segredos

Em local, `application-local.yml` usa valores ficticios:

- `APP_EVENT_HASH_SALT`;
- `APP_AGE_GATE_SIGNING_VALUE`.

Fora de `local`, a aplicacao falha se o salt/segredo estiver ausente, vazio ou ficticio.

Nao ha segredo real versionado.

## Classificacao

`LIVRE` permanece acessivel sem confirmacao de idade quando publicado/aprovado/nao removido.

`BLOQUEADO` nao libera sem idade confirmada. Apos confirmacao valida, o backend pode liberar detalhe, stories e WhatsApp quando as demais regras publicas forem atendidas.

## Ressalva juridica

A fase segue a diretriz interna referida como Lei Felca/ECA Digital, mas nao promete conformidade juridica final sem revisao especifica futura.

## Midia

Stories autorizados retornam somente metadata segura. Enquanto CDN/midia publica real nao existir, `urlPublica` fica nulo e a pendencia continua:

```text
PENDENTE_URL_PUBLICA_MIDIA_CDN
```

O Bloco 11 consolidou essa regra em `MidiaPublicaUrlService`: a URL publica nao pode ser derivada de bucket, chaveObjeto, storageProvider, sha256, etag ou URL privada.

## Ajuste Bloco 10

A pendencia historica `PENDENTE_CONFIRMACAO_IDADE_STORIES` foi substituida no fluxo ativo. Sem idade confirmada, o endpoint usa `IDADE_NAO_CONFIRMADA`; com idade confirmada e midia/CDN pendente, usa `PENDENTE_URL_PUBLICA_MIDIA_CDN`.
