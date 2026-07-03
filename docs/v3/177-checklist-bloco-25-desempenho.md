# Checklist Bloco 25 - desempenho read-only

## Escopo

- [x] Endpoints locais read-only de desempenho criados.
- [x] Painel admin local `/admin/desempenho` criado.
- [x] Dados sinteticos locais adicionados sem migration.
- [x] OpenAPI atualizado.
- [x] Smoke HTTP cobre endpoints principais.
- [x] Estado vazio/fallback de anuncio sem metricas previsto.
- [x] Comparativo organico/Premium documentado.
- [x] Politica de prova de resultado documentada.

## Seguranca

- [x] Sem IP bruto.
- [x] Sem User-Agent bruto.
- [x] Sem referer bruto.
- [x] Sem hash interno em resposta.
- [x] Sem CPF/documento privado.
- [x] Sem telefone/WhatsApp bruto.
- [x] Sem storage key, bucket, provider, sha256 ou etag.
- [x] Sem valor monetario, saldo, credito, txid, Pix/Efi ou payload financeiro.
- [x] Sem pixel/tracking externo.
- [x] Sem exportacao.

## RBAC

- [x] `ADMIN` acessa todos os endpoints do bloco.
- [x] `COMERCIAL` acessa resumo, anunciante e desempenho comercial.
- [x] `MODERADOR` acessa desempenho basico de anuncio.
- [x] `MODERADOR` nao acessa resumo comercial agregado.
- [x] `USUARIO` nao acessa admin.
- [x] Sem sessao retorna `401`.

## Premium

- [x] Premium e comparado como exposicao/tendencia.
- [x] `promessaResultadoGarantido=false`.
- [x] `gratuitoLimitado=false`.
- [x] Nao ha texto de resultado garantido.
- [x] Nao ha regra de limitar gratuito.

## Proibicoes confirmadas

- [x] Sem migration nova.
- [x] Sem SQL de schema.
- [x] Sem producao.
- [x] Sem VPS.
- [x] Sem banco de producao.
- [x] Sem Efi real.
- [x] Sem API externa.
- [x] Sem OpenAI.
- [x] Sem dump ou dado real.
- [x] Sem remote.
- [x] Sem push.
- [x] Sem commit.

## Evidencias

- [x] Screenshot desktop.
- [x] Screenshot mobile.
- [x] Screenshot de estado vazio/fallback.
- [x] Validacao mobile estatica.
- [x] Build/test backend.
- [x] Lint/build frontend.
- [x] Smoke HTTP local.
- [x] Scanners de seguranca.

Os itens de evidencia devem ser marcados como concluidos apos execucao real das validacoes no fechamento do bloco.

## Evidencias geradas

- `docs/v3/evidencias/bloco-25/desktop-admin-desempenho.png`;
- `docs/v3/evidencias/bloco-25/mobile-admin-desempenho.png`;
- `docs/v3/evidencias/bloco-25/desktop-admin-desempenho-fallback.png`;
- `docs/v3/evidencias/bloco-25/relatorio-e2e-local-descartavel.md`.

O overlay circular preto com `N` visivel nos screenshots foi tratado como sobreposicao externa da ferramenta de captura. A busca estatica no frontend nao encontrou componente da V3 para esse overlay.
