# Sensibilidade e obrigatoriedade dos campos de importacao

Status da entrega: `REGRAS DOCUMENTAIS ESTRUTURAIS`.

## Sensibilidade

Classificacoes criadas:

- `PUBLICO`: campo que podera aparecer em rota publica apos revisao.
- `OPERACIONAL`: campo tecnico/operacional sem exposicao publica direta.
- `PESSOAL`: dado pessoal como e-mail, telefone ou WhatsApp.
- `SENSIVEL`: dado que exige cuidado adicional de classificacao.
- `FINANCEIRO`: dado de pagamento, credito, valor, status financeiro ou conciliacao.
- `PRIVADO_DOCUMENTAL`: documento privado, nunca publicavel como midia.
- `TECNICO_HASH`: checksum, tamanho, algoritmo ou evidencia tecnica.

Campos `EMAIL`, `TELEFONE`, `DOCUMENTO`, `DINHEIRO` e `CREDITO` exigem classificacao de sensibilidade no validador.

## Obrigatoriedade

Classificacoes criadas:

- `OBRIGATORIO`: campo estrutural necessario.
- `OBRIGATORIO_SE_PRESENTE_ORIGEM`: campo exigido quando a origem futura possuir a informacao.
- `OPCIONAL`: campo nao bloqueante.
- `DERIVADO`: campo calculado em fase futura.
- `PENDENTE_EVIDENCIA`: campo depende de fonte real autorizada.

Campo obrigatorio sem nome logico gera pendencia estrutural.

## Saneamento

Campos marcados como saneaveis podem exigir normalizacao futura antes de qualquer importacao real. Exemplos:

- telefone/WhatsApp;
- e-mail;
- textos publicos;
- slugs;
- status legado;
- caminhos logicos sanitizados;
- chaves SEO.

Saneamento futuro nao autoriza leitura de dump nesta fase.

## Evidencia

Campos que exigem evidencia precisam ser confirmados por fonte real autorizada antes de promocao. Exemplos:

- provedor de pagamento;
- status financeiro;
- `txid`;
- relacao entre pagamento e credito;
- Premium ativo;
- metricas existentes;
- decisao de URL publica;
- checksum declarado;
- classificacao de documento privado.

## Regras proibitivas

- Campo financeiro nao pode usar tipo incompatível.
- Campo de credito nao pode usar decimal/float.
- Documento privado nao pode ser `PUBLICO`.
- Telefone/WhatsApp e dado pessoal.
- Documento privado nao entra em galeria publica.
- Nome de tabela legada nao define provedor de pagamento.
- Gratuito nao tera limite artificial de cliques, contatos ou WhatsApp.

## Validacao em memoria

`ValidadorDicionarioImportacao` valida apenas o catalogo Java:

- arquivo sem campos;
- campo obrigatorio sem nome logico;
- campo sem tipo;
- campo sem obrigatoriedade;
- campo sensivel sem classificacao;
- campo financeiro com tipo incompativel;
- campo de credito com tipo incompativel;
- documento como publico;
- tipo de arquivo sem dicionario.

O validador nao abre arquivos, nao le filesystem, nao conecta banco, nao chama rede e nao inspeciona dado real.

## Complemento da Fase 2D

As regras de saneamento da Fase 2D devem respeitar estas classificacoes: telefone/WhatsApp segue como dado pessoal, documento privado nao pode ser publico, financeiro e credito exigem tipos compativeis, pagamento depende de evidencia e textos publicos exigem saneamento antes de qualquer publicacao futura.

A Fase 2D permanece estrutural e nao executa transformacao real.
