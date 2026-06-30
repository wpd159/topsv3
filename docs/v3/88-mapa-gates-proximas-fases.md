# Mapa de gates das proximas fases

Status da entrega: `MAPA DOCUMENTAL DE GATES`.

## Gates principais

| Gate | Condicao minima | Bloqueia |
| --- | --- | --- |
| `GATE_REVISAO_PRO_SCHEMA` | Schema V001..V017 revisado e aprovado | Banco persistente, backend de dominio, entidades JPA, repositories |
| `GATE_FLYWAY_REAL_LOCAL` | Flyway local disponivel sem download ou decisao formal equivalente | Validacao oficial de migrations |
| `GATE_FONTE_REAL_AUTORIZADA` | Fonte autorizada, fora do repo, com origem/responsavel | Dry-run real e importacao |
| `GATE_MANIFESTO_CHECKSUMS` | Manifesto e checksums presentes | Leitura operacional de pacote real |
| `GATE_SEM_VAZAMENTO` | Nenhum dado real em Git, ZIP, docs ou logs versionaveis | Revisao e compartilhamento de pacote |
| `GATE_DRY_RUN_REAL` | Dry-run real autorizado e aprovado | Importacao real |
| `GATE_PAGAMENTOS_EVIDENCIA` | Pagamentos classificados por evidencia | Reconciliacao financeira e creditos |
| `GATE_RETENCAO_DOCUMENTAL` | Politica juridica final definida | Tratamento definitivo de documentos privados |
| `GATE_METRICAS_EQUIVALENCIA` | Metricas reais preservadas/migradas/reimplementadas com equivalencia | Go-live funcional |
| `GATE_PREMIUM_BENEFICIOS` | Premium/beneficios atuais preservados por evidencia | Go-live comercial |

## Classificacao de proximas fases

### Podem seguir como documentais

- ADR de retencao documental final.
- Matriz go/no-go da importacao.
- Politica de sanitizacao de relatorios operacionais.
- Checklist de revisao Pro por dominio.
- Runbook de dry-run real.

### Exigem Pro

- Aprovar schema para uso fora de banco descartavel.
- Criar entidades JPA de dominio.
- Criar repositories/services/controllers de dominio.
- Criar backend funcional de anuncios, pagamentos, creditos, midia e admin.
- Ativar banco local persistente para ensaio controlado.

### Exigem fonte real

- Ajustar dicionario definitivo.
- Confirmar obrigatoriedade de arquivos.
- Confirmar checksums reais.
- Mapear pagamentos por evidencia.
- Validar metricas reais.
- Validar Premium e beneficios atuais.
- Preparar mapa URL real.

### Exigem banco local

- Flyway real.
- Dry-run real com staging local autorizado.
- Ensaios comparativos e reconciliacao.
- Relatorios operacionais de importacao.

### Permanecem bloqueadas

- Importacao real.
- ETL real.
- Leitura de dump real nesta fase.
- Deploy.
- Acesso a producao, VPS, banco de producao, Efi real, OpenAI ou API externa.
- Commit, remote ou push nesta fase.

## Criterio de desbloqueio

Uma fase so deve ser desbloqueada quando seu gate estiver explicitamente aprovado e registrado em documento versionavel sanitizado. A aprovacao nao pode depender de dado real anexado ao repositorio.

## Gate adicional - Bloco 3

O Bloco 3 desbloqueia apenas leitura estrutural do domínio em Java puro. Ele não desbloqueia JPA, repositories, services, endpoints de domínio, importação real, banco persistente, Flyway real, deploy ou fonte real.

Para avançar para backend persistente, registrar aprovação de dependência JPA/Spring Data JPA, revisar mapeamentos com Pro e executar validações em banco local descartável.
