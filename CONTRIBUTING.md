# Contribuição

## Regras gerais

- Não commitar secrets, credenciais, dumps, backups, uploads, logs ou dados reais.
- Não copiar arquivos do legado para este repositório sem decisão formal.
- Não criar migrations sem revisão.
- Migrations usam Flyway, SQL explícito e versionamento por Git.
- Não usar migration automática por ORM, executor próprio ou alteração manual de schema em produção.
- Não alterar regras financeiras, autenticação, SEO ou backup sem revisão.
- Não usar produção como ambiente de desenvolvimento.
- Fase 1C é documentação e desenho físico-conceitual do banco; não cria migrations, SQL, schema executável, entidades JPA de domínio, repositories ou services.
- Fase 1C.1 é preparação da Fase 1D; pode documentar nomes planejados de migrations, mas não cria arquivos SQL.
- Fase 1C.2 documenta padrões transversais de API, logs, auditoria e OpenAPI; não implementa regra de negócio.
- Fase 1C.3 implementa somente infraestrutura transversal mínima local em backend/frontend; não cria banco, migration, SQL, entidade JPA de domínio, repository, service de negócio, autenticação real, Pix/Efí ou integração externa.
- Fase 1C.4 implementa somente skeleton local das rotas públicas preservadas e SEO local seguro; não cria busca real, anúncio real, conteúdo adulto real, canonical de produção local, API externa, banco, migration ou SQL.
- Fase 1C.5 documenta GEO/AEO/LLM Visibility e cria skeleton institucional neutro; não acessa IA externa, não cria integração OpenAI, scraping, spam, conteúdo explícito, anúncio real, dados reais, backend de domínio, banco, migration ou SQL.
- Fase 1C.6A permite apenas captura pública somente leitura de textos SEO atuais, com GET, rate limit e sanitização; não acessa admin, login, API privada, banco, VPS, Efí, IA externa, imagens, vídeos, dados sensíveis, HTML bruto completo, migration ou SQL.
- Fase 1C.6B cria apenas shell administrativo local e mapa estrutural; não cria autenticação funcional, RBAC real, ação administrativa, backend de domínio, dado real, integração externa, banco, migration ou SQL.
- Fase 1C.7 documenta preservação do visual atual; não cria redesign, nova identidade visual, nova paleta, nova tipografia, layout final, dado real, conteúdo explícito, banco, migration ou SQL.
- Fase 1C.8 cria validação local estática de rotas públicas e SEO skeleton; não executa crawler real, produção, banco, API externa, build com download, migration ou SQL.
- Fase 1D gera migrations Flyway/PostgreSQL apenas para auditoria, com status `AGUARDANDO_REVISAO_PRO`; não aplicar Flyway, não acessar banco, não iniciar fase dependente e não tratar o schema como aprovado antes da revisão Pro.
- Fase 1D.3 prepara validação Flyway/PostgreSQL apenas em banco local descartável; se Docker daemon, imagem PostgreSQL ou Flyway não existirem localmente, registrar `PENDENTE_VALIDACAO_POSTGRES_LOCAL` sem instalar, baixar, puxar imagem ou acessar ambiente externo.
- Fase 1D.4 permite iniciar Docker Desktop local já instalado e validar migrations em PostgreSQL descartável. Se Flyway não existir localmente, pode ser usado fallback `SQL_ORDENADO_PSQL`; isso não aprova o schema e não autoriza banco persistente, importador, backend de domínio ou fase dependente.
- Fase 1D.5 fecha o pacote final de auditoria das migrations; não cria migration nova, não altera SQL de schema e mantém o status `AGUARDANDO_REVISAO_PRO`.
- Fase 2A cria apenas estrutura local do importador saneador: Java puro, DTOs/records, enums e builder em memória. Não ler dump, não acessar banco, não importar dados, não criar migration, não alterar SQL, não criar entidade JPA, repository, controller ou endpoint funcional.
- Fase 2B cria apenas contratos locais do pacote de entrada da importação: Java puro, DTOs/records, enums, validador estrutural em memória e exemplo sanitizado. Não ler dump, não abrir arquivo real de entrada, não acessar banco, não importar dados, não executar ETL, não criar migration, não alterar SQL, não criar entidade JPA, repository, controller ou endpoint funcional.
- Fase 2C cria apenas dicionário estrutural de campos do pacote de importação: Java puro, enums, DTOs/records, catálogo e validador em memória. Não ler dump, não abrir arquivo real de entrada, não acessar banco, não importar dados, não executar ETL, não criar migration, não alterar SQL, não criar entidade JPA, repository, controller ou endpoint funcional.
- Fase 2D cria apenas regras estruturais de saneamento e transformação legado -> V3: Java puro, enums, DTOs/records, catálogo e validador em memória. Não ler dump, não abrir arquivo real de entrada, não acessar banco, não transformar dados, não importar dados, não executar ETL, não criar migration, não alterar SQL, não criar entidade JPA, repository, controller ou endpoint funcional.
- Fase 2E cria apenas plano de execucao e dry-run estrutural da importacao: Java puro, enums, DTOs/records, catalogo de etapas e validador em memoria. Nao ler dump, nao abrir arquivo real de entrada, nao acessar banco, nao transformar dados, nao importar dados, nao executar ETL, nao criar migration, nao alterar SQL, nao criar entidade JPA, repository, service, controller ou endpoint funcional.
- Fase 2F cria apenas gate operacional de fonte real autorizada e protecao contra vazamento. Nao usar dump, nao usar dado real, nao abrir arquivo real de entrada, nao acessar banco, nao importar dados, nao executar ETL, nao criar migration, nao alterar SQL, nao criar entidade JPA, repository, service, controller ou endpoint funcional.
- Fase 2G cria apenas dossie documental de transicao para revisao Pro e fonte real futura. Nao criar nova camada de importador, nao criar codigo Java, nao criar script, nao usar dump, nao usar dado real, nao abrir arquivo real de entrada, nao acessar banco, nao importar dados, nao executar ETL, nao criar migration, nao alterar SQL.

