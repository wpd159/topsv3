# Relatorio - causa do texto tecnico publico

## Resultado

Falha visual do Bloco 32 identificada e corrigida no Bloco 32.1.

## Causa

O componente publico de detalhe do anuncio renderizava diretamente:

- o estado interno do age gate recebido como `status`;
- a pendencia tecnica `pendenciaContatoPublico` retornada pelo contrato publico local.

Isso fazia textos internos aparecerem para visitantes nos prints renderizados, incluindo `conteudo_autorizado` e `PENDENTE_POLITICA_EXPOSICAO_WHATSAPP_PUBLICO`.

## Arquivos envolvidos

- `frontend/src/modules/public/components/PublicAnuncioDetalhe.tsx`;
- `frontend/src/modules/public/skeleton/PublicAgeGateContent.tsx`;
- `backend/src/main/java/br/com/topsdojob/v3/application/publico/mapper/AnuncioPublicoMapper.java`.

## Decisao

A correcao foi feita na apresentacao publica. O backend continua sendo a fonte de decisao de seguranca, contato, classificacao e disponibilidade.
