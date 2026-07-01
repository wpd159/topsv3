# Plano de execução por fases

## Diretriz

Construir a V3 por fases, com gates objetivos. Dados, SEO, segurança, backup, privacidade, pagamentos e rollback são transversais e devem ser considerados desde a Fase 0.

Nenhuma fase pode usar produção como ambiente de desenvolvimento.

## Fase 0 — SDD

Objetivo:

- documentar visão, regras absolutas, arquitetura, dados, SEO, importação, mídia, premium, segurança, backup, aceite e fases;
- registrar riscos do legado;
- levantar dúvidas bloqueantes;
- não implementar código.

Entregas:

- documentos em `docs/v3`;
- inventário dos insumos disponíveis;
- registro de limitações da auditoria;
- lista de riscos e próximos passos.

Gate:

- SDD inicial aprovado;
- escopo de implementação V3 definido;
- riscos críticos conhecidos registrados.

## Fase 0.1 — fechamento e ADRs

Objetivo:

- fechar divergências do SDD;
- registrar ADRs;
- decidir modelo canônico de stories;
- definir tratamento de provedores legados;
- formalizar ambiente local, privacidade e promoção para VPS.

Entregas:

- modelo de `story_anuncio` com `anuncio_midia_id`;
- nomenclatura oficial de moderação;
- ADRs em `docs/v3/14-decisoes-pendentes-e-adrs.md`;
- documentos de ambiente e privacidade;
- critérios vagos substituídos por referência a ADR quando necessário.

Gate:

- ADR-004 aprovado;
- ADR-005 aprovado;
- pendências com fase bloqueada identificada;
- nenhum código ou migration criado.

## Fase 0.2 — Git e proteção do repositório

Objetivo:

- inicializar ou regularizar repositório Git;
- proteger segredos e artefatos sensíveis;
- definir fluxo de revisão.

Entregas futuras:

- `.gitignore` revisado;
- proteção contra commit de secrets;
- política de branch e revisão;
- convenção de commits;
- checklist de arquivos proibidos.

Gate:

- Git ativo;
- secrets, dumps, backups, uploads e logs fora do versionamento;
- revisão obrigatória para migrations e mudanças financeiras.

## Fase 1A — ambiente local

Objetivo:

- preparar desenvolvimento integralmente local.

Entregas futuras:

- PostgreSQL local isolado;
- storage local;
- captura local de e-mail;
- backend e frontend locais;
- Efí em `LOCAL_MOCK` por padrão;
- secrets separados por ambiente.

Gate:

- ambiente local sobe sem produção;
- `EFI_PIX_MOCK_MODE=true` confirmado;
- nenhum secret real no repositório;
- ADR-010 aprovado.

## Fase 1B — skeleton backend/frontend

Objetivo:

- criar a estrutura mínima do backend e frontend.

Entregas futuras:

- skeleton Spring Boot;
- skeleton Next.js;
- health checks locais;
- contratos iniciais;
- autenticação definida conforme ADR-007.

Gate:

- backend e frontend executam localmente;
- autenticação/sessão não bloqueada;
- contratos iniciais versionados.

## Fase 1C — banco V3

Objetivo:

- definir o desenho físico-conceitual do banco V3 em ambiente local isolado, sem migrations.

Entregas:

- ADR-011 aprovado;
- blueprint do banco V3;
- tabelas finais, staging, auditoria, backup, importação e outbox documentadas;
- extensões PostgreSQL candidatas documentadas;
- constraints e índices planejados;
- estratégia de FTS/trigram e busca pública;
- regras de exclusão lógica, versionamento e concorrência.

Gate:

- modelo revisado contra o SDD;
- nenhuma migration ou SQL criado;
- nenhuma entidade JPA de domínio, repository ou service criado;
- ADR-004 refletido no desenho;
- storage definitivo decidido quando bloquear mídia/importação;
- remote, push, deploy e produção proibidos.

## Fase 1C.1 — preparação da Fase 1D

Objetivo:

- preparar a criação futura de migrations Flyway sem criar SQL, schema ou arquivos `V*.sql`.

Entregas:

- plano de migrations Flyway;
- ordem planejada das migrations V3;
- decisões pré-Fase 1D registradas;
- pendências fortes separadas para revisão na Fase 1D;
- checklist pré-1D.

