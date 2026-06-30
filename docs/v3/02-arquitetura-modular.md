# Arquitetura modular da V3

## Decisão arquitetural

A V3 deve ser um monolito modular em Spring Boot, com PostgreSQL como banco relacional principal e Next.js como frontend público/admin. A modularidade deve ocorrer por domínio, contrato, permissão, evento e fronteira transacional.

Microserviços não são recomendados nesta etapa, porque aumentariam complexidade operacional sem resolver os principais riscos: dados, SEO, mídia, créditos, premium, segurança, importação e rollback.

## Princípios de módulo

Cada módulo deve declarar:

- fonte de verdade;
- comandos que alteram estado;
- consultas públicas e administrativas;
- eventos produzidos;
- dependências permitidas;
- permissões necessárias;
- dados auditáveis;
- política de retenção;
- critérios de aceite.

## Módulos

### Usuários e autenticação

Responsável por cadastro, login, sessões, tokens por e-mail, MFA, recuperação de acesso, perfis e vínculos do usuário com anúncios.

Fonte de verdade:

- `usuario`
- `sessao_usuario`
- `token_seguranca`
- `papel_usuario`

Decisão: reescrever segurança, sessões, 2FA e recuperação; reaproveitar apenas conceitos válidos de cadastro e verificação.

### Anúncios

Responsável por ciclo de vida do anúncio, dados comerciais, status, publicação, edição, vínculo com usuário e regras de exibição.

Fonte de verdade:

- `anuncio`
- `anuncio_status_historico`
- `revisao_anuncio`

Decisão: recriar o modelo limpo e importar dados saneados, preservando slugs.

### Busca e listagem pública

Responsável por listagens, filtros por localidade, categoria, atributos, texto e ranking.

Fonte de verdade:

- `documento_busca_anuncio`
- Índices PostgreSQL FTS/trigram

Decisão: adaptar regras uteis do legado, sem fallback `findAll()` em produção. O ranking deve ser explicável internamente.

### Página pública do anúncio

Responsável por `/anuncios/[slug]`, metadados, mídia pública, CTA de WhatsApp, dados estruturados e eventos de visualização.

Fonte de verdade:

- `anuncio`
- `anuncio_midia`
- `seo_url`
- `evento_visualizacao`
- `clique_whatsapp`

Decisão: preservar URL e slug; redesenhar contrato de dados.

### Mídia

Responsável por arquivos, fotos, vídeos, capa, documentos privados, variantes, verificação de storage e entrega segura.

Fonte de verdade:

- `arquivo_midia`
- `anuncio_midia`
- `documento_usuario`
- `documento_usuario_acesso`

Decisão: reescrever modelo canônico; usar compatibilidade com storage atual apenas como adaptador. Documento privado não é mídia publicável e seu acesso deve ser auditável.

### Stories

Responsável por stories publicáveis vinculados ao anúncio, com validade, moderação e mídia canônica.

Fonte de verdade:

- `story_anuncio`
- `anuncio_midia`

Decisão: stories não podem ser entidade solta com URL isolada. O vínculo canônico do story é `story_anuncio.anuncio_midia_id`; anúncio e arquivo são obtidos por `anuncio_midia`.

Regras:

- `story_anuncio.anuncio_midia_id` é obrigatório e único;
- `anuncio_midia.tipo` deve ser `STORY`;
- `anuncio_midia.finalidade` deve ser `STORY`;
- não pode existir story sem vínculo canônico;
- não pode haver duas fontes concorrentes para anúncio/arquivo do story.

### Moderação

Responsável por fila, revisões, decisões, justificativas, documentos, mídias pendentes, classificação e trilha de aprovação/rejeição.

Fonte de verdade:

- `revisao_anuncio`
- `anuncio_midia_revisao`
- `decisao_moderacao`

Decisão: reaproveitar fluxo e experiência administrativa do legado com contratos V3.

Nomenclatura oficial:

- `revisao_anuncio`: solicitação/revisão principal de anúncio;
- `anuncio_midia_revisao`: mídias propostas ou alteradas dentro da revisão;
- `decisao_moderacao`: decisão administrativa.

### Premium

Responsável por catálogo, benefícios, durações, ativações, revogações e efeitos no ranking.

Fonte de verdade:

- `beneficio_premium`
- `beneficio_premium_opcao`
- `ativacao_beneficio`

Decisão: reaproveitar catálogo como conhecimento, mas registrar ativações de forma imutável e auditável.

### Créditos

Responsável por razão de créditos, saldo projetado, ajustes, consumo por benefícios e conciliação.

Fonte de verdade:

- `movimento_credito`
- `saldo_credito_usuario` como projeção

Decisão: reescrever núcleo contábil. Saldo mutável isolado não é aceitável.

### Financeiro

Responsável por planos, checkout, pagamentos, webhooks, conciliação, reembolso/estorno e relatórios financeiros.

Fonte de verdade:

- `pagamento`
- `pagamento_evento`
- `pagamento_webhook`
- `pagamento_conciliacao`
- `movimento_credito`

Decisão: manter Efí Bank/Efí Pay como integração Pix ativa inicial da V3, descartando Mercado Pago como caminho ativo. A V3 deve reescrever a integração por contrato de provedor, sem copiar cegamente o legado.

Classificações:

- `EFI_PIX`: integração ativa a preservar.
- `MERCADO_PAGO`: legado descartado.
- `SUPABASE`: legado de importação/leitura temporária somente.

Contrato interno recomendado:

- `PagamentoProvider`
- `criarCobrancaPix(...)`
- `consultarCobranca(...)`
- `processarNotificacao(...)`
- `cancelarCobranca(...)`
- `consultarIntegracao(...)`

