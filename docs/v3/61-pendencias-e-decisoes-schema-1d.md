# Pendências e decisões do schema na Fase 1D

## Status

Status da entrega: `OK PARA AUDITORIA, AGUARDANDO_REVISAO_PRO`.

Este documento registra decisões conservadoras tomadas para materializar as migrations de auditoria. Essas decisões não significam aprovação para aplicação em banco.

## Decisões materializadas para auditoria

### Schema PostgreSQL

Decisão de auditoria: usar `public` único no baseline inicial.

Motivo: reduzir complexidade da primeira revisão física e manter aderência aos documentos pré-1D. A separação por schemas pode voltar à pauta antes de qualquer execução fora de banco descartável.

### UUID

Decisão de auditoria: armazenar chaves técnicas como `uuid` e manter UUID v7 gerado pela aplicação.

Motivo: a Fase 1D não cria backend funcional e não escolhe biblioteca Java concreta. Nenhuma extensão de UUID foi habilitada.

### Extensões de busca

Decisão de auditoria: materializar `pg_trgm` e `unaccent` em `V001` como candidatas para busca.

Motivo: os documentos de banco e busca já apontavam essas extensões como candidatas. A aplicação real depende de revisão Pro de custo, disponibilidade e necessidade antes de executar Flyway.

### CHECK versus catálogo

Decisão de auditoria: usar `CHECK` para estados técnicos pequenos e estáveis nesta primeira versão.

Motivo: a Fase 1D precisa produzir um schema revisável sem criar módulos administrativos funcionais. Catálogos comerciais ou administráveis devem ser reavaliados antes de implementação de backend/admin.

### Rotas e SEO

Decisão de auditoria: armazenar caminhos canônicos como path relativo, sem domínio de produção.

Motivo: canonical final depende de ambiente futuro. Rotas alternativas de anúncio permanecem proibidas. A Fase 1D.2 liberou apenas os caminhos controlados `/sitemap.xml` e `/robots.txt` nas constraints de SEO.

### Slug público

Decisão de auditoria: `anuncio.slug` é globalmente único.

Motivo: evitar reutilização indevida de slug público histórico e reduzir risco de perda SEO. Uma tabela de reserva/histórico pode ser avaliada antes do importador, mas não foi criada nesta fase.

### Mídia

Decisão de auditoria: armazenar referência abstrata de storage por chave, bucket e metadados técnicos, sem URL pública real.

Motivo: URL pública não é fonte de verdade de mídia. Stories referenciam `anuncio_midia`.

### Documentos privados de usuário

Decisão de auditoria: documento privado de usuário nunca é publicável, usa `retencao_ate` nullable e possui política de retenção explícita.

Motivo: o documento pode ser mantido enquanto houver anúncio vinculado, possibilidade operacional de reativação, finalidade operacional legítima ou obrigação jurídica. A Fase 1D não cria expurgo automático. A decisão jurídica final de retenção fica para fase futura.

### Premium e gratuito

Decisão de auditoria: modelar Premium como benefícios aditivos e auditáveis.

Motivo: preservar a regra atual e evitar paywall agressivo. Não foi criado limite diário de cliques, contatos ou WhatsApp para plano gratuito.

### Ativação Premium em grupo

Decisão de auditoria: criar `grupo_ativacao_beneficio` para pacote, campanha, cortesia, admin e importação.

Motivo: `ativacao_beneficio.grupo_ativacao_id` precisava de tabela própria para permitir múltiplas ativações vinculadas sem regra funcional nesta fase.

### Métricas

Decisão de auditoria: criar base para visualizações, cliques WhatsApp, agregados diários e verificação etária. Agregados usam `id` como PK e chaves normalizadas para origem desconhecida.

Motivo: métricas existentes em produção devem ser preservadas, migradas, auditadas ou reimplementadas com equivalência funcional. IP e user-agent entram somente como hash quando necessários.

### Financeiro e legado

Decisão de auditoria: Efí usa método Pix, Mercado Pago legado não representa fluxo ativo e eventos financeiros são idempotentes por provedor.

Motivo: evitar colisão entre provedores e impedir que tabela ou nomenclatura legada defina provedor sem evidência.

### Importação

Decisão de auditoria: staging separado por prefixo `stg_` e controle explícito de execução, com tabelas declaradas explicitamente.

Motivo: o importador não foi criado. Tabelas staging não promovem dados automaticamente e a Fase 1D.2 removeu `LIKE INCLUDING ALL` para facilitar auditoria.

### Validação PostgreSQL descartável

Decisão de auditoria: criar validador local para executar Flyway apenas contra PostgreSQL descartável, sem volume persistente e sem download de ferramentas ou imagens.

Motivo: a Fase 1D.3 precisa preparar a validação real de sintaxe/ordem/FK em PostgreSQL, mas não pode instalar dependências nem puxar imagens. Nesta máquina o resultado ficou `PENDENTE_VALIDACAO_POSTGRES_LOCAL` porque o Docker daemon estava indisponível e Flyway CLI não foi encontrado.

Atualização 1D.4: Docker Desktop local foi iniciado pelo script, a imagem local `postgres:16` estava disponível e as migrations `V001` a `V017` foram aplicadas em ordem em PostgreSQL descartável pelo fallback `SQL_ORDENADO_PSQL`. Nenhuma migration precisou de correção. Flyway real permanece pendente apenas como ferramenta de validação futura, porque CLI/imagem Flyway local não estavam disponíveis sem instalação ou download.

Atualização 1D.5: o pacote final de auditoria foi consolidado sem criar migration nova e sem alterar SQL de schema. O validador PostgreSQL passou a retornar `0` somente para `OK_POSTGRES_DESCARTAVEL`, `1` para falha real de migration/SQL e `2` para pendência operacional.

### Sanitização de auditoria

Decisão de auditoria: `auditoria_evento.antes_json` e `auditoria_evento.depois_json` permanecem como campos estruturais, acompanhados de hashes, mas dependem de sanitizer futuro da aplicação.

Motivo: migration não consegue garantir sozinha a remoção contextual de dados sensíveis em snapshots JSON. A política de sanitizer, minimização e retenção deve ser definida e implementada no backend futuro antes de qualquer uso real.

## Pendências para auditoria Pro

- Aprovar ou remover `pg_trgm` e `unaccent` do baseline.
- Confirmar `public` único versus schemas separados.
- Revisar enum por enum para confirmar `CHECK` ou catálogo.
- Validar custo e necessidade dos índices de busca.
- Validar estratégia definitiva de reserva/histórico de slug antes de importação.
- Validar modelagem financeira, idempotência, txid, webhook e conciliação.
- Validar se o ledger de créditos cobre cortesias, campanhas, migração, ajustes, estorno e expiração.
- Validar retenção/minimização de eventos, métricas, IP e user-agent.
- Validar compatibilidade dos agregados de métricas com origem desconhecida.
- Validar decisão jurídica final de retenção de documentos privados.
- Validar equivalência das métricas atuais antes de iniciar importação.
- Validar estratégia definitiva de storage antes de upload/importação de mídia.
- Validar o impacto de constraints finais na futura importação.
- Executar `flyway migrate/validate` real quando Flyway local estiver disponível sem instalação ou download.
- Definir e validar sanitizer da aplicação para `auditoria_evento.antes_json` e `auditoria_evento.depois_json`.
- Aprovar explicitamente o schema antes de iniciar importador, backend de domínio, entidades JPA, repositories, services, controllers ou Fase 2.