## Antes de gerar pacote ou commitar localmente

Execute:

```powershell
.\scripts\security\verificar-codificacao.ps1
.\scripts\security\verificar-arquivos-proibidos.ps1
.\scripts\security\verificar-segredos.ps1
.\scripts\local\validar-migrations-sql-estatico.ps1
.\scripts\local\validar-migrations-postgres-descartavel.ps1
git diff --cached --check
```

O hook de pre-commit executa os scripts automaticamente para arquivos staged.

Commit local é opcional nas fases locais e não deve ser executado quando a fase solicitar apenas staging e ZIP de revisão. A falta de `user.name` ou `user.email` local não bloqueia o avanço quando os ZIPs validados, relatórios e índice staged estiverem aprovados. Remote, push, publicação ou deploy só podem ocorrer com autorização explícita futura.

Os testes dos hooks cobrem os scripts isolados e o hook real em repositório temporário, inclusive execução a partir de subdiretório, ordem `codificação -> arquivos proibidos -> secrets`, propagação de exit code e simulação de PowerShell ausente.

Os scripts distinguem achado de segurança de erro operacional:

- `0`: validação limpa;
- `1`: risco encontrado;
- `2`: falha operacional, como erro de Git, leitura de blob ou scanner indisponível.

O scanner de segredos sempre executa o fallback local. Se `gitleaks` estiver disponível, ele roda como camada adicional; se não estiver, o resultado de Gitleaks fica `PENDENTE`, sem substituir a validação local.

Enquanto houver suporte ao Windows PowerShell 5.1, scripts `.ps1` devem ser salvos em UTF-8 com BOM. Documentos Markdown, shell scripts, TOML, JSON e YAML devem ficar em UTF-8 sem BOM. CSV de relatório destinado ao Windows deve usar UTF-8 com BOM.

Arquivos textuais em UTF-16, UTF-32, com byte NUL ou controles inválidos são bloqueados. A heurística de `?` corrompido é aplicada apenas a texto natural, para evitar falso positivo em URL, query string e código.

## Documentação

Mudanças de arquitetura devem atualizar os documentos em `docs/v3` e, quando aplicável, registrar ADR.

Contratos de API futuros devem seguir os padrões em `docs/v3/32-padroes-api-erros-paginacao.md` e `docs/v3/33-padroes-logs-auditoria-observabilidade.md`. Entidades JPA não devem ser expostas como DTO público.

A camada transversal implementada na Fase 1C.3 fica restrita a pacotes `platform/*`, health local e cliente API local do frontend. Novos endpoints de domínio, autenticação funcional, services de negócio, repositories e entidades JPA continuam bloqueados até fases específicas.

O skeleton público da Fase 1C.4 deve permanecer neutro: páginas públicas locais podem validar rotas e metadados `noindex`, mas não podem carregar dados reais, fotos reais, listagens reais, JSON-LD final, busca real, backend de domínio ou canonical de produção em ambiente local.

`/anuncios/[slug]` é o contrato público absoluto da página de anúncio. Rotas alternativas como `/anuncio/[id]`, `/perfil/[slug]`, `/acompanhante/[slug]` e `/ads/[slug]` são proibidas. Antes de alterar rotas públicas ou SEO local, execute `scripts/local/validar-rotas-publicas-seo-local.ps1`.