Gate:

- nenhuma migration criada;
- nenhum SQL criado;
- nenhum banco acessado;
- revisão reforçada exigida para a Fase 1D;
- produção, remote e push continuam proibidos.

## Fase 1C.2 — padrões transversais

Objetivo:

- padronizar contratos transversais de API, logs, auditoria e observabilidade sem implementar regra de negócio.

Entregas:

- padrão de resposta de erro;
- padrão de paginação, filtros e ordenação;
- padrão de `X-Request-Id`;
- padrão de logs sem dados sensíveis;
- padrão de auditoria de comandos críticos;
- padrão para erros 400, 401, 403, 404, 409, 422 e 500;
- padrão de DTO público sem entidade JPA exposta;
- componentes OpenAPI reutilizáveis para fases futuras.

Gate:

- nenhuma migration criada;
- nenhum SQL criado;
- nenhum banco acessado;
- nenhuma entidade JPA, repository ou service de negócio criado;
- Fase 1D não iniciada.

## Fase 1C.3 — camada transversal mínima local

Objetivo:

- implementar infraestrutura transversal mínima de baixo risco no backend e frontend, sem regra de negócio e sem banco.

Entregas:

- filtro de `X-Request-Id`;
- `ApiErrorResponse`;
- `GlobalExceptionHandler`;
- códigos de erro padronizados;
- CORS local restrito;
- health checks locais padronizados;
- cliente API local mínimo no frontend;
- página `/health` local resiliente;
- OpenAPI local coerente com health, request id e erro 500 genérico;
- documentação e checklist da fase.

Gate:

- nenhuma migration criada;
- nenhum SQL criado;
- nenhum banco acessado;
- nenhuma entidade JPA de domínio, repository ou service de negócio criado;
- nenhuma autenticação real implementada;
- nenhuma integração Pix/Efí ou externa acessada;
- Fase 1D não iniciada.

## Fase 1C.4 — rotas públicas skeleton e SEO local seguro

Objetivo:

- preparar skeleton frontend das rotas públicas críticas e contratos SEO locais, sem busca real, dados reais ou backend de domínio.

Entregas:

- rota `/anuncios/[slug]` como skeleton local;
- rota `/acompanhantes/[uf]/[cidade]` como skeleton local;
- rota `/acompanhantes/[uf]/[cidade]/[bairro]` como skeleton local;
- `/robots.txt` local bloqueando indexação;
- `/sitemap.xml` local com lista mínima segura;
- helper de canonical local via `NEXT_PUBLIC_CANONICAL_DOMAIN`;
- documentação e checklist da fase.

Gate:

- nenhuma migration criada;
- nenhum SQL criado;
- nenhum banco acessado;
- nenhum dado real usado;
- nenhuma busca real criada;
- nenhuma API externa chamada;
- nenhum canonical de produção emitido no local;
- Fase 1D não iniciada.

## Fase 1C.5 — GEO/AEO/LLM Visibility local

Objetivo:

- documentar estratégia para que o projeto seja melhor compreendido por buscadores com IA e mecanismos de resposta, sem integração externa e sem conteúdo sensível.

Entregas:

- estratégia GEO/AEO/LLM Visibility;
- diretrizes de conteúdo institucional neutro;
- páginas institucionais skeleton;
- `llms.txt` local;
- diretrizes de schema.org futuro;
- atualização de SEO/robots/sitemap como atividade transversal;
- checklist da fase.

Gate:

- nenhuma IA externa acessada;
- nenhuma integração OpenAI ou semelhante criada;
- nenhum dado real usado;
- nenhum anúncio real criado;
- nenhum conteúdo explícito criado;
- nenhuma migration criada;
- nenhum SQL criado;
- nenhum banco acessado;
- nenhum backend de domínio criado;
- Fase 1D não iniciada.

## Fase 1C.6A — captura pública somente leitura

Objetivo:

- capturar snapshot local sanitizado de textos SEO públicos atuais da produção para análise futura de SEO/GEO/AEO.

Entregas:

- script local de captura com simulação por padrão;
- diretório versionável de metadados sanitizados;
- documentação de escopo, sanitização e riscos;
- inventário de URLs capturadas e ignoradas;
- checklist da fase.

Gate:

