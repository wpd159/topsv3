# Bloco 34 - paridade visual e funcional do wizard `/anunciar`

## Objetivo

O Bloco 34 consolida o checkpoint local dos Blocos 33/33.1 e reorienta o trabalho para preservar, na V3 local, a experiencia de conversao do fluxo publico `/anunciar` da producao atual.

Este bloco nao avanca em admin/moderacao. A validacao administrativa/moderacao relacionada ao anuncio criado pelo wizard fica adiada para o Bloco 35.

## Checkpoint local

- Commit local criado: `b7f5f98`.
- Mensagem: `test: valida wizard anunciar sintetico ate bloco 33.1`.
- Remote: ausente.
- Push: nao executado.

## Consulta a producao

A consulta foi somente leitura, por pagina publica:

- URL: `https://topsdojob.com/anunciar`;
- resultado observado: redirecionamento publico para `https://topsdojob.com/?next=%2Fanunciar`;
- motivo: aviso de conteudo adulto/age gate antes do acesso ao fluxo;
- acao bloqueada por seguranca: nao foi clicado em `Aceitar`, pois isso completaria verificacao etaria em producao.

Nao houve login, preenchimento, envio, upload, pagamento, Pix/Efi, e-mail, WhatsApp, API externa autenticada, VPS, banco ou alteracao em producao.

## Referencia observavel

Sem ultrapassar o age gate, foram observados:

- marca Tops do Job no topo;
- navegacao publica simples;
- CTA `PUBLICAR SEU ANUNCIO`;
- busca publica;
- categorias em destaque;
- secao de confianca/seguranca;
- aviso de conteudo adulto;
- redirecionamento com `next=/anunciar`.

## Ajustes aplicados na V3 local

- O titulo publico da rota passou a priorizar `PUBLICAR SEU ANUNCIO`.
- A introducao do wizard reforca o CTA de publicacao, mantendo envio gratuito e revisao antes de publicar.
- O painel lateral foi aproximado do discurso publico de confianca, seguranca e visibilidade.
- O texto do contato esclarece que WhatsApp publico nao e liberado por este wizard.
- A etapa de midia preserva o conceito de midia revisada da producao, mas sem upload real nesta fase.
- O validador do wizard passou a gerar evidencias no Bloco 34 e a usar textos sinteticos acentuados.

## Fora do escopo

- admin/moderacao;
- stores;
- upload real;
- pagamento real;
- Pix/Efi real;
- e-mail real;
- WhatsApp real;
- publicacao automatica;
- dados reais;
- banco de producao;
- restore;
- sanitizacao real;
- deploy;
- remote ou push.

## Resultado

A V3 local ficou em paridade suficiente com a parte observavel da producao para o Bloco 34: CTA, confianca, revisao antes de publicacao, fluxo guiado e ausencia de efeitos reais. A paridade profunda do wizard de producao permanece limitada pelo age gate e deve ser retomada apenas com confirmacao explicita para a observacao segura desse passo ou por outra evidencia autorizada.
