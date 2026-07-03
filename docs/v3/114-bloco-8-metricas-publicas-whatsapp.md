# Bloco 8 - Metricas publicas e WhatsApp local

## Escopo

O Bloco 8 adiciona endpoints publicos locais para registrar visualizacao e clique WhatsApp em banco PostgreSQL descartavel.

Endpoints:

- `POST /api/public/anuncios/{slug}/visualizacao`;
- `POST /api/public/anuncios/{slug}/clique-whatsapp`.

Os endpoints sao publicos, nao exigem autenticacao e nao executam acao administrativa critica. Eles nao alteram anuncio, pagamento, credito, moderacao ou importacao.

## Classificacao binaria

A classificacao publica da V3 e apenas:

- `LIVRE`;
- `BLOQUEADO`.

Conteudo `LIVRE` pode aparecer publicamente quando tambem estiver `PUBLICADO`, `APROVADO` e sem `removido_em`.

Conteudo `BLOQUEADO` nao libera sem confirmacao de idade. A partir do Bloco 9, pode ser liberado pelo backend apos confirmacao valida, conforme politica propria.

O backend e a fonte da decisao. O frontend nao decide classificacao.

## Visualizacao

O endpoint de visualizacao registra evento em `evento_visualizacao` quando o anuncio e publicavel.

Dados tecnicos sao minimizados:

- IP bruto nao e armazenado;
- User-Agent bruto nao e armazenado;
- referer bruto nao e armazenado;
- visitante local opcional e armazenado somente como hash.

O hash usa SHA-256 com salt de ambiente local configuravel por `APP_EVENT_HASH_SALT`. Em ambiente local ha fallback ficticio `valor_local_ficticio`. A partir do Bloco 9, fora de `local` a aplicacao falha se o salt estiver ausente, vazio ou ficticio.

## Clique WhatsApp

O endpoint de clique WhatsApp registra evento em `clique_whatsapp` e avalia a politica backend de contato publico.

WhatsApp so retorna pelo campo `whatsappUrl` deste endpoint, e apenas quando permitido.

Nao existe limite diario comercial de clique, contato ou WhatsApp.

O numero sintetico permitido em smoke local e:

```text
+5500000000000
```

No retorno autorizado, a URL local esperada e:

```text
https://wa.me/5500000000000
```

## Stories

Stories permanecem bloqueados por padrao.

Regras:

- story sem confirmacao de idade nao e publico;
- story `LIVRE`, aprovado e vinculado a anuncio publicado depende de confirmacao de idade pelo backend;
- story `BLOQUEADO` nao e publico sem idade confirmada e pode ser liberado pelo backend apos confirmacao valida;
- nao ha estado intermediario, blur por categoria ou desbloqueio parcial por visitante.

Motivo registrado quando nao houver confirmacao a partir dos ajustes dos Blocos 9 e 10:

```text
IDADE_NAO_CONFIRMADA
```

A pendencia de URL publica de midia/CDN permanece:

```text
PENDENTE_URL_PUBLICA_MIDIA_CDN
```

## Frontend

A rota `/anuncios/[slug]` registra visualizacao local de forma discreta e possui acao `Ver WhatsApp`.

O frontend:

- nao decide classificacao;
- nao usa localStorage;
- nao usa sessionStorage;
- nao chama producao por padrao;
- nao carrega midia real;
- so reflete `whatsappUrl` quando o backend autoriza.

## Fora do escopo

Nao houve producao, VPS, banco de producao, dump, dado real, API externa, Efi real, OpenAI, admin funcional, autenticacao real, Pix/Efi funcional, financeiro funcional, moderacao real, importador real, migration nova ou SQL de schema novo.

## Complemento Bloco 25 - prova de resultado

O Bloco 25 usa as mesmas bases de metricas para leitura administrativa agregada e sanitizada.

Regras adicionais:

- respostas admin retornam visualizacoes, cliques WhatsApp permitidos, taxa clique/view, origem agregada e serie diaria;
- eventos brutos continuam fora do DTO;
- IP, User-Agent, referer bruto e hashes internos nao podem ser retornados;
- origem deve ser agregada e sanitizada;
- comparativo organico/Premium e informativo e nao garante resultado;
- gratuito permanece sem limite comercial artificial de clique, contato ou WhatsApp;
- nao ha pixel, tracking externo, exportacao, API externa ou dado real.
