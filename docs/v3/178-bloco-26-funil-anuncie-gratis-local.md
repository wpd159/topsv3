# Bloco 26 - funil Anuncie gratis local

## Objetivo

O Bloco 26 cria o fluxo local `/anunciar` para captar solicitacoes sinteticas de novos anuncios.

O foco estrategico continua sendo aumentar a base de anuncios bons e a liquidez da V3. O plano gratuito permanece util e nao recebeu limite comercial artificial de clique, contato ou WhatsApp.

## Consulta a producao

Nao houve consulta SSH somente leitura. O comportamento necessario foi confirmado por documentos locais, schema V3, dados sinteticos, codigo do workspace e contratos ja existentes.

## Endpoint escolhido

O contrato criado e:

```text
POST /api/public/anunciar
```

Motivo da escolha:

- preserva a rota publica `/anunciar`;
- deixa claro que o fluxo e captacao/cadastro, nao publicacao;
- evita criar rota alternativa publica de anuncio;
- permite manter `/api/public/anuncios/{slug}` apenas para leitura publica.

## Persistencia local

O endpoint usa apenas tabelas existentes:

- `usuario`;
- `anuncio`;
- `anuncio_localizacao`;
- `documento_busca_anuncio`;
- `revisao_anuncio`.

Nenhuma migration foi criada. Nenhum SQL de schema foi alterado.

Quando o payload e valido, o backend cria:

- usuario sintetico local, se ainda nao existir;
- anuncio com `status=PENDENTE_REVISAO`;
- moderacao com `status_moderacao=PENDENTE`;
- classificacao inicial binaria `LIVRE`;
- localizacao sintetica local;
- documento de busca `NAO_PUBLICAVEL`;
- revisao `ABERTA` do tipo `CRIACAO`.

Se o WhatsApp sintetico reservado `+5500000000000` ja estiver vinculado a outro usuario local,
o backend reutiliza esse usuario local em vez de tentar gravar telefone duplicado. Essa regra
evita erro 500 em smokes repetidos e permanece restrita ao ambiente sintetico/local.

O anuncio nao fica publico porque:

- `status` nao e `PUBLICADO`;
- `status_moderacao` nao e `APROVADO`;
- `publicado_em` fica nulo;
- a projecao de busca fica `NAO_PUBLICAVEL`;
- nao ha midia publica.

## Validacoes

O frontend e o backend validam:

- nome de exibicao obrigatorio;
- e-mail apenas em dominio reservado `example.invalid`, quando informado;
- WhatsApp sintetico local `+5500000000000`;
- UF com duas letras;
- cidade obrigatoria;
- titulo com limite de tamanho;
- descricao com limite de tamanho;
- preco maior que zero;
- titulo sem telefone, WhatsApp, URL ou rede social;
- aceite de termos obrigatorio;
- confirmacao local de idade obrigatoria;
- campos perigosos bloqueados.

Campos perigosos como `pagamentoId`, `creditoId`, `storageKey`, `documento`, `foto`, `video`, `cpf`, `payload`, `role` e similares retornam `400`.

## Sem efeitos reais

O fluxo mantem as flags de seguranca:

- `publicacaoAutomaticaExecutada=false`;
- `uploadRealExecutado=false`;
- `pagamentoCriado=false`;
- `creditoCriado=false`;
- `premiumObrigatorio=false`;
- `emailRealEnviado=false`;
- `whatsappRealEnviado=false`.

Nao ha upload real, foto real, video real, documento real, pagamento, credito, checkout, Pix, Efi, e-mail real, WhatsApp real, importador real, API externa, producao, VPS ou banco de producao.

## Frontend

A pagina `frontend/src/app/anunciar/page.tsx` passou a renderizar o componente `PublicAnunciarForm`.

No Bloco 26.2, `PublicAnunciarForm` foi mantido como alias de compatibilidade e passou a renderizar um wizard progressivo em `PublicAnunciarWizard`.

O wizard:

- usa apenas estado React em memoria;
- nao usa `localStorage`;
- nao usa `sessionStorage`;
- nao usa `document.body.style.overflow`;
- nao cria scroll lock;
- nao usa botao flutuante;
- nao cria animacao automatica;
- mantem CTA dentro do fluxo normal da pagina;
- informa que fotos/upload ficam para fase futura;
- valida uma etapa por vez;
- envia o payload somente na revisao final;
- mantem etapa de sucesso separada.

## Admin

Nenhuma nova acao admin foi criada.

O admin existente consegue visualizar a solicitacao local porque ela cria um anuncio pendente e uma revisao aberta usando as tabelas atuais. A tela de moderacao continua local, autenticada e limitada aos contratos ja existentes.

## Overlay N

O circulo `N` observado em prints anteriores foi tratado como artefato externo do navegador/ferramenta de captura se nao aparecer no codigo da V3. O Bloco 26 valida novamente por busca estatica no frontend antes do pacote final.

## Evidencias visuais

Os prints do Bloco 26 devem ficar em:

```text
docs/v3/evidencias/bloco-26/
```

Capturas obrigatorias:

- desktop `/anunciar`: `docs/v3/evidencias/bloco-26/anunciar-desktop-inicial.png`;
- mobile `/anunciar`: `docs/v3/evidencias/bloco-26/anunciar-mobile-inicial.png` (viewport mobile);
- estado inicial: `docs/v3/evidencias/bloco-26/anunciar-desktop-inicial.png`;
- validacao de erro: `docs/v3/evidencias/bloco-26/anunciar-validacao-erro.png`;
- envio local bem-sucedido: `docs/v3/evidencias/bloco-26/anunciar-sucesso.png`;
- estado de upload futuro: `docs/v3/evidencias/bloco-26/anunciar-upload-futuro.png`;
- admin mostrando solicitacao local: `docs/v3/evidencias/bloco-26/admin-solicitacao-bloco-26.png`.

Todos os prints devem usar apenas ambiente local e dados sinteticos.

As capturas mobile finais usam viewport, nao `fullPage`, porque a captura `fullPage` do navegador
integrado apresentou costura visual inconsistente apesar de o DOM medir largura correta e sem
scroll horizontal.

O smoke HTTP direto `scripts/local/validar-api-publica-local.ps1 -BaseUrl http://127.0.0.1:18080`
foi executado em PostgreSQL descartavel fresco apos os prints e retornou
`VALIDATION_RESULT=OK_API_PUBLICA_LOCAL`.

## Riscos residuais

- A decisao final de campos de cadastro depende de revisao Pro e paridade com producao.
- Upload real de midia fica para fase futura.
- Publicacao final depende de moderacao e regras futuras.
- Dados reais continuam proibidos ate autorizacao formal.
