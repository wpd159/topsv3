# Ambiente local e promoção para VPS

## Objetivo

Definir como a V3 deve sair do desenvolvimento local para staging, homologação e produção sem usar produção como bancada de teste.

Esta diretriz é documental. A Fase 1A cria apenas infraestrutura local isolada, sem conexão externa, credenciais reais ou integração externa.

## Princípios

- O desenvolvimento deve ser integralmente local até passar pelos gates da fase correspondente.
- Produção nunca deve ser usada para desenvolvimento, depuração ou teste exploratório.
- Cada ambiente deve ter banco, storage, secrets, logs e credenciais separados.
- Dumps, backups, uploads e logs ficam fora do Git.
- Secrets não entram no repositório, frontend, JAR, imagem, ZIP de entrega ou backup comum baixável pelo painel.
- Promoção para VPS só ocorre por artefato reproduzível e revisado.

## Ambientes

### LOCAL

Finalidade:

- desenvolvimento diário;
- testes automatizados e manuais;
- simulação de integrações externas;
- validação inicial de migrations futuras.

Componentes esperados:

- PostgreSQL local isolado;
- storage local para mídia e documentos;
- captura local de e-mail;
- Efí em `LOCAL_MOCK` por padrão;
- frontend local;
- backend local;
- variáveis e secrets locais sem credenciais reais de produção.

Regras:

- `EFI_PIX_MOCK_MODE=true` é o padrão.
- O mock local nunca pode acessar produção.
- Dados reais só podem ser usados se sanitizados e aprovados.
- Uploads e logs locais não entram no Git.

Arquivos locais preparados na Fase 1A:

- `.env.local.example`;
- `infra/local/docker-compose.local.yml`;
- `scripts/local/validar-ambiente-local.ps1`;
- `scripts/local/subir-local.ps1`;
- `scripts/local/status-local.ps1`;
- `scripts/local/logs-infra-local.ps1`;
- `scripts/local/parar-local.ps1`;
- `scripts/local/limpar-local.ps1`.

Serviços locais preparados:

- PostgreSQL local;
- MinIO local S3-compatible;
- Mailpit para captura local de e-mail.

Os volumes ficam em `storage-local/`, fora do Git. A execução exige Docker já instalado manualmente; a fase não instala ferramentas.

### TESTES LOCAIS

Finalidade:

- executar testes de domínio;
- validar contratos backend/frontend;
- validar importador com amostras controladas;
- validar idempotência de pagamentos e webhooks simulados.

Gate:

- suíte local aprovada;
- configuração local documentada;
- nenhum secret real no repositório;
- nenhum dado pessoal indevido em logs.

### STAGING ISOLADO

Finalidade:

- validar artefato em VPS ou ambiente equivalente antes de homologação;
- executar smoke tests;
- testar configuração de infraestrutura sem tráfego público.

Regras:

- staging deve ser autenticado;
- staging deve ser `noindex`;
- staging não envia IndexNow;
- staging não usa credenciais de produção;
- staging não deve emitir canonical de produção em página indexável.

### HOMOLOGAÇÃO

Finalidade:

- validar fluxos externos permitidos, como Efí em homologação;
- executar testes com operadores autorizados;
- validar relatórios de importação, conciliação e SEO.

Regras:

- Pix real deve usar exclusivamente homologação Efí;
- credenciais ficam fora do repositório;
- massa de dados deve ser sanitizada ou formalmente autorizada;
- qualquer divergência financeira bloqueia promoção.

### PRODUÇÃO

Finalidade:

- tráfego real;
- operação comercial;
- pagamentos reais;
- dados pessoais reais.

Regras:

- produção só recebe artefato reproduzível;
- produção não compila código manualmente;
- produção não roda seeder de dados;
- produção não executa migration fora do processo aprovado;
- rollback deve estar ensaiado antes da virada.

## Fluxo de promoção

```text
LOCAL
-> TESTES LOCAIS
-> STAGING ISOLADO
-> HOMOLOGAÇÃO
-> PRODUÇÃO
```

Cada passagem exige:

- artefato identificado;
- configuração separada;
- checklist de segurança;
- smoke test;
- registro de aprovação;
- plano de retorno.

## Fase 1 detalhada

### Fase 1A: repositório e infraestrutura local

Objetivo:

- criar o repositório versionado;
- configurar proteção básica;
- definir estrutura local de backend, frontend, banco, storage, e-mail local e variáveis por ambiente.

Não inclui:

- schema SQL final;
- integração externa real;
- deploy de produção.

### Fase 1B: skeleton backend/frontend

Objetivo:

- criar estrutura mínima executável de backend e frontend;
- validar contratos iniciais;
- manter tela e API sem regra financeira real.

