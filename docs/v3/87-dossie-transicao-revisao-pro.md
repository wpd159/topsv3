# Dossie de transicao para revisao Pro

Status da entrega: `DOSSIE DOCUMENTAL LOCAL, SEM NOVA CAMADA DE IMPORTADOR`.

## Objetivo

Este dossie consolida o estado atual da V3 antes de qualquer fase com fonte real autorizada. Ele serve como ponto unico para revisao Pro, decisao go/no-go e planejamento de dry-run real futuro.

Esta fase nao cria codigo Java, script, migration, SQL, banco, endpoint, entidade JPA, repository, controller de dominio, storage real, ETL ou importacao.

## Estado da Fase 1D

- Migrations `V001` a `V017` criadas em `backend/src/main/resources/db/migration`.
- Validacao SQL estatica: OK.
- Validacao PostgreSQL 16 descartavel: OK via `SQL_ORDENADO_PSQL`.
- Flyway real: pendente, porque CLI/imagem Flyway local nao estava disponivel sem instalacao/download.
- Schema: `AGUARDANDO_REVISAO_PRO`.
- Uso permitido atual: auditoria tecnica local e revisao.
- Uso proibido atual: banco persistente, producao, backend de dominio, entidades JPA, repositories, services, controllers, importacao real e fases dependentes criticas.

## Estado das Fases 2A a 2F

| Fase | Entrega | Status |
| --- | --- | --- |
| 2A | Estrutura local do importador saneador | Contratos Java e relatorios em memoria, sem importacao real |
| 2B | Pacote de entrada | DTOs, tipos de arquivo, validador estrutural e exemplo sanitizado |
| 2C | Dicionario de campos | Catalogo estrutural por arquivo, sensibilidade e obrigatoriedade |
| 2D | Regras de saneamento | Catalogo de regras, escopos, severidades e validador em memoria |
| 2E | Plano/dry-run estrutural | Ordem segura, dependencias, gates e validacao estrutural |
| 2F | Gate de fonte real autorizada | Regras de recebimento, `.gitignore`, exemplo sanitizado e validador sem pacote real |

Nenhuma dessas fases leu dump real, abriu arquivo real de entrada, acessou banco, executou ETL, transformou dados, importou dados ou iniciou uso de fonte real.

## Gates bloqueantes antes de importacao real

Antes de qualquer importacao real, todos os itens abaixo devem estar resolvidos:

- revisao Pro do schema;
- Flyway real local quando ferramenta estiver disponivel sem instalacao/download, ou decisao formal equivalente;
- fonte real autorizada;
- pacote real fora do repositorio e fora de `C:\topsv3`;
- manifesto e checksums;
- origem declarada;
- data/hora de extracao;
- responsavel pela geracao;
- validacao da fonte sem vazamento;
- dry-run real autorizado;
- proibicao de anexar dado real em ZIP de revisao;
- politica de armazenamento operacional temporario;
- decisao final de retencao documental;
- revisao de pagamentos Efi/Mercado Pago por evidencia;
- revisao de metricas reais;
- revisao de Premium e beneficios atuais;
- relatorios publicos ou anexaveis sanitizados;
- pendencias bloqueantes zeradas ou formalmente tratadas.

## Proibicoes mantidas

- Usar producao como bancada de teste.
- Colocar dump dentro do repositorio.
- Versionar dado real em `docs`, `backend`, `frontend`, `scripts` ou qualquer pasta Git.
- Fazer commit com pacote real.
- Importar sem dry-run real aprovado.
- Importar sem manifesto/checksums.
- Criar backend de dominio em cima de schema nao aprovado.
- Criar entidades JPA/repositories antes do gate apropriado.
- Usar Premium como paywall agressivo.
- Limitar WhatsApp, cliques ou contatos do gratuito por monetizacao artificial.
- Publicar documento privado.
- Converter pagamento por nome de tabela em vez de evidencia.

## Proximas fases possiveis

### Exigem revisao Pro

- Aplicacao de schema em banco local nao descartavel.
- Backend de dominio.
- Entidades JPA, repositories e services.
- Endpoints funcionais de dominio.
- Admin funcional.
- Integracao financeira real ou homologada.

### Exigem fonte real autorizada

- Dry-run real de importacao.
- Revisao de obrigatoriedade e checksums do pacote.
- Mapeamento real legado -> V3.
- Manifesto real de midia.
- Reconciliacao real de pagamentos, creditos e Premium.
- Validacao real de metricas e URLs.

### Exigem banco local

- Validacao Flyway real.
- Ensaios de carga de schema.
- Importacao em staging local autorizado.
- Comparacao de execucoes de dry-run real.

### Ainda podem ser documentais

- ADRs de retencao documental.
- Runbooks de operacao de importacao.
- Matrizes go/no-go.
- Politicas de sanitizacao de relatorios.
- Checklist Pro de pagamentos, metricas e Premium.

### Bloqueadas

- Importacao real.
- ETL real.
- Leitura de dump real.
- Abertura de arquivo real de entrada.
- Uso de banco de producao.
- Deploy ou acesso a VPS.
- Remote, push e commit nesta fase.

## Riscos residuais

- Flyway real ainda pendente.
- JUnit/Maven ainda dependem de executor local sem download.
- Fonte real ainda nao conhecida.
- Obrigatoriedade/checksum ainda dependem da fonte real.
- Pagamentos precisam de classificacao por evidencia, sem inferencia por nome de tabela.
- Retencao documental exige decisao juridica final.
- Auditoria precisa de sanitizacao rigorosa.
- Custo de indices/extensoes precisa de revisao Pro.
- Metricas precisam de equivalencia real validada.
- Premium e beneficios atuais precisam ser preservados por evidencia.

## Regra de transicao

A proxima etapa critica so deve comecar quando o gate correspondente estiver aprovado. Ate la, o estado do projeto permanece local, documental/estrutural e sem fonte real.

## Atualização Bloco 3

O Bloco 3 acrescentou uma base de domínio Java puro para futura implementação do backend. Essa base não altera o estado do dossiê: schema continua pendente de revisão Pro, Flyway real continua pendente, fonte real continua bloqueada e nenhuma importação real foi iniciada.

Entidades JPA e repositories permanecem pendentes por ausência de dependência local aprovada.
