# Bloco 31 - validacao sintetica API/SEO

## Objetivo

Consolidar o checkpoint local do Bloco 30 e validar a V3 com base sintetica local em ambiente descartavel/controlado, sem dados reais, producao, VPS, backup, restore de producao ou quarentena como staging final.

## Checkpoint

- Commit local do Bloco 30: `2acc60b`.
- Mensagem: `docs: retoma v3 com base sintetica local no bloco 30`.
- Remote: vazio.
- Push: nao executado.

## Implementacao

- `scripts/local/validar-e2e-local-descartavel.ps1` foi estendido para aceitar prefixo Docker, smoke HTTP customizado e overlay da fixture sintetica.
- `scripts/local/validar-e2e-sintetico-local.ps1` chama o E2E com prefixo `topsv3-e2e-sintetico`.
- `scripts/local/validar-api-publica-sintetica-local.ps1` executa o smoke legado e os endpoints `demo-*` da fixture.
- `scripts/local/validar-seo-sintetico-local.ps1` valida rotas SEO sinteticas.

## Resultado

- PostgreSQL descartavel com `postgres:17`: OK.
- Migrations V001-V017 aplicadas: OK.
- Seeds sinteticos existentes aplicados: OK.
- Fixture `v3-dados-sinteticos.json` aplicada como overlay: OK.
- Backend local iniciado em profile local: OK.
- API publica sintetica: OK.
- SEO sintetico: OK.
- `BLOQUEADO` sem WhatsApp publico: OK.
- Pendente/rejeitado fora da publicacao normal: OK.

## Limites

O Bloco 31 nao substitui revisao Pro/humana, restore real, sanitizacao real ou homologacao/cutover. A validacao e local, sintetica e descartavel.