Implementação inicial:

- `EfiPixProvider implements PagamentoProvider`

Regras:

- entidades centrais não devem carregar nome específico de fornecedor;
- não usar `pagamentos_mp`, `PagamentoMP`, `mp_payment_id` ou nomes herdados do Mercado Pago no modelo V3;
- checkout Pix de compra de créditos deve continuar existindo;
- criação de cobrança, QR Code, Pix cópia e cola, consulta de status, webhook Efí e conciliação com créditos são obrigatórios;
- ambientes devem ser separados em `LOCAL_MOCK`, `HOMOLOGACAO` e `PRODUCAO`;
- o modo local deve simular cobrança, QR Code/cópia e cola fictícios, pendência, confirmação controlada, webhook simulado e idempotência;
- a aprovação do pagamento deve gerar exatamente uma entrada na razão de créditos;
- o saldo projetado nunca deve ser usado como prova de pagamento;
- pagamento já concluído não deve ser modificado por webhook ou consulta duplicada, salvo transição formal de estorno/cancelamento auditada.

Fluxo obrigatório:

1. usuário seleciona plano de créditos;
2. backend cria registro local de pagamento;
3. backend gera `txid` único;
4. adapter Efí cria a cobrança;
5. sistema recebe QR Code e Pix cópia e cola;
6. pagamento permanece pendente;
7. confirmação ocorre por webhook ou consulta ativa;
8. sistema registra evento recebido;
9. sistema consulta/confirma estado real quando necessário;
10. sistema aprova pagamento de forma idempotente;
11. sistema cria uma única entrada na razão de créditos;
12. sistema atualiza projeção de saldo;
13. sistema registra auditoria;
14. frontend recebe estado atualizado.

### SEO local

Responsável por canonical, metadados, páginas por UF/cidade/bairro, conteúdo local, indexabilidade e redirects.

Fonte de verdade:

- `seo_url`
- `seo_redirect`
- `seo_metadado`
- `localidade`

Decisão: centralizar domínio sem `www` e eliminar fontes duplicadas.

### Sitemap

Responsável por gerar sitemap limpo e particionado a partir do registro de URLs indexáveis.

Fonte de verdade:

- `seo_url`

Decisão: não gerar sitemap por fan-out descoordenado entre varias APIs.

### Dashboard/admin

Responsável por operação administrativa: usuários, anúncios, moderação, conteúdo, premium, créditos, financeiro, SEO, banners, backup e auditoria.

Fonte de verdade: consome os módulos de domínio por permissões explícitas.

Decisão: reaproveitar telas e fluxos onde fizer sentido, refazendo contratos e RBAC.

### Comercial

Responsável por pipeline comercial, contatos, retornos, status de relacionamento, oportunidades e observações não destrutivas.

Fonte de verdade:

- `comercial_contato`
- `comercial_interacao`
- `comercial_status`

Decisão: criar papel COMERCIAL com permissões próprias.

### Suporte

Responsável por tickets, mensagens, anexos, vínculos com usuário/anúncio/pagamento/moderação e histórico.

Fonte de verdade:

- `ticket_suporte`
- `mensagem_suporte`

Decisão: reaproveitar conceito e integrar com auditoria e retenção.

### Auditoria

Responsável por registrar comandos críticos, ator, IP, user-agent, antes/depois quando aplicável, recurso afetado e resultado.

Fonte de verdade:

- `auditoria_evento`

Decisão: módulo transversal obrigatório.

### Backup

Responsável por políticas, execuções, artefatos, checksums, retenção, criptografia, alertas e testes de restauração.

Fonte de verdade:

- `backup_politica`
- `backup_execucao`
- `backup_artefato`
- `backup_teste_restauracao`

Decisão: novo módulo. Painel administra e acompanha; execução técnica deve ocorrer em job/worker isolado.

### Importador saneador

Responsável por extrair, normalizar, validar, carregar staging, importar V3, reconciliar e reportar pendências.

Fonte de verdade:

- `importacao_execucao`
- `importacao_mapeamento`
- `importacao_pendencia`
- tabelas de staging

Decisão: novo módulo de migração, idempotente e auditável, fora do startup normal da aplicação.

## Regras transversais

- Flyway é a ferramenta de migrations da V3.
- Migrations serão SQL explícitas, versionadas por Git e imutáveis depois de aplicadas fora de ambiente local descartável.
- Migrations não serão executadas automaticamente por ORM.
- Não haverá executor próprio de migrations.
- Não haverá alteração manual de schema em produção.
- Migrations não serão executadas no startup operacional comum da aplicação.
- Migration fora do boot comum da aplicação.
- Outbox transacional para e-mail, IndexNow, webhooks internos, processamento de mídia e eventos.
- Auditoria de comandos críticos.
- Soft delete quando houver obrigação historica.
- Idempotência em pagamentos, importações, webhooks e ativações.
- Observabilidade com logs estruturados, métricas, tracing e health checks.
- Configuração tipada e validada no boot.
- Nenhum segredo no repositório, build, frontend ou imagem.
- Nenhum seeder de produção alterando dados no startup.

## Dependências permitidas

Módulos de domínio podem depender de usuários, auditoria e configuração. Mídia, SEO, créditos, premium e financeiro devem expor serviços internos claros. Nenhum módulo deve consultar tabelas legadas diretamente fora do importador saneador.

## Anticorruption layer

Toda compatibilidade com legado deve ficar no importador ou em adaptadores temporários explicitamente marcados. Regras novas não devem chamar modelos legados como fonte de verdade.
