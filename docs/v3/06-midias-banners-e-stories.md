# Mídias, banners e stories

## Objetivo

Criar uma fonte única de verdade para mídia pública e privada, eliminando conflitos entre listas de URLs, tabelas antigas, revisões, placeholders, Supabase/R2 e stories soltos.

## Fonte única de verdade

A V3 deve usar `arquivo_midia` como fonte canônica do arquivo e tabelas de vínculo para uso por domínio.

Fontes principais:

- `arquivo_midia`: arquivo físico/lógico no storage.
- `anuncio_midia`: uso público ou moderavel da mídia no anúncio.
- `story_anuncio`: story vinculado ao anúncio.
- `documento_usuario`: documento privado vinculado ao usuário.
- `documento_usuario_acesso`: auditoria de acesso a documento privado.
- `banner`: conteúdo administrativo de banner.

URL pública não é fonte de verdade. Ela deve ser derivada de provider, bucket, chave do objeto, política de acesso e CDN.

## Compatibilidade com storage atual

A V3 deve manter compatibilidade com o storage atual durante migração e operação inicial, mas documentar a fonte canônica.

Regras:

- R2 pode ser fonte final de mídia pública e documentos privados, se validado.
- Supabase legado deve ser adaptador de importação/leitura temporária, não fonte concorrente permanente.
- Todo objeto migrado deve ter manifesto.
- Quando houver cópia, validar tamanho e checksum/ETag quando disponível.
- Configurações de bucket e base URL devem vir de configuração central, não hardcode espalhado.

## Documentos privados de usuário

Documento privado não é mídia publicável e não deve ser exposto em API pública.

Regras:

- Documento pode ser mantido enquanto houver anúncio vinculado ou finalidade operacional legítima.
- `retencao_ate` é nullable e não deve ser obrigatório.
- A política de retenção deve distinguir `ENQUANTO_HOUVER_ANUNCIO`, `DATA_DEFINIDA`, `RETENCAO_JURIDICA` e `MANUAL`.
- Deve existir status de validação.
- Quando validado, deve registrar `validado_por` e `validado_em`.
- Quando removido ou expurgado, deve registrar `removido_em` ou `expurgado_em`.
- Acesso a documento privado deve ser auditável.
- Não há expurgo automático nesta fase.
- A decisão jurídica final de retenção permanece para fase futura.

## Placeholder

Placeholder não é mídia real.

Regras:

- Placeholder não entra em `arquivo_midia`.
- Placeholder não satisfaz critério de anúncio com foto.
- Placeholder não deve habilitar página indexável se a regra exigir mídia real.
- Placeholder pode existir como fallback visual do frontend, sem efeito de negócio.

## Mídia de anúncio

Tipos:

- `FOTO`
- `VIDEO`
- `STORY`

Finalidades:

- `CAPA`
- `GALERIA`
- `STORY`

Regras:

- Um anúncio aprovado precisa de mídias publicáveis coerentes com a regra comercial.
- Capa deve ser uma mídia real e publicável.
- Ordem de galeria deve ser armazenada.
- Moderação deve decidir sobre mídias novas/removidas.
- Mídia removida deve preservar histórico quando houver auditoria.

## Documentos privados

Documentos de usuário devem ficar separados da galeria pública.

Regras:

- Acesso por URL assinada ou streaming autenticado.
- Auditoria de acesso administrativo.
- Retenção definida.
- Nunca incluir documento privado em sitemap, payload público, JSON-LD ou cache público.

## Stories

Stories devem estar vinculados ao anúncio.

Regras:

- `story_anuncio.anuncio_midia_id` obrigatório e único.
- `anuncio_midia.tipo` deve ser `STORY`.
- `anuncio_midia.finalidade` deve ser `STORY`.
- Anúncio e arquivo são obtidos por `anuncio_midia`.
- Não pode existir story sem vínculo canônico.
- Não pode haver duas fontes concorrentes para anúncio/arquivo do story.
- Story deve ter status e janela de exibição.
- Story expirado não aparece publicamente.
- Story pendente de moderação não aparece publicamente.
- Story pode usar imagem ou video, conforme regra de tamanho/duração.
- O benefício premium `STORIES`, se existir, deve apenas habilitar permissão/cota; não deve criar ranking escondido.

## Processamento de mídia

Pipeline recomendado:

1. upload;
2. validação de tamanho;
3. validação de MIME real;
4. leitura de dimensões/duração;
5. antivirus ou scanner equivalente quando disponível;
6. geração de variantes;
7. registro de checksums;
8. moderação;
9. publicação;
10. invalidação de cache quando necessário.

## Banners obrigatórios

Slots obrigatórios:

- Desktop: 1452 x 500 px.
- Mobile: 1080 x 900 px.

As dimensões são fixas. O conteúdo deve ser editável no painel admin.

## Modelo de banners

### `banner_espaco`

- `id`
- `codigo`
- `nome`
- `largura_desktop`
- `altura_desktop`
- `largura_mobile`
- `altura_mobile`
- `ativo`

### `banner`

- `id`
- `banner_espaco_id`
- `titulo`
- `subtitulo`
- `texto_botao`
- `url_destino`
- `alt_text_desktop`
- `alt_text_mobile`
- `arquivo_desktop_id`
- `arquivo_mobile_id`
- `status`
- `inicio_em`
- `fim_em`
- `ordem`
- `versao`
- `criado_por`
- `atualizado_por`
- `criado_em`
- `atualizado_em`

### `banner_versao`

- `id`
- `banner_id`
- `snapshot_json`
- `criado_por`
- `criado_em`

## Painel administrativo de banners

O painel deve permitir:

- upload;
- recorte/crop;
- preview;
- alt text;
- link;
- botão;
- status ativo/inativo;
- agendamento;
- histórico;
- rollback de banner anterior.

Regras administrativas:

- Crop deve gerar exatamente a dimensão do slot.
- Preview deve mostrar desktop e mobile.
- Alt text é obrigatório.
- Link externo deve ser validado.
- Publicação deve ser auditada.
- Deve haver rollback para versão anterior.
- Deve haver status `RASCUNHO`, `AGENDADO`, `PUBLICADO`, `INATIVO` e `ENCERRADO`.
- Deve haver cache invalidado ao publicar ou reverter.

## Acessibilidade e SEO

- Mídia pública deve ter `alt` quando aplicável.
- Banner não deve embutir texto essencial apenas na imagem.
- Imagem de banner não deve substituir H1/semântica da página.
- Metadados de anúncio devem derivar de dados do anúncio, não do nome do arquivo.

## Relatório de saneamento de mídia

O importador deve informar:

- total de arquivos esperados;
- total encontrados;
- total ausentes;
- total quebrados;
- total duplicados;
- total com MIME divergente;
- total com tamanho divergente;
- anúncios sem foto real;
- stories sem `anuncio_midia` canônico;
- documentos privados fora do local esperado.

## Gate de mídia

A V3 não pode virar produção se:

- anúncios ativos prioritários estiverem sem mídia válida sem pendência aprovada;
- placeholders estiverem sendo tratados como fotos reais;
- story existir sem `anuncio_midia` canônico;
- documento privado estiver exposto em rota pública;
- banner principal não tiver variante desktop e mobile válidas;
- storage canônico não estiver documentado.
