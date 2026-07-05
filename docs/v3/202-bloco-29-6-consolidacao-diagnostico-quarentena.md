# Bloco 29.6 - consolidacao do diagnostico de quarentena

## Objetivo

Consolidar o diagnostico do Bloco 29.5, corrigir inconsistencias documentais, registrar o alerta residual da sanitizacao agregada e endurecer os scripts locais de quarentena contra uso acidental de recursos Docker fora dos nomes autorizados.

Este bloco nao executa novo restore, nao restaura `POST_DATA`, nao executa nova sanitizacao, nao corrige orfaos e nao aprova staging final.

## Estado consolidado

- Bloco 29.5 restaurou a quarentena sem `POST_DATA`.
- A quarentena foi sanitizada no Bloco 29.5.
- A validacao posterior por padroes sensiveis registrou `total_sensivel=0`.
- Os relatorios registram 153 colunas sensiveis candidatas tratadas e 4 erros agregados de sanitizacao por coluna.
- Os 4 erros agregados ficam classificados como `ALERTA_SANITIZACAO_AGREGADA_NAO_CLASSIFICADA`.
- O banco de quarentena permanece proibido para staging final, importacao definitiva e validacao transacional final.

## Hardening Docker

Os scripts de quarentena passam a exigir nomes exatos:

- container: `topsv3-bloco29-pg17-quarentena`;
- volume: `topsv3-bloco29-pgdata-quarentena`;
- network: `topsv3-bloco29-net`;
- banco: `topsv3_quarentena`.

Qualquer nome diferente deve resultar em `FALHA_RECURSO_DOCKER_FORA_DO_PREFIXO_AUTORIZADO`.

## Decisao A/B/C

- Opcao A: obrigatoria para homologacao/cutover. Exige novo backup consistente ou correcao da origem/backup antes de staging final.
- Opcao B: permitida apenas como insumo auxiliar para SEO agregado, inventario e analise de consistencia.
- Opcao C: bloqueada ate revisao Pro/humana em novo bloco, com mapeamento seguro, reversivel e sanitizado.

## Proibicoes preservadas

- Sem producao, VPS, banco de producao ou SQL em producao.
- Sem dump novo, SQL bruto versionado ou log bruto versionado.
- Sem backup, dump, midia real, documento real, `.env`, certificado, chave, token, banco local, slug real bruto ou payload sensivel no repositorio ou ZIP.
- Sem `docker prune`, `docker compose down`, stop/rm/volume rm/network rm de TopsWI ou terceiro.
- Sem Pix/Efi real, pagamento, upload, e-mail real, WhatsApp real ou API externa real.
- Sem remote, push ou commit neste bloco.