- somente GET usado;
- apenas URLs públicas permitidas acessadas;
- admin, login, API privada, banco, VPS, Efí e IA externa não acessados;
- HTML bruto completo, imagens, vídeos, telefone, WhatsApp, documentos e dados privados não versionados;
- nenhuma migration criada;
- nenhum SQL criado;
- Fase 1D não iniciada.

## Fase 1C.6B — admin shell local

Objetivo:

- criar o shell administrativo local e o mapa estrutural dos módulos previstos no SDD, sem regra de negócio.

Entregas:

- rota `/admin`;
- páginas placeholder dos módulos admin;
- componentes de shell administrativo;
- documentação do shell local;
- mapa dos módulos admin;
- checklist da fase.

Gate:

- admin ainda não funcional;
- todas as páginas admin em `noindex`;
- nenhuma autenticação real criada;
- nenhuma ação real criada;
- nenhum dado real usado;
- nenhum backend de domínio criado;
- nenhuma migration criada;
- nenhum SQL criado;
- nenhum banco acessado;
- Fase 1D não iniciada.

## Fase 1C.7 — preservação visual atual

Objetivo:

- inventariar as fontes visuais locais disponíveis;
- documentar que a V3 deve preservar o visual atual do Tops do Job;
- impedir que skeletons locais sejam tratados como layout final;
- registrar pendências de fonte visual atual antes do frontend real.

Entregas:

- diretriz de preservação visual;
- inventário local de fonte visual atual;
- checklist de preservação visual;
- registro de `PENDENTE_FONTE_VISUAL_ATUAL` quando não houver fonte visual confiável no workspace;
- dimensões fixas de banner preservadas: desktop 1452 x 500 px e mobile 1080 x 900 px.

Gate:

- nenhum redesign criado;
- nenhuma nova identidade visual criada;
- nenhuma nova paleta ou tipografia definida;
- nenhum layout final criado;
- nenhum dado real ou conteúdo explícito usado;
- nenhuma API externa chamada;
- nenhuma migration criada;
- nenhum SQL criado;
- nenhum banco acessado;
- Fase 1D não iniciada.

## Fase 1C.8 — validação local de rotas públicas e SEO skeleton

Objetivo:

- criar validação local estática para rotas públicas críticas e SEO skeleton;
- preservar `/anuncios/[slug]` como contrato público absoluto;
- bloquear rotas alternativas de anúncio;
- proteger robots, sitemap, canonical local e admin `noindex`.

Entregas:

- script `scripts/local/validar-rotas-publicas-seo-local.ps1`;
- documentação da validação local;
- checklist de validação de rotas e SEO;
- atualização das diretrizes de SEO, contribuição e rotas skeleton.

Gate:

- `/anuncios/[slug]` validado;
- `/acompanhantes/[uf]/[cidade]` validado;
- `/acompanhantes/[uf]/[cidade]/[bairro]` validado;
- rotas alternativas de anúncio ausentes;
- robots local bloqueando indexação;
- sitemap local sem domínio de produção;
- canonical de produção ausente no local;
- admin permanece `noindex`;
- nenhum dado real, imagem real, conteúdo explícito ou chamada externa;
- nenhuma migration criada;
- nenhum SQL criado;
- nenhum banco acessado;
- Fase 1D não iniciada.

## Fase 1D — migrations

Objetivo:

- criar migrations iniciais reproduzíveis para auditoria Pro, sem aplicar banco nesta execução.

Entrada obrigatória:

- documentos da Fase 1C.1 revisados;
- pendências pré-1D resolvidas ou explicitamente aceitas;
- confirmação de que o alvo é local e descartável.

Entregas desta execução:

- Flyway como ferramenta de migrations da V3;
- migrations SQL explícitas V001 a V017;
- migrations versionadas por Git;
- documentação da matriz de tabelas;
- checklist de auditoria Pro;
- registro de pendências e decisões de schema;
- validador SQL estático sem banco;
- nenhuma migration automática por ORM;
- revisão por Git e ZIP de revisão;
- proibição de executor próprio de migrations.

Gate:

