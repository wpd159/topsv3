# Politica de metricas e prova de resultado

## Principios

Metricas da V3 devem ajudar a explicar desempenho sem criar promessa comercial indevida.

Regras permanentes:

- prova de resultado e evidencia informativa, nao garantia de contratacao;
- Premium pode ampliar exposicao, mas nao garante resultado;
- gratuito nao deve ser limitado artificialmente em clique, contato ou WhatsApp;
- backend calcula e decide;
- frontend exibe apenas DTO sanitizado;
- dados brutos sensiveis nunca entram em resposta administrativa ou publica.

## Metricas permitidas neste bloco

- visualizacoes agregadas;
- cliques WhatsApp permitidos;
- taxa clique/view;
- origem agregada;
- cidade, bairro e UF sanitizados;
- serie diaria;
- comparativo organico/Premium;
- totais por anunciante para uso administrativo futuro.

## Metricas proibidas neste bloco

- IP bruto;
- User-Agent bruto;
- referer bruto;
- hash interno de visitante;
- identificador de dispositivo;
- e-mail, telefone, WhatsApp bruto ou documento;
- bucket, storage key, sha256 ou etag;
- payload financeiro, Pix/Efi, webhook, saldo, credito, txid, valor monetario;
- pixel externo;
- tracking externo;
- exportacao;
- relatorio enviado por e-mail real.

## Comparativo organico/Premium

O comparativo deve separar dias ou totais com Premium ativo e sem Premium ativo.

Ele deve comunicar tendencia e exposicao, nunca resultado garantido.

Texto seguro:

```text
Beneficios Premium podem ampliar exposicao visual do anuncio. Compare periodos com e sem beneficio ativo para entender tendencia. Resultados variam conforme praca, anuncio, fotos, texto e demanda.
```

Textos proibidos:

- resultado garantido;
- contratacao garantida;
- gratis recebe menos contato;
- pague para liberar WhatsApp;
- Premium garante top absoluto;
- clique ou lead garantido.

## Retencao e privacidade

Eventos brutos podem existir localmente como base tecnica, mas respostas devem sair apenas agregadas.

Hash nao e anonimizacao completa. Por isso, hash de visitante, IP ou User-Agent tambem nao deve ser retornado ao admin ou anunciante.

## Anunciante futuro

O Bloco 25 ainda nao cria painel real de anunciante.

A visao por anunciante e administrativa e retorna:

- `endpointAnuncianteRealDisponivel=false`;
- `somenteLeitura=true`;
- `dadosSensiveisOcultos=true`.

Antes de expor relatorios para anunciante real, a V3 precisa de revisao Pro de privacidade, periodo, granularidade, download/exportacao, texto comercial e equivalencia com metricas atuais.

## Producao atual

Quando houver duvida sobre comportamento ja existente de producao, preservar producao como base. A consulta a producao so e permitida se estritamente necessaria e somente leitura. O Bloco 25 foi implementado com base nos documentos e codigo local, sem consulta SSH.