Antes de revisar ou alterar migrations da Fase 1D, execute `scripts/local/validar-migrations-sql-estatico.ps1`. Esse script é textual, não acessa banco e não substitui revisão Pro, validação PostgreSQL descartável ou análise manual de arquitetura.

Quando a fase autorizar validação com banco descartável, use `scripts/local/validar-migrations-postgres-descartavel.ps1`. O script só pode usar Docker/Flyway/imagens já disponíveis localmente; não deve instalar ferramentas, baixar dependências, executar `docker pull`, criar volume persistente, acessar produção ou aplicar migrations em banco persistente.

Na Fase 1D.4, a validação real foi executada com imagem local `postgres:16` e método `SQL_ORDENADO_PSQL`, porque Flyway CLI/imagem Flyway não estavam disponíveis sem instalação. Qualquer execução futura com Flyway real continua restrita a banco local descartável.

O validador PostgreSQL descartável deve retornar `0` apenas para `OK_POSTGRES_DESCARTAVEL`, `1` para falha real de migration/SQL e `2` para pendência operacional. Retorno `2` não é aprovação; é bloqueio ambiental que deve ser resolvido ou registrado.

No módulo `br.com.topsdojob.v3.importacao`, classes das Fases 2A, 2B, 2C, 2D e 2E devem permanecer sem anotações JPA/Spring de domínio. Os subpacotes `importacao.pacote`, `importacao.dicionario`, `importacao.saneamento` e `importacao.plano` não podem receber I/O real, filesystem, datasource, repository, storage client, HTTP client ou leitura de dump. Testes podem ser criados, mas só devem ser executados quando houver executor local disponível sem download de dependências.

O script `scripts/local/validar-fonte-importacao-local.ps1` e gate preventivo da Fase 2F. Sem parametro, ele apenas confirma que nenhuma fonte real foi validada. Com diretorio em fase futura autorizada, ele deve verificar somente metadados de caminho/listagem, bloquear pasta dentro do workspace/repositorio e nunca abrir conteudo de arquivo real.

O dossie da Fase 2G e documental. Ele nao substitui revisao Pro, nao aprova schema, nao autoriza fonte real e nao desbloqueia backend de dominio.

GEO/AEO/LLM Visibility é transversal, mas não autoriza automação de conteúdo público. Páginas institucionais, FAQ, schema.org, `llms.txt` e textos úteis para IA devem ser neutros, revisados por humano e compatíveis com SEO tradicional, privacidade, segurança e compliance.

Capturas de produção, quando expressamente autorizadas, devem ser públicas, somente leitura, limitadas por rate, sanitizadas e armazenadas sem HTML bruto completo, imagens, vídeos, telefones, WhatsApp, documentos ou dados privados.

Páginas admin skeleton devem permanecer `noindex`, sem dados reais, sem botões de ação real e sem chamadas a backend. Ações críticas futuras exigirão autenticação, autorização por perfil e auditoria.

Mudanças visuais devem preservar o visual atual do Tops do Job. Skeletons locais não são referência de layout final; qualquer nova paleta, tipografia, redesign de cards, redesign da home ou mudança estrutural de UX exige aprovação expressa e fonte visual atual inventariada.

## Pacotes de revisão

Toda execução futura do Codex deve gerar um ZIP na Área de Trabalho com apenas os arquivos criados ou modificados na execução, preservando estrutura relativa, manifesto SHA-256, resumo da entrega e relatório de validações. O ZIP não deve entrar no Git.

A seleção do pacote deve comparar o hash do índice Git do inventário inicial com o hash final do índice. O inventário deve ser criado fora do repositório com `scripts/entrega/criar-inventario-inicial.ps1`, em UTF-8 com BOM, e validado pelo gerador antes do uso. O script de pacote deve bloquear qualquer alteração fora do staging, reabrir o ZIP, validar entradas, manifesto, hashes, codificação e ausência de arquivos proibidos.

O manifesto validado é sempre o CSV extraído do ZIP, não apenas os objetos em memória do script. `MANIFESTO-ARQUIVOS.csv`, `RESUMO-ENTREGA.md`, `RELATORIO-VALIDACOES.md` e relatórios de testes incluídos passam pelo scanner de secrets.

## Bloco 3 - backend de domínio base

O Bloco 3 pode criar apenas domínio Java puro espelhando as migrations auditadas. Enquanto `jakarta.persistence` e Spring Data JPA não estiverem aprovados localmente, é proibido criar entidades JPA anotadas, repositories, datasource de domínio, services de negócio, controllers de domínio ou endpoints funcionais.

Campos sensíveis devem permanecer como hashes, referências ou textos sanitizados. Documento privado não pode virar mídia pública. Auditoria deve usar snapshots sanitizados ou hashes. Pix Efi continua proibido fora de mock/local sem credencial real.