- status `OK PARA AUDITORIA, AGUARDANDO_REVISAO_PRO`;
- nenhuma migration aplicada;
- Flyway não executado;
- nenhum banco acessado ou iniciado;
- runtime não executa migration no startup comum;
- nenhuma migration tratada como aprovada sem revisão Pro;
- nenhuma entidade JPA, repository, service de domínio, controller de domínio ou importador criado;
- Fase 1E, Fase 2 e fases dependentes do schema não iniciadas.

Observação: a primeira execução da Fase 1D gera o material SQL para auditoria. A aplicação em banco local descartável, validação Flyway real, importador e backend de domínio exigem autorização futura.

## Fase 2 — importador

Objetivo:

- migrar dados por ETL auditável e idempotente.

### Fase 2A — estrutura local do importador saneador

Objetivo:

- criar estrutura local do módulo de importação saneadora, sem importar dados.

Entregas:

- pacote `backend/src/main/java/br/com/topsdojob/v3/importacao`;
- enums de pendência;
- DTOs/records de relatório, manifesto, mapeamento legado -> V3 e mapa URL atual -> V3;
- builder de relatório em memória;
- skeleton estrutural sem banco, sem JPA, sem repository e sem controller;
- documentação e checklist da fase.

Gate:

- nenhum dump lido;
- nenhum dado real usado;
- nenhum banco acessado;
- nenhuma migration criada;
- nenhum SQL alterado;
- nenhuma importação real iniciada;
- produção, VPS, Efí real, OpenAI, API externa, remote, push e commit proibidos.

### Fase 2B — contratos do pacote de entrada

Objetivo:

- definir contratos locais para o pacote de entrada da futura importação, sem ler dump e sem acessar banco.

Entregas:

- pacote `backend/src/main/java/br/com/topsdojob/v3/importacao/pacote`;
- enum de tipos de arquivo esperados;
- enum de status de arquivo declarado;
- DTOs/records de pacote e arquivo;
- resultado de validação estrutural;
- validador em memória sem I/O;
- exemplo sanitizado;
- documentação e checklist da fase.

Gate:

- nenhum dump lido;
- nenhum dado real usado;
- nenhum arquivo real de entrada aberto;
- nenhum banco acessado;
- nenhuma migration criada;
- nenhum SQL alterado;
- nenhuma importação real ou ETL iniciados;
- produção, VPS, Efí real, OpenAI, API externa, remote, push e commit proibidos.

### Fase 2C — dicionário estrutural de campos

Objetivo:

- definir dicionário estrutural de campos esperados por tipo de arquivo do pacote de importação, sem fonte real e sem banco.

Entregas:

- pacote `backend/src/main/java/br/com/topsdojob/v3/importacao/dicionario`;
- tipos de campo;
- classificações de sensibilidade;
- classificações de obrigatoriedade;
- DTOs/records de campo e dicionário por arquivo;
- catálogo estrutural por tipo de arquivo;
- validador em memória sem I/O;
- documentação e checklist da fase.

Gate:

- nenhum dump lido;
- nenhum dado real usado;
- nenhum arquivo real de entrada aberto;
- nenhum banco acessado;
- nenhuma migration criada;
- nenhum SQL alterado;
- nenhuma importação real ou ETL iniciados;
- produção, VPS, Efí real, OpenAI, API externa, remote, push e commit proibidos.

### Fase 2D — regras estruturais de saneamento e transformação

Objetivo:

- definir catálogo estrutural de regras de saneamento e transformação legado -> V3, sem fonte real, sem I/O e sem banco.

Entregas:

- pacote `backend/src/main/java/br/com/topsdojob/v3/importacao/saneamento`;
- tipos de regra;
- severidades de regra;
- escopos de regra;
- DTOs/records de regra e resultado estrutural;
- catálogo estrutural por escopo;
- validador em memória sem I/O;
- documentação e checklist da fase.

Gate:

- nenhum dump lido;
- nenhum dado real usado;
- nenhum arquivo real de entrada aberto;
- nenhuma transformação real executada;
- nenhum banco acessado;
- nenhuma migration criada;
- nenhum SQL alterado;
- nenhuma importação real ou ETL iniciados;
- produção, VPS, Efí real, OpenAI, API externa, remote, push e commit proibidos.

### Fase 2E - plano de execucao e dry-run estrutural

Objetivo:

- definir plano local de execucao da futura importacao e contrato de dry-run estrutural, sem fonte real, sem I/O e sem banco.