Não inclui:

- fluxo Pix real;
- importador definitivo;
- migrations de domínio.

### Fase 1C: banco V3

Objetivo:

- definir desenho físico-conceitual do banco V3 em ambiente local isolado;
- documentar tabelas, constraints, índices, extensões, busca, auditoria, backup e importação;
- preparar a Fase 1D sem criar banco físico executável.

Não inclui:

- execução em produção;
- dados reais sem sanitização;
- migrations;
- SQL executável;
- entidades JPA de domínio;
- repositories;
- services de negócio.

### Fase 1C.1: preparação da Fase 1D

Objetivo:

- preparar convenções, ordem, riscos e decisões de migrations sem criar SQL;
- revisar health/OpenAPI e Compose local;
- manter produção intocada e sem acesso a banco.

Não inclui:

- migrations;
- arquivos `V*.sql`;
- execução de Flyway;
- conexão com banco;
- Docker iniciado;
- Fase 1D iniciada.

### Fase 1C.4: rotas públicas skeleton e SEO local seguro

Objetivo:

- criar skeleton local das rotas públicas críticas;
- validar `robots.txt` e `sitemap.xml` locais;
- manter canonical local sem domínio de produção;
- preservar rotas futuras sem implementar busca, anúncios, SEO real ou conteúdo final.

Não inclui:

- busca real;
- anúncio real;
- conteúdo adulto real;
- dados reais;
- backend de domínio;
- API externa;
- canonical de produção em ambiente local;
- migrations;
- SQL;
- conexão com banco;
- Fase 1D iniciada.

### Fase 1C.5: GEO/AEO/LLM Visibility local

Objetivo:

- documentar estratégia de clareza institucional para buscadores com IA;
- criar skeleton local de páginas institucionais neutras;
- preparar `llms.txt` local;
- orientar schema.org futuro sem JSON-LD final;
- manter SEO tradicional, privacidade, segurança e compliance como gates.

Não inclui:

- acesso a OpenAI, ChatGPT, Gemini, Perplexity ou IA real;
- integração com IA externa;
- scraping;
- spam;
- publicação automática;
- conteúdo adulto explícito;
- dados reais;
- anúncio real;
- backend de domínio;
- banco;
- migration;
- SQL;
- Fase 1D iniciada.

### Fase 1C.6B: admin shell local

Objetivo:

- criar estrutura local do admin;
- mapear módulos operacionais previstos;
- manter páginas admin em `noindex`;
- preparar navegação sem autenticação funcional ou ações reais.

Não inclui:

- login real;
- autenticação funcional;
- RBAC real;
- dados reais;
- chamadas a backend;
- ações críticas;
- moderação real;
- financeiro, créditos ou Pix reais;
- banco;
- migration;
- SQL;
- Fase 1D iniciada.

### Fase 1C.7: preservação visual atual

Objetivo:

- documentar que a V3 deve preservar o visual atual;
- inventariar fontes visuais locais disponíveis;
- registrar `PENDENTE_FONTE_VISUAL_ATUAL` quando não houver fonte confiável no workspace;
- manter skeletons locais como placeholders temporários, sem layout final.

Não inclui:

- redesign;
- nova identidade visual;
- nova paleta;
- nova tipografia;
- layout final de home, listagem, anúncio ou admin;
- captura externa de prints;
- dados reais;
- conteúdo explícito;
- API externa;
- banco;
- migration;
- SQL;
- Fase 1D iniciada.

### Fase 1D: migrations

Objetivo:

- criar migrations iniciais revisadas;
- executar migrations de forma reproduzível;
- validar rollback operacional por backup, não por edição manual do banco.

Entrada obrigatória:

- desenho da Fase 1C revisado e aprovado.
- preparação da Fase 1C.1 revisada, incluindo plano Flyway, ordem planejada e decisões pré-1D.

Ferramenta definida:

- Flyway com Spring Boot e PostgreSQL;
- PostgreSQL;
- migrations SQL explícitas;
- revisão por Git;
- execução reproduzível por ambiente.
- migrations imutáveis depois de aplicadas fora de ambiente local descartável;
- nenhuma migration automática por ORM;
- nenhum executor próprio;
- nenhuma alteração manual de schema em produção;
- nenhuma execução no startup operacional comum da aplicação.

## Gates mínimos

- ambiente local sobe sem depender de produção;
- secrets separados por ambiente;
- dumps, backups, uploads e logs fora do Git;
- staging autenticado e `noindex`;
- produção promovida apenas por artefato reproduzível;
- decisão ADR-010 aprovada antes de qualquer promoção para VPS.
