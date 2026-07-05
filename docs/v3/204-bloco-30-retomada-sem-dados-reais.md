# Bloco 30 - retomada sem dados reais

## Decisao

O Bloco 30 formaliza que a frente de dados reais do Bloco 29 fica materialmente aberta e adiada para pre-staging/cutover. O projeto nao precisa testar com dados reais agora.

O desenvolvimento da V3 segue com dados sinteticos locais, versionaveis e seguros. Nao houve restore novo, POST_DATA, sanitizacao nova, correcao de orfaos, producao, VPS, Docker, banco de producao, SQL em producao, dump novo, SQL bruto ou log bruto versionado.

## Estado dos Blocos 29 a 29.6

- Bloco 29: gate de restore completo permanece aberto e adiado.
- Bloco 29.5: aprovado somente como diagnostico de quarentena sanitizada, sem POST_DATA e sem staging final.
- Bloco 29.6: aprovado como consolidacao documental e hardening.
- Checkpoint local: `36b94c6 docs: consolida diagnostico quarentena ate bloco 29.6`.

## Gates

- Opcao A: obrigatoria antes de homologacao/cutover real, exigindo novo backup consistente ou correcao da origem.
- Opcao B: permitida apenas como insumo auxiliar agregado de SEO/diagnostico.
- Opcao C: bloqueada ate revisao Pro/humana em bloco futuro.

## Base sintetica

A base sintetica local foi consolidada em `backend/src/test/resources/fixtures/v3-dados-sinteticos.json`, com scripts de geracao/relatorio e validacao em `scripts/local`.

Contagens atuais:

- 5 cidades;
- 9 bairros;
- 12 anuncios;
- 9 anuncios `LIVRE`;
- 3 anuncios `BLOQUEADO`;
- 4 anuncios com Premium ativo;
- 2 anuncios com Premium expirado;
- 6 anuncios gratuitos;
- 6 metricas agregadas;
- 9 rotas cobertas.

## Regra permanente

Producao continua proibida como bancada de teste. Banco de producao, Efí real, Pix real, pagamento real, upload real, e-mail real, WhatsApp real e API externa real seguem fora do Bloco 30.