Entregas:

- pacote `backend/src/main/java/br/com/topsdojob/v3/importacao/plano`;
- tipos de etapa;
- status e criticidades;
- DTOs/records de dependencia, etapa, plano e resultado;
- catalogo de ordem segura;
- validador em memoria sem I/O;
- testes estruturais;
- documentacao e checklist da fase.

Gate:

- nenhum dump lido;
- nenhum dado real usado;
- nenhum arquivo real de entrada aberto;
- nenhuma transformacao real executada;
- nenhum banco acessado;
- nenhuma migration criada;
- nenhum SQL alterado;
- nenhuma importacao real ou ETL iniciados;
- nenhuma entidade JPA, repository, service, controller ou endpoint funcional criado;
- producao, VPS, Efi real, OpenAI, API externa, remote, push e commit proibidos.

### Fase 2F - gate de fonte real autorizada

Objetivo:

- definir gate operacional para fonte real autorizada antes de qualquer leitura de dump, export, midia, pagamento, metrica ou arquivo legado.

Entregas:

- documentacao do gate de fonte real;
- runbook de recebimento do pacote real futuro;
- checklist da fase;
- reforco de `.gitignore` para artefatos reais de importacao;
- script local `scripts/local/validar-fonte-importacao-local.ps1`;
- exemplo sanitizado de instrucao de recebimento.

Gate:

- fonte real ainda nao usada;
- pacote real deve ficar fora do repositorio;
- nenhum dump lido;
- nenhum dado real usado;
- nenhum arquivo real de entrada aberto;
- nenhuma transformacao real executada;
- nenhum banco acessado;
- nenhuma migration criada;
- nenhum SQL alterado;
- nenhuma importacao real ou ETL iniciados;
- nenhuma entidade JPA, repository, service, controller ou endpoint funcional criado;
- producao, VPS, Efi real, OpenAI, API externa, remote, push e commit proibidos.

### Fase 2G - dossie de transicao para revisao Pro

Objetivo:

- consolidar o estado da Fase 1D e das Fases 2A a 2F antes de revisao Pro, fonte real autorizada e dry-run real futuro.

Entregas:

- dossie de transicao para revisao Pro;
- mapa de gates das proximas fases;
- checklist da fase;
- atualizacao cirurgica da documentacao existente.

Gate:

- nenhuma nova camada de importador criada;
- nenhum codigo Java criado;
- nenhum script criado;
- nenhum dump lido;
- nenhum dado real usado;
- nenhum arquivo real de entrada aberto;
- nenhuma transformacao real executada;
- nenhum banco acessado;
- nenhuma migration criada;
- nenhum SQL alterado;
- nenhuma importacao real ou ETL iniciados;
- nenhuma entidade JPA, repository, service, controller ou endpoint funcional criado;
- producao, VPS, Efi real, OpenAI, API externa, remote, push e commit proibidos.

Entregas futuras:

- staging;
- mapeamento legado -> V3;
- relatórios com códigos obrigatórios;
- manifesto de mídia;
- reconciliação de créditos/pagamentos/premium;
- classificação de pagamentos Efí, Mercado Pago legado e registros sem provedor;
- mapa URL atual -> V3.

Gate:

- duas execuções consecutivas reproduzíveis;
- divergências não financeiras documentadas;
- divergência financeira final zero;
- financeiro conciliado;
- SEO pendente tratado;
- ADR-006 resolvido se bloquear storage/mídia;
- ADR-005 refletido na importação.

## Fase 3 — backend

Objetivo:

- implementar contratos de domínio da V3.

Ordem recomendada:

1. usuários/autenticação/sessões;
2. anúncios;
3. localidades;
4. mídia;
5. stories;
6. moderação;
7. premium;
8. créditos;
9. financeiro com `PagamentoProvider` e `EfiPixProvider`;
10. busca;
11. SEO/sitemap;
12. dashboard/admin;
13. comercial;
14. suporte;
15. auditoria;
16. backup.

Gate:

- testes de domínio;
- testes de integração;
- permissões por papel;
- contratos estáveis para frontend;
- Pix local em `LOCAL_MOCK` sem credenciais reais.

## Fase 4 — frontend

Objetivo:

- entregar experiência pública preservando URLs e SEO.

