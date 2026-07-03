# Politica de cadastro de anuncio gratis

## Principio

O plano gratuito da V3 deve continuar util.

Premium e aditivo. Premium pode ampliar exposicao, organizar midia, oferecer destaque ou relatorio, mas nao pode virar barreira para anunciar, receber cliques, receber contatos ou liberar WhatsApp por regra comercial artificial.

## Funil local

O Bloco 26 implementa apenas um funil local e sintetico:

- pagina publica `/anunciar`;
- wizard progressivo com envio apenas na revisao final;
- endpoint `POST /api/public/anunciar`;
- solicitacao local de anuncio;
- revisao pendente;
- ausencia de publicacao automatica;
- ausencia de upload real;
- ausencia de pagamento.

## Dados permitidos no E2E

Somente dados ficticios:

- nome: `Anunciante Sintetica Local`;
- e-mail: `anunciante.local@example.invalid`;
- WhatsApp: `+5500000000000`;
- UF: `ZZ`;
- cidade: `Cidade Sintetica`;
- bairro: `Bairro Sintetico`;
- titulo neutro;
- descricao neutra;
- preco sintetico maior que zero.

Nao usar CPF, documento, foto, video, endereco real, WhatsApp real, e-mail real ou dado de producao.

Como o WhatsApp acima e reservado e repetivel nos smokes, o backend pode reutilizar usuario local
ja existente por esse telefone sintetico. Isso evita colisao de indice unico sem aceitar telefone
real e sem criar regra de contato publico.

## Moderacao

Solicitacao gratuita nao equivale a publicacao.

Antes de aparecer como anuncio publico normal, o anuncio precisa passar por etapas futuras de moderacao, midia e politica de publicacao. Neste bloco, a revisao nasce `ABERTA` e o anuncio fica `PENDENTE_REVISAO`.

## Campos proibidos

O contrato publico nao aceita campos de controle interno ou financeiro:

- status;
- statusModeracao;
- classificacaoConteudo;
- pagamentoId;
- creditoId;
- pix;
- efi;
- storageKey;
- arquivo;
- foto;
- video;
- documento;
- cpf;
- payload;
- role;
- papel.

Campos desconhecidos ou perigosos devem retornar `400`.

## Upload futuro

Foto, video e documento ficam fora deste bloco.

Se a regra futura exigir foto para publicar, o anuncio deve permanecer nao publico ate upload e moderacao aprovados. O funil gratuito pode captar a solicitacao sem exigir foto neste momento.

## WhatsApp e contatos

O Bloco 26 nao cria limite diario comercial de WhatsApp, clique ou contato para o gratuito.

O backend continua sendo a fonte da politica de contato publico. O frontend nao decide classificacao, contato publico, publicacao, Premium ou liberacao de WhatsApp.

## Pagamento e Premium

O cadastro gratis nao pode:

- criar checkout;
- gerar Pix;
- chamar Efi;
- criar pagamento;
- criar credito;
- ativar beneficio Premium;
- exigir Premium para continuar;
- prometer resultado.

Qualquer fluxo financeiro fica em fase futura aprovada.

## Bloco 26.2

O Bloco 26.2 acrescenta apenas UX progressiva e preview Premium administrativo local.

Isso nao altera a politica do gratuito: anunciar continua sem compra obrigatoria, sem limite comercial artificial de WhatsApp/clique/contato e sem publicacao automatica.
