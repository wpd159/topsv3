# Relatorio - correcao da UI publica

## Resultado

Correcao aplicada sem redesign.

## Alteracoes

- `PublicAnuncioDetalhe` passou a usar `formatarStatusPublico`.
- `PublicAnuncioDetalhe` passou a usar `formatarContatoPublico`.
- Estados tecnicos deixaram de ser exibidos diretamente.
- Pendencia tecnica de WhatsApp deixou de ser exibida diretamente.

## Rotulos publicos aplicados

- `Conteudo disponivel`.
- `Confirmacao de idade necessaria`.
- `Confirmando idade`.
- `Idade nao confirmada`.
- `Conteudo indisponivel`.
- `Contato mediado pelo Tops do Job.`

## Limites preservados

- Sem mudanca de regra de autorizacao.
- Sem decisao de seguranca no frontend.
- Sem WhatsApp publico indevido em anuncio `BLOQUEADO`.
- Sem nova paleta, nova tipografia, animacao, botao flutuante, scroll lock ou `document.body.style.overflow`.