Entregas futuras:

- home;
- busca;
- listagens por UF/cidade/bairro;
- página `/anuncios/[slug]`;
- canonical;
- metadados;
- JSON-LD;
- banner administrável;
- sitemap/robots integrados.

Gate:

- crawler comparativo aprovado;
- URLs prioritárias respondem;
- sitemap limpo;
- canonical sem `www`;
- staging `noindex`.

## Fase 5 — admin

Objetivo:

- entregar operação administrativa segura.

Entregas futuras:

- usuários;
- anúncios;
- moderação;
- mídia;
- premium;
- créditos;
- financeiro;
- banners;
- SEO;
- comercial;
- suporte;
- backup;
- auditoria.

Gate:

- ADMIN, MODERADOR e COMERCIAL testados;
- ações críticas auditadas;
- backup manual disponível;
- rollback de banner funcionando;
- métricas do admin definidas por `DECISAO_PENDENTE: ADR-009` antes da Fase 7.

## Fase 6 — SEO e IA

Objetivo:

- consolidar SEO programático, conteúdo assistido e validação automatizada sem gerar páginas fracas ou duplicadas.

Entregas futuras:

- registro SEO;
- diagnóstico de indexabilidade;
- mapa de redirects;
- validação Search Console;
- IndexNow;
- regras para sugestões de IA;
- aprovação humana de conteúdo gerado;
- bloqueio de geração em massa sem inventário.

Gate:

- nenhuma página IA publicada sem aprovação;
- sitemap sem URL fraca/noindex;
- redirects testados;
- canonical consistente.

## Fase 7 — testes reais

Objetivo:

- validar V3 com cópia segura/sanitizada ou ambiente controlado com dados reais conforme política aprovada.

Entregas futuras:

- execução sombra;
- importação completa;
- comparação funcional;
- comparação SEO;
- reconciliação de créditos;
- validação de mídia;
- teste de pagamentos Efí em homologação;
- webhook Efí validado e testado contra duplicidade;
- teste de carga com thresholds definidos em `DECISAO_PENDENTE: ADR-009`;
- teste de falhas.

Gate:

- critérios de aceite cumpridos;
- sem risco crítico/alto aberto;
- relatórios aprovados;
- thresholds de performance aprovados no ADR-009.

## Fase 8 — migração

Objetivo:

- executar delta final e preparar virada.

Entregas futuras:

- comunicação de janela;
- congelamento curto de escrita;
- backup final;
- delta final;
- reconciliação final;
- validação de URLs;
- validação de mídia;
- validação de admin;
- RPO, RTO e retenção aprovados no ADR-008.

Gate:

- go/no-go aprovado;
- rollback pronto;
- backup validado;
- responsáveis disponíveis;
- privacidade e jurídico aprovados.

## Fase 9 — virada

Objetivo:

- colocar V3 em produção com monitoramento intensivo.

Entregas futuras:

- troca controlada de rota/proxy/symlink/DNS conforme arquitetura;
- smoke tests;
- monitoramento de 5xx/404;
- validação de login/admin;
- validação de anúncio;
- validação de busca;
- validação de pagamento Pix Efí;
- validação de sitemap/robots/canonical.

Gate:

- smoke tests aprovados;
- limite de erros definido por `DECISAO_PENDENTE: ADR-009` antes da Fase 7;
- sem qualquer divergência financeira;
- decisão manter/rollback tomada dentro da janela.

## Fase 10 — estabilização

Objetivo:

- estabilizar a V3 e encerrar dependências legadas com segurança.

Entregas futuras:

- monitoramento de SEO por Search Console;
- monitoramento de 404/5xx;
- acompanhamento de pagamentos/créditos;
- acompanhamento de webhooks Efí, conciliação e eventos com falha;
- acompanhamento de importação pendente;
- suporte a usuários;
- revisão de performance com thresholds definidos no ADR-009;
- runbook de incidentes atualizado;
- plano de desativação de compatibilidades legadas.

Gate:

- janela de estabilização definida por `DECISAO_PENDENTE: ADR-009` antes da Fase 7;
- pendências críticas resolvidas;
- legado arquivado/descomissionado apenas com aprovação;
- backups e restauração continuam operando.

## Atividades transversais

Durante todas as fases:

