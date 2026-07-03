# Bloco 11 - midia publica/CDN local

## Objetivo

O Bloco 11 consolida a politica local de midia publica para anuncios e stories sem acessar storage real, sem usar midia real e sem expor campos internos de armazenamento.

## Checkpoint local

Antes das alteracoes deste bloco foi criado checkpoint local aprovado:

```text
c669d9d feat: consolida base v3 local ate bloco 10
```

Nao houve push e `git remote -v` permaneceu vazio.

## Consulta a producao

Nao houve consulta SSH somente leitura. As regras necessarias estavam claras nos documentos locais, contratos, codigo do workspace e validacoes dos blocos anteriores.

## Estrategia escolhida

Foi escolhida a opcao mais segura:

```text
urlPublica = null
pendenciaMidia = PENDENTE_URL_PUBLICA_MIDIA_CDN
```

Nao foi criado placeholder visual ou URL sintetica. A CDN real permanece pendencia futura.

## Backend

Foi criada a politica local `MidiaPublicaUrlService`.

Ela nao gera URL publica a partir de:

- `bucket`;
- `chaveObjeto`;
- `storageProvider`;
- `sha256`;
- `etag`;
- URL privada.

DTOs publicos continuam expondo somente metadata segura de midia. Campos privados de storage permanecem fora do contrato publico.

## Stories

Stories continuam exigindo confirmacao de idade pelo backend.

Sem idade confirmada:

- `autorizado=false`;
- `stories=[]`;
- `motivoPublico=IDADE_NAO_CONFIRMADA`;
- nenhuma midia e retornada.

Com idade confirmada:

- o backend pode retornar metadata publica segura;
- `urlPublica` permanece nulo;
- `pendenciaMidia` informa `PENDENTE_URL_PUBLICA_MIDIA_CDN`.

## Conteudo BLOQUEADO

Conteudo `BLOQUEADO` continua liberavel apenas apos confirmacao de idade valida pelo backend. O frontend nao decide classificacao, liberacao, WhatsApp ou exibicao de midia.

## Frontend

O frontend publico foi preparado para exibir o estado de midia local pendente sem redesenhar as telas:

- detalhe de anuncio;
- listagem por cidade;
- listagem por bairro.

Quando `urlPublica` estiver nulo, o frontend exibe a pendencia retornada pelo contrato ou o fallback `PENDENTE_URL_PUBLICA_MIDIA_CDN`.

## Smoke e E2E

O smoke HTTP local passou a validar que respostas publicas nao retornam `urlPublica` real de midia enquanto a CDN estiver pendente e que objetos de midia retornem a pendencia documentada.

O E2E local descartavel usa esse smoke como gate, mantendo PostgreSQL descartavel, dados sinteticos e ausencia de volume persistente.

## Resultados da execucao local

- Diagnostico de toolchain: `OK_DIAGNOSTICO_TOOLCHAIN_LOCAL`.
- Build agregado backend/frontend: `OK_BUILD_LOCAL`.
- Backend direto: `mvn -q -DskipTests compile` e `mvn -q test` com exit 0.
- Frontend direto: `npm run lint` e `npm run build` com exit 0.
- E2E local descartavel: `OK_E2E_LOCAL_DESCARTAVEL`.
- Smoke HTTP dentro do E2E: `SMOKE_HTTP_OK=True`.
- PostgreSQL descartavel: `postgres:16`, 17 migrations aplicadas, container e rede removidos, sem volume persistente.
- Rotas publicas/SEO local: 51/51 verificacoes OK.
- Migrations SQL estaticas: 27/27 verificacoes OK.
- Persistencia JPA estatica: 20/20 verificacoes OK.

## Fora do escopo

Nao houve migration, SQL de schema, dado real, dump, midia real, storage real, admin funcional, autenticacao real, Pix/Efi, financeiro, importador real, acesso a producao, VPS, banco de producao, OpenAI, API externa, remote ou push.

## Riscos residuais

- CDN/storage mapping real ainda precisa de desenho e aprovacao futura.
- Placeholder local nao foi criado nesta etapa por decisao de seguranca.
- Revisao visual final da midia publica dependera de fonte visual aprovada.

## Complemento Bloco 12

A politica de midia publica segura permanece inalterada. A autenticacao admin local nao cria upload, revisao real, publicacao de midia, storage real ou CDN real.

## Complemento Bloco 21

O Bloco 21 criou placeholder visual local neutro para a midia publica pendente.

Esse placeholder:

- nao usa imagem real;
- nao usa video real;
- nao usa URL sintetica de CDN;
- nao monta URL a partir de storage;
- nao expoe `bucket`, `chaveObjeto`, `storageProvider`, `sha256`, `etag` ou URL privada;
- preserva `urlPublica=null` e `PENDENTE_URL_PUBLICA_MIDIA_CDN`;
- tem dimensao estavel para reduzir salto visual no mobile.

A politica de CDN/storage real permanece pendente para fase futura.
