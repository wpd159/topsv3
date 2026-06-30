# Relatório de correção 1D.2

Status: `OK PARA AUDITORIA, AGUARDANDO_REVISAO_PRO`.

## Escopo

A Fase 1D.2 corrige bloqueios remanescentes das migrations Flyway geradas na Fase 1D. Nenhuma migration foi aplicada, Flyway não foi executado, nenhum banco foi acessado e nenhuma fase posterior foi iniciada.

## Correções por item

| Item | Migration | Correção feita | Risco mitigado | Pendência Pro |
| --- | --- | --- | --- | --- |
| 1 | `V010` | `seo_url` aceita `/sitemap.xml` e `/robots.txt`; redirects aceitam ponto apenas em path controlado. | Bloqueio indevido de arquivos públicos essenciais e risco de liberar URL perigosa. | Revisar mapa real de redirects antes da importação. |
| 2 | `V009` | Agregados de visualização e clique usam `id` e chaves normalizadas de origem. | PK composta não força origem conhecida quando origem for desconhecida. | Validar equivalência com métricas atuais. |
| 3 | `V007` | Ledger ganhou `direcao` e CHECK de saldo; ajuste exige auditoria. | Divergência matemática de créditos. | Revisar todos os tipos financeiros antes da implementação. |
| 4 | `V008` | Pagamento ganhou coerência Efí/Pix, legado sem fluxo ativo, aprovação/creditação e compra válida. | Pagamento aprovado sem data, crédito ou método incoerente. | Revisar regras finais de conciliação. |
| 5 | `V008` | `pagamento_evento` ganhou `provedor`; unicidade é por provedor e evento. | Colisão de idempotência entre provedores. | Confirmar identificadores reais por provedor no importador. |
| 6 | `V007` | Criada `grupo_ativacao_beneficio` e FK em `ativacao_beneficio`. | Campo solto sem integridade para pacotes/campanhas. | Revisar fluxo de ativação em backend futuro. |
| 7 | `V014` | `backup_artefato.tipo` inclui `BANCO`, `MIDIA`, `CONFIGURACAO`, `SEO`, `RELEASE`, `LOG`, `MANIFESTO`. | Tipos operacionais previstos ficavam bloqueados. | Definir política real de backup/restore. |
| 8 | `V004` | `anuncio.slug` passou a ser globalmente único. | Reutilização indevida de slug histórico. | Avaliar tabela de reserva/histórico antes do importador. |
| 9 | `V003`/`V017` | Criados índices únicos auxiliares e FKs compostas para estado/cidade/bairro. | Combinação inconsistente de localização. | Validar impacto em importação com dados legados. |
| 10 | `V010` | Índice parcial impede múltiplas versões `APROVADO`/`PUBLICADO` por URL e chave. | Ambiguidade de conteúdo SEO ativo. | Revisar fluxo editorial futuro. |
| 11 | `V011` | Banner em rascunho aceita mídia/alt text nulos; agendado/publicado exige ambos. | Rascunho ficava impossível sem assets prontos. | Revisar estados finais do admin. |
| 12 | `V013` | Auditoria ganhou `antes_hash`/`depois_hash` e comentários de snapshots sanitizados. | JSON livre com conteúdo sensível integral. | Definir sanitizer e política de retenção. |
| 13 | `V015` | Staging reescrito explicitamente; removido `LIKE INCLUDING ALL`. | Auditoria prejudicada por cópia implícita de estrutura. | Revisar campos específicos por entidade antes do importador. |
| 14 | Docs | Importação/financeiro documentam que tabela legada Mercado Pago pode conter Efí e que provedor exige evidência. | Classificação errada por nome de tabela. | Definir evidências do importador futuro. |

## Decisões preservadas

- `documento_usuario.retencao_ate` permanece nullable.
- `politica_retencao` preserva `ENQUANTO_HOUVER_ANUNCIO`.
- Não foi criado expurgo automático.
- Documento privado continua não publicável e com acesso auditável.

## Validação esperada

Validação autorizada nesta fase é apenas estática. Flyway, Docker, banco local, importador, backend de domínio e integrações externas permanecem bloqueados até revisão Pro e autorização futura.

## Complemento 1D.3

A Fase 1D.3 adicionou validação local descartável como preparação técnica, sem aprovar o schema e sem aplicar migrations nesta máquina.

Correções complementares permitidas:

- `V005`: adicionada `anuncio_midia_story_consistencia_chk` para garantir consistência entre `tipo = 'STORY'` e `finalidade = 'STORY'`.
- `V008`: adicionadas `pagamento_id_provedor_uk` e `pagamento_evento_pagamento_provedor_fk` para impedir divergência de provedor entre evento financeiro e pagamento.

Resultado local:

- `PENDENTE_VALIDACAO_POSTGRES_LOCAL`;
- Docker daemon indisponível;
- Flyway CLI não encontrado;
- nenhuma imagem baixada;
- nenhum container, banco, volume ou rede criado;
- nenhuma migration aplicada.
