# Checkpoint local pre-commit

## Escopo

Este checkpoint consolida o estado local do Tops do Job V3 ate o Bloco 3.1.

O projeto permanece 100% local:

- sem remote configurado;
- sem push executado;
- sem commit executado nesta fase;
- sem acesso a producao;
- sem acesso a VPS;
- sem acesso a banco;
- sem acesso a Efi real;
- sem OpenAI ou API externa;
- sem dump real ou dados reais.

## Correcao aplicada

Arquivo corrigido:

- `backend/src/main/java/br/com/topsdojob/v3/domain/moderacao/RevisaoAnuncio.java`

Alteracao:

- `payloadSolicitadoJson` -> `payloadSolicitado`

Motivo:

- manter aderencia nominal a coluna `payload_solicitado` da tabela `revisao_anuncio`.

## Total staged

Total de arquivos staged apos esta correcao: 334.

Esse total inclui os arquivos acumulados das fases locais anteriores e o novo checkpoint deste Bloco 3.1.

## Principais blocos entregues

- Fase 0: SDD e diretrizes de arquitetura, seguranca, privacidade, SEO, midia, premium, backup e rollback.
- Fase 0.2: protecoes Git, scanners locais e politica de pacotes de revisao.
- Fase 1A: infraestrutura local, Docker Compose local e variaveis de ambiente de exemplo.
- Fase 1B: skeleton backend/frontend e health checks locais.
- Fase 1C: padroes transversais, rotas publicas skeleton, SEO local, admin shell, preservacao visual e validadores locais.
- Fase 1D: migrations Flyway/PostgreSQL `V001` a `V017` em status `AGUARDANDO_REVISAO_PRO`.
- Fase 2A a 2G: estrutura documental e Java puro do importador saneador, sem fonte real, sem ETL e sem banco.
- Bloco 3: dominio Java puro espelhando o schema, sem JPA, repositories, services, controllers ou endpoints.
- Bloco 3.1: correcao nominal curta em `RevisaoAnuncio` e checkpoint local pre-commit.
- Bloco 4: persistencia JPA base local com entidades e repositories minimos, sem controllers, endpoints, services, importador real, banco, migration ou SQL novo.

## Commit sugerido

Sugestao para commit local futuro, se houver autorizacao expressa:

```bash
git commit -m "feat: consolida base v3 local ate bloco 3"
```

Nao executar commit automaticamente nesta fase.

## Riscos residuais

- Schema segue aguardando revisao Pro.
- JPA e Spring Data JPA seguem pendentes.
- Repositories, services e controllers de dominio seguem bloqueados.
- Flyway real ainda depende de ambiente local autorizado.
- Fonte real de importacao ainda nao foi usada nem autorizada para leitura.
- Retencao juridica final de documentos ainda depende de decisao futura.
