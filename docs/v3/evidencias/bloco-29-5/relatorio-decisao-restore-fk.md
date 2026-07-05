# Relatorio - decisao tecnica restore/FK

- Bloco: 29.5
- Resultado: DOSSIER_DECISAO_TECNICA_GERADO
- Escolha automatica executada: nao
- Banco avaliado: quarentena sanitizada sem `POST_DATA`
- Banco aprovado para staging final: nao
- Dados reais listados: nao
- Slugs, IDs, chaves, constraints ou nomes sensiveis listados: nao

## Evidencias agregadas

- Restore de quarentena sem `POST_DATA`: OK.
- Sanitizacao da quarentena: OK.
- Validacao de dados sensiveis: OK, total sensivel 0.
- FKs previstas no TOC do dump: 76.
- Falhas `POST_DATA / FK` classificadas no diagnostico bruto sanitizado: 1.
- Heuristica de orfandade com maior impacto agregado: dominio anuncio.
- SEO agregado gerado: OK.
- URLs de anuncio preservaveis estimadas: 520.
- URLs noindex/removidas estimadas: 3.
- URLs desconhecidas do Bloco 28 ainda pendentes: 45.

## Opcao A - parar e exigir novo backup consistente

- Mais conservadora.
- Mantem fidelidade integral entre origem, restore e constraints finais.
- Exige novo dump/backup ou correcao na origem.
- Bloqueia uso da quarentena atual como validacao realista final.

Recomendacao tecnica consolidada no Bloco 29.6: obrigatoria para homologacao/cutover e para qualquer decisao que dependa de integridade transacional completa.

## Opcao B - aceitar quarentena sanitizada apenas para SEO/agregados

- Permitida para inventario, SEO e analise de consistencia.
- Nao aprova staging final.
- Nao aprova teste transacional definitivo da V3.
- Nao aprova importacao definitiva.

Recomendacao tecnica consolidada no Bloco 29.6: aceitavel apenas como insumo auxiliar, desde que os relatorios continuem agregados e sem valores reais.

## Opcao C - preparar plano local de correcao de orfaos apos sanitizacao

- So pode ocorrer em novo bloco.
- Exige mapeamento Pro/humano das relacoes orfas por dominio.
- Deve ser local, sanitizado, reversivel e sem tocar producao.
- Nao pode alterar backup original nem banco de producao.

Recomendacao tecnica consolidada no Bloco 29.6: bloqueada ate revisao Pro/humana. Qualquer correcao local de orfaos exige novo bloco, mapeamento seguro, reversivel e sanitizado.

## Recomendacao motivada

A recomendacao tecnica e manter a Opcao A como caminho obrigatorio para aprovacao final e usar a Opcao B apenas como apoio temporario de SEO/agregados. A Opcao C permanece bloqueada ate revisao Pro/humana porque ha indicio agregado relevante de orfandade e o banco de quarentena nao possui `POST_DATA`.

## Restricoes consolidadas no Bloco 29.6

- O banco de quarentena nao pode ser usado como base de importacao definitiva.
- O banco de quarentena nao pode validar comportamento transacional final.
- O Bloco 29 ainda nao esta fechado materialmente.
- O Bloco 29.5 esta aprovado apenas como diagnostico de quarentena sanitizada.
