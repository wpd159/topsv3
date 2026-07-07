# Bloco 62 - Decisao tecnica: frontend de producao como base da V3

## Status

- Status do bloco: DOCUMENTAL_CONCLUIDO
- Decisao recomendada: OPCAO_B_FRONTEND_PRODUCAO_COMO_BASE
- Implementacao de migracao: NAO_EXECUTADA_NESTE_BLOCO
- Escopo alterado: somente documentacao de frontend
- Fonte de producao auditada: `C:\clone\topsdojob-frontend`, somente leitura
- Frontend V3 auditado: `C:\topsv3\frontend`

## Decisao

A V3 deve parar a estrategia de ajustes visuais por aproximacao sobre o frontend atual e adotar como estrategia principal a Opção B: usar o frontend de producao em `C:\clone\topsdojob-frontend` como base visual e funcional da camada publica da V3, adaptando gradualmente as integracoes para os contratos seguros do backend V3.

Esta decisao nao autoriza copiar regras de negocio antigas, chamadas reais, pagamentos, uploads, stores, auth de producao, Pix/Efi, webhooks, endpoints externos, dados reais ou credenciais. O clone serve como fonte visual e funcional de UX; as integracoes devem ser reencaminhadas para os adaptadores e contratos V3.

## Motivo

O HML subiu tecnicamente, mas a interface V3 foi reprovada por baixa paridade visual e funcional com a producao atual. Os blocos de transplante parcial melhoraram shell, cards, detalhe, header e wizard, mas a abordagem ainda depende de microcorrecoes sucessivas e nao garante equivalencia estrutural com o site real.

## Por que a Opção A nao deve ser a estrategia principal

A Opção A, continuar aproximando o frontend V3 atual por ajustes incrementais, fica descartada como estrategia principal porque:

- preserva uma base visual nascida como skeleton tecnico;
- exige reconstruir manualmente padroes que ja existem no frontend de producao;
- aumenta o risco de divergencia em filtros, cards, listagens, detalhe, wizard, SEO e mobile;
- tende a gerar muitos blocos pequenos sem convergir para paridade real;
- ja demonstrou limite pratico apos HML e auditoria visual.

A Opção A pode continuar existindo apenas como apoio pontual para validadores, adapters, contratos e telas internas que a V3 ja estabilizou.

## Limites do Bloco 62

Este bloco nao implementa migracao, nao altera o clone e nao substitui arquivos do frontend V3. Tambem nao toca em backend, banco, migrations, auth/RBAC, restore, VPS, importacao, Pix/Efi, pagamento, upload real, webhook, dados reais, producao ou push.

## Artefatos criados

- `docs/v3/278-bloco-62-decisao-frontend-producao-base-v3.md`
- `docs/v3/279-checklist-bloco-62-decisao-frontend-producao-base-v3.md`
- `docs/v3/FRONTEND-plano-migracao-producao-para-v3.md`
- `docs/v3/evidencias/bloco-62/relatorio-decisao-opcao-b.md`
- `docs/v3/evidencias/bloco-62/relatorio-inventario-frontend-producao.md`
- `docs/v3/evidencias/bloco-62/relatorio-inventario-frontend-v3.md`
- `docs/v3/evidencias/bloco-62/relatorio-plano-fases.md`
- `docs/v3/evidencias/bloco-62/relatorio-riscos.md`

## Resultado esperado para os proximos blocos

Os proximos blocos devem tratar a troca de base do frontend como migracao controlada: primeiro inventario e freeze de contratos, depois incorporacao da base visual de producao, depois reencaminhamento de APIs para a V3, e somente depois validacao HML. Nenhum passo deve reativar producao, dados reais, Pix/Efi real, upload real ou webhook real.