- segurança;
- auditoria;
- observabilidade;
- backup;
- rollback;
- documentação;
- testes;
- proteção de dados;
- preservação SEO;
- preservação visual;
- operação comercial.

## Bloco 3 - backend de domínio base local

Objetivo:

- criar base local de domínio em Java puro espelhando estruturalmente as migrations `V001` a `V017`;
- preparar enums e records para futura implementação do backend;
- manter JPA, repositories, services, controllers e endpoints funcionais como pendências até dependência e revisão futura.

Gate:

- nenhuma migration nova;
- nenhum SQL alterado;
- nenhum banco acessado;
- nenhum importador real iniciado;
- nenhum dump ou dado real usado;
- nenhum `jakarta.persistence` ou Spring Data JPA adicionado sem autorização;
- `PENDENTE_JPA_JAKARTA_PERSISTENCE` e `PENDENTE_REPOSITORIES_SPRING_DATA_JPA` documentados.

## Bloco 4 - persistencia JPA base local

Objetivo:

- adicionar dependencias minimas de JPA/Spring Data e driver PostgreSQL runtime;
- criar entidades JPA principais alinhadas ao schema `V001` a `V017`;
- criar repositories minimos sem query customizada;
- manter controllers, endpoints, services de negocio, importador real, banco e dados reais bloqueados.

Gate:

- nenhuma migration nova;
- nenhum SQL alterado;
- nenhum banco acessado;
- nenhuma importacao ou ETL real;
- nenhum controller funcional;
- nenhum endpoint de dominio;
- nenhum service de negocio;
- nenhum remote, push ou commit nesta fase.

## Bloco 5 - API publica minima de leitura

Objetivo:

- criar DTOs publicos, mappers, services read-only e controllers GET para leitura publica;
- expor detalhe de anuncio por slug, listagem por cidade, listagem por bairro e metadados SEO locais;
- preservar as rotas publicas frontend `/anuncios/[slug]`, `/acompanhantes/[uf]/[cidade]` e `/acompanhantes/[uf]/[cidade]/[bairro]`;
- manter admin funcional, autenticacao real, acoes criticas, financeiro, Pix/Efi, moderacao real e importador real bloqueados.

Gate:

- nenhum documento privado ou campo sensivel exposto;
- nenhum banco persistente acessado;
- nenhuma migration criada;
- nenhum SQL alterado;
- nenhum dado real ou dump usado;
- nenhum endpoint de escrita;
- nenhum remote, push ou commit nesta fase.

## Bloco 6 - frontend publico integrado a API local

Objetivo:

- integrar as rotas publicas skeleton ao contrato backend publico de leitura;
- preservar visual atual sem redesign, nova paleta ou nova tipografia;
- manter fallback seguro quando backend local estiver indisponivel;
- corrigir aceite de `/sitemap.xml` e `/robots.txt` no SEO backend local;
- corrigir paginacao publica para filtrar anuncios publicados/aprovados antes da pagina final.

Gate:

- nenhum dado real, dump, imagem real ou arquivo real de entrada;
- nenhum WhatsApp publico sem politica aprovada;
- nenhuma storage key, hash, bucket ou URL privada exposta;
- nenhuma migration ou SQL alterado;
- nenhum banco de producao, API externa, producao ou VPS;
- nenhum admin funcional, autenticacao real, Pix/Efi, financeiro ou moderacao real;
- nenhum remote, push ou commit nesta fase.

## Bloco 7 - e2e local descartavel

Objetivo:

- executar PostgreSQL local descartavel;
- aplicar migrations V001-V017 sem alterar SQL;
- inserir dados sinteticos minimos e neutros;
- iniciar backend local em profile seguro;
- executar smoke HTTP da API publica;
- validar frontend lint/build e backend compile/test.

Gate:

- nenhum volume persistente;
- nenhum dado real, dump ou arquivo real de entrada;
- nenhum WhatsApp publico, documento privado, storage key, hash ou bucket exposto;
- nenhuma migration criada;
- nenhum SQL de schema alterado;
- nenhum admin funcional, autenticacao real, Pix/Efi funcional ou importador real;
- nenhum acesso a producao, VPS, banco de producao, Efi real, OpenAI ou API externa;
- nenhum remote, push ou commit nesta fase.
