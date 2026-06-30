# Visão geral da V3

## Objetivo

A V3 do Tops do Job deve ser uma plataforma nova ou quase nova, construída por Specification-Driven Development, preservando dados, SEO, URLs públicas, slugs e operação comercial sem herdar a dívida técnica do legado.

A meta não é trocar telas de forma superficial. A meta é reconstruir as fontes de verdade: dados, mídia, créditos, premium, SEO, permissões, importação, backup e rollback.

## Base deste diagnóstico

A varredura local encontrou o workspace `C:\topsv3` vazio e sem repositório Git. Portanto, não ha código fonte local para compilar, testar ou inspecionar diretamente nesta etapa.

Foram usados como insumo os artefatos do ZIP `auditoria_topsdojob_v3_entregaveis.zip`:

- `relatorio_auditoria_topsdojob_v3.md`
- `plano_v3_revisado.md`
- `inventario_endpoints_legado.csv`
- `matriz_reaproveitamento_v3.csv`
- `inventario_configuracoes_legado.csv`

Segundo a auditoria, o legado analisado em pacote anterior continha backend Spring Boot/Java, frontend Next.js/TypeScript, PostgreSQL, integrações de mídia, Pix, SEO e painel administrativo. A auditoria também registrou riscos críticos de credenciais, dados pessoais, migrations, mídia, autenticação, FAQ público, canonical divergente e backup insuficiente.

Correção de escopo financeiro:

- a integração Pix atual usa Efí Bank/Efí Pay e deve permanecer na V3 como integração ativa a preservar;
- qualquer fluxo Mercado Pago é legado descartado, sem nova dependência na V3;
- Supabase é legado de importação/leitura temporária, não fonte permanente;
- a preservação da Efí não autoriza cópia cega da implementação legada: o desenho V3 deve reescrever a integração com provider, idempotência, auditoria, segurança e testes.

## Diretriz técnica

A recomendação e manter a stack principal:

- Backend: Java Spring Boot.
- Banco: PostgreSQL.
- Frontend: Next.js.
- Storage de mídia: compatibilidade com o storage atual, com fonte canônica documentada.
- Idioma: português do Brasil em painel, operação, mensagens e documentação funcional sempre que possível.

A forma recomendada é um monolito modular no backend, com fronteiras de domínio claras, banco V3 limpo e frontend separado por contratos tipados. Não há justificativa técnica nesta etapa para microserviços.

## Princípios de preservação

1. Dados financeiros, usuários, anúncios, slugs, mídias e históricos nunca devem ser ajustados silenciosamente.
2. URLs públicas atuais devem continuar funcionando.
3. O domínio canônico deve ser `https://topsdojob.com`, sem `www`.
4. Slugs de anúncios devem ser preservados sempre que possível.
5. SEO deve ser tratado como fundação, não como acabamento.
6. A operação comercial deve continuar no legado até a V3 passar pelos gates.
7. Rollback real deve existir antes da virada.
8. Backup e restauração devem ser testados, não apenas documentados.
9. Regra nova e legado não devem dividir a mesma fonte de verdade ambígua.
10. Banners devem ter dimensões fixas, mas conteúdo editável no admin.

## Riscos do legado identificados na auditoria

- Migrations históricas com executor próprio, ordenação textual e versões duplicadas.
- Seeders de startup alterando dados e criando contas com credenciais fixas.
- Arquivos sensíveis, certificados, logs, banco local e exportação de usuários dentro de artefatos.
- Autenticação baseada em JWT/cookie com CSRF desabilitado e sessões pouco revogáveis.
- Fluxos de 2FA e recuperação com lacunas de segurança.
- FAQ com escrita pública conforme inventário da auditoria.
- Múltiplas fontes concorrentes para fotos, vídeos, documentos, revisões e stories.
- Créditos baseados em saldo mutável, sem razão contábil completa.
- Premium com benefícios uteis, mas ativações sem origem, ator e revogação suficientemente auditáveis.
- Canonical, robots, sitemap e IndexNow divergindo entre `www` e sem `www`.
- Backup pontual documentado, mas sem módulo, política, retenção, checksum, alerta e teste de restauração.

## Estratégia de construção paralela

A V3 deve ser construída em paralelo ao legado, com isolamento operacional:

1. Legado continua atendendo clientes.
2. V3 nasce com banco, configuração e ambiente separados.
3. Importador saneador executa ensaios repetíveis contra snapshots.
4. SEO e URLs são validados por crawl comparativo.
5. Mídia e storage são reconciliados por manifesto.
6. Créditos, pagamentos e premium exigem tolerância zero para divergência.
7. V3 fica bloqueada/noindex durante homologação.
8. Cutover ocorre apenas após backup final, delta final, reconciliação e smoke tests.
9. Rollback deve ter sido ensaiado antes da virada.
10. Legado permanece congelado e disponível por janela de segurança depois da virada.

## O que será reaproveitado

- Stack Java Spring Boot, PostgreSQL, Next.js e TypeScript.
- Conceitos e telas maduras de moderação, dashboard, suporte, chat, blog, conteúdo institucional e painel.
- Regras válidas de localidades, páginas públicas, slugs e SEO local.
- Integrações com R2, Pix Efí, e-mail e release por symlink, após hardening.
- Catálogo de benefícios premium, com versionamento e auditoria.

## O que será reescrito ou descartado

- Executor próprio de migrations.
- Seeders de produção e qualquer credencial fixa.
- Dados pessoais e certificados empacotados em código ou artefatos.
- Modelo concorrente de mídias.
- Núcleo contábil de créditos baseado apenas em saldo mutável.
- Sessões, 2FA, recuperação de senha e matriz de permissões.
- Orquestração de sitemap, robots, canonical e IndexNow com fontes duplicadas.
- Fallbacks de busca que carreguem tudo em memória.
- Fluxos Mercado Pago como caminho ativo de pagamento.

## Definição de pronto desta fase

Esta Fase 0 será considerada completa quando:

- Os documentos SDD em `docs/v3` existirem e cobrirem preservação, arquitetura, dados, SEO, importação, mídia, premium, segurança, backup, aceite e fases.
- Riscos do legado estiverem registrados sem exposição de valores sensíveis.
- Dúvidas técnicas bloqueantes estiverem listadas.
- Nenhum código, migration, banco, segredo ou arquivo de produção tiver sido alterado.
