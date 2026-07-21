# Auditoria cirúrgica de paridade do painel do anunciante

## 1. Escopo, baseline e método

- Auditoria exclusivamente documental e comportamental, executada sobre o `main` em `8ddff96eefd4c62211151ee8f9c6e2a9fb06b3e5`, igual a `origin/main` no início.
- Baseline canônico preservado: `AUDITORIA-PARIDADE-INTEGRAL.md`, `matriz-funcional-integral.json` e decisões consolidadas do SDD.
- Fontes confrontadas: produção real autenticada, código da produção em `C:\clone`, backend/frontend/OpenAPI V3, banco da produção somente leitura, PostgreSQL da pré-produção somente leitura e comportamento autenticado de `v3.esle.cloud`.
- Conta anunciante autorizada usada sem registrar e-mail, senha, telefone, cookie, token, CPF, documento ou identificador privado neste arquivo.
- Nenhuma compra, Pix, edição, exclusão, ativação Premium, moderação, alteração de crédito, importação ou operação R2 foi executada.
- Nenhuma função da produção ou função nova da V3 está autorizada para remoção; ausências e divergências permanecem registradas para implementação.
- Função sem backend não representa vazio legítimo: permanece classificada como contrato ausente, sem dado vazio fabricado.
- Limitação operacional relevante: ao inspecionar o botão de pausa na produção, um clique executou a mutação sem diálogo de confirmação. O mesmo anúncio foi imediatamente reativado; o estado final foi conferido como publicado, com `1` ativo e `0` pausado. O episódio prova o risco registrado em `S05`; não restou alteração funcional.

As classificações funcionais usadas na matriz são exclusivamente:

`EQUIVALENTE_E_FUNCIONAL`, `FUNCAO_NOVA_V3`, `AUSENTE_NA_V3`, `DIVERGENTE_DA_PRODUCAO`, `FUNCAO_APENAS_VISUAL`, `CONTRATO_BACKEND_AUSENTE`, `CONTRATO_FRONTEND_INCOMPATIVEL`, `FALSO_VAZIO`, `FALLBACK_HARDCODED`, `QUEBRADO`, `SEGURANCA_INCORRETA` e `REQUER_DECISAO_DO_PROPRIETARIO`.

## 2. Inventário da produção

### 2.1 Navegação principal do painel

| Ordem | Área | Rota | Comportamento comprovado |
|---:|---|---|---|
| 1 | Visão geral | `/painel` | Resumo comercial, score, indicadores, ranking e recomendações reais. |
| 2 | Meus anúncios | `/meus-anuncios` | Gestão operacional dos anúncios e acesso ao wizard. |
| 3 | Performance | `/painel/performance` | Métricas, comparação temporal, série de 14 dias e ranking. |
| 4 | Créditos e planos | `/creditos` | Saldo, pacotes, benefícios e início de checkout. |
| 5 | Recomendações | `/painel#recomendacoes` | Recomendações calculadas a partir dos dados da conta e dos anúncios. |
| 6 | Conta e configurações | `/minha-conta` | Perfil, localização, senha, 2FA legado, resumos e exclusão. |

O componente `PainelShell` da produção mantém os seis itens em desktop e mobile. Em edição e monetização, `Meus anúncios` permanece o contexto funcional.

### 2.2 Subrotas e áreas adicionais encontradas

- `/anunciar/wizard`: wizard canônico de criação.
- `/meus-anuncios/{slug}/editar`: o mesmo wizard em edição, com cinco etapas na produção.
- `/meus-anuncios/{slug}/monetizar`: wizard comercial de cinco etapas.
- `/checkout/creditos/{planoId}`: checkout interno de créditos/Pix.
- `/indicacoes`: link, total indicado e créditos de indicação.
- `/favoritos`: anúncios favoritados.
- `/chat`: conversas e busca de usuário.
- `/meus-tickets`: listagem, abertura, conversa e encerramento de suporte.
- Modal global de feedback/sugestão e botão flutuante de suporte.
- Submenu autenticado: Minha conta, Painel comercial, Meus anúncios, Créditos, Indicações, Favoritos, Chat, Meus suportes, Feedback, Administração condicional e Logout.

## 3. Inventário da V3

### 3.1 Navegação principal do painel

| Ordem | Área | Rota | Comportamento comprovado |
|---:|---|---|---|
| 1 | Visão geral | `/painel` | Sessão, perfil mínimo, saldo, ledger recente e benefícios ativos. |
| 2 | Meus anúncios | `/meus-anuncios` | Lista própria, capa, estado, moderação, detalhe e edição. |
| 3 | Conta e configurações | `/minha-conta` | Username, e-mail somente leitura e telefone. |

`Performance`, `Créditos e planos` e `Recomendações` não aparecem no `PainelShell` V3. A rota `/painel/performance` existe, mas não possui contrato backend. `/creditos` redireciona para `/painel`.

### 3.2 Subrotas e funções novas encontradas

- `/anunciar` e `/anunciar/wizard`: wizard único de criação.
- `/meus-anuncios/{slug}`: detalhe autenticado próprio, inexistente como rota dedicada na produção.
- `/meus-anuncios/{slug}/editar`: o mesmo `AnuncioWizard` em `mode="edit"`, com sete etapas.
- `/meus-anuncios/{slug}/monetizar`: wizard de monetização usando ledger e catálogo V3.
- `/indicacoes`: superfície herdada sem contrato, preenchida com zeros e link vazio.
- `/favoritos`: contrato V3 real e isolado por sessão.
- `/chat` e `/meus-tickets`: controles preservados, com ausência de contrato explicitada.
- KYC na etapa 7, gestão de mídias no R2, detalhe próprio, ledger imutável, benefícios atômicos e erros 403/404 explícitos são funções novas V3.

## 4. Catálogo de contratos e fontes

As referências abaixo tornam explícitos método, payload/resposta, DTO, serviço, persistência, autenticação, CSRF e OpenAPI usados pelas linhas da matriz.

Cada linha da matriz herda o contrato citado nesta seção. Leituras autenticadas usam `cache: no-store` ou estado cliente sem cache compartilhado; não foi encontrado cache servidor específico do painel. `GET` não envia payload. Mutações V3 usam DTO JSON ou multipart tipado, sessão pública e token CSRF; os estados relevantes são 200/204, 400/422, 401, 403, 404 e 409 conforme a operação. Erro de rede/5xx permanece erro técnico e não é convertido em vazio.

### 4.1 Produção

| Código | Contrato e comportamento | Fonte, segurança e persistência |
|---|---|---|
| `P-OV` | `GET /painel-anunciante/visao-geral`; resposta `AdvertiserDashboardOverviewResponseDTO`. | `PainelAnuncianteController/Service`; usuário vem do `SecurityContext`; anúncios, usuário, créditos, `feature_ativacao` e `clique_whatsapp`; `no-store` no cliente. CSRF da produção está globalmente desabilitado. |
| `P-PF` | `GET /painel-anunciante/performance`; resposta `AdvertiserDashboardPerformanceResponseDTO`. | Mesmo serviço; `anuncios.visualizacoes`, `clique_whatsapp`, ativações Premium; sessão por papel. |
| `P-MA` | `GET /anuncios/meus`; lista cards próprios. | ID do usuário extraído do JWT/cookie; `anuncios`, localização, mídia e features. A página transforma falha em `[]`. |
| `P-LC` | `PUT /anuncios/{id}/pausar`, `PUT /anuncios/{id}/postar`, `DELETE /anuncios/{id}`. | `AnuncioController/Service`; proprietário resolvido pelo token e validado pelo serviço; sem CSRF. Pausa/reativação não têm confirmação; exclusão tem modal. |
| `P-FT` | `GET /features/catalogo`, ativações e impulsionamento por anúncio. | Catálogo e `feature_ativacao`; erros do catálogo são silenciosamente ignorados na lista. |
| `P-ST` | `POST /stories` multipart. | Anúncio ativo, catálogo e crédito; corpo binário não foi enviado nesta auditoria. |
| `P-ED` | `GET/PUT /anuncios/meus/{slug}/editar`. | Wizard de produção; proprietário autenticado; anúncio/localização/serviços/mídias. |
| `P-CR` | `GET /creditos/planos`, `GET /creditos/resumo`, `POST /checkout/creditos/{planoId}` e consulta/cancelamento de pagamento. | Crédito e histórico legados, pagamentos nominalmente MP/Efí; checkout não executado. |
| `P-AC` | `PUT /usuarios/{email}/editar`, `PUT /usuarios/{email}/alterar-senha`, `DELETE /usuarios/{email}/excluir-conta`. | `UsuarioController/Service`; perfil e senha selecionam usuário pelo e-mail da rota. A edição e exclusão não confrontam esse e-mail com o principal autenticado; risco crítico de IDOR. |
| `P-2F` | `GET /2fa/status/{email}`, `POST /2fa/ativar`, `/verificar`, `/desativar`. | Serviço seleciona usuário pelo e-mail do request sem confrontar com o principal; legado intencionalmente não migrado. |
| `P-RF` | `/indicacoes/*`; resumo e configuração de indicação. | `IndicacaoController`, usuário e crédito legado. |
| `P-FV` | `GET /anuncios/favoritos` e ações de favorito. | Usuário extraído do JWT/cookie; relação usuário-anúncio. |
| `P-SU` | `/suporte/*`, `/ws-suporte`, tópicos de ticket/chat. | Suporte, mensagens e WebSocket; sessão legada. |
| `P-LC2` | `GET /localidades/estados`, cidades e bairros. | Taxonomia legada usada somente pela conta da produção. |

### 4.2 V3

| Código | Contrato e comportamento | Fonte, segurança e persistência |
|---|---|---|
| `V-AU` | `GET/PATCH /api/public/auth/me`, `POST /api/public/auth/logout`. | `PublicAuthController/Service`; `PublicUserPrincipal`, cookie público e CSRF; OpenAPI presente. Perfil aceita username e telefone, não e-mail. |
| `V-MA` | `GET /api/public/minha-conta/anuncios`, `GET/PATCH /{slug}`. | `MeusAnunciosController/ConsultaService`; proprietário exclusivamente da sessão; 401/403/404; `anuncio`, localização e mídia; OpenAPI presente. DTO não contém métricas, Premium, Story ou histórico. |
| `V-MM` | `GET/POST /api/public/minha-conta/anuncios/{slug}/midias`, limites, ordem e remoção lógica. | Propriedade por sessão, CSRF nas mutações, R2 único; pendente/restrita sem URL pública; OpenAPI presente. |
| `V-MO` | `GET /api/public/minha-conta/monetizacao`, `POST /compras`. | Ledger, catálogo, pacotes e benefícios; débito/ativação atômicos e idempotentes; sessão e CSRF; OpenAPI presente. |
| `V-KY` | `GET/POST /api/public/minha-conta/kyc`. | Sessão, validação de CPF/idade e documentos privados R2; OpenAPI presente. |
| `V-FV` | `GET/PUT/DELETE /api/public/minha-conta/favoritos/{slug?}`. | Usuário exclusivamente da sessão, operações idempotentes e CSRF; OpenAPI presente. |
| `V-PF` | Frontend chama `GET /api/public/painel-anunciante/performance`. | Não há controller, serviço nem path no OpenAPI. Retorna 404 e a tela mostra erro explícito. |
| `V-VW` | `POST /api/public/anuncios/{slug}/visualizacao`. | `MetricaPublicaService`; grava `evento_visualizacao` com hashes técnicos; OpenAPI presente. Não foi acionado deliberadamente na auditoria. |
| `V-WA` | `POST /api/public/anuncios/{slug}/clique-whatsapp`. | Política de contato e `clique_whatsapp`; OpenAPI presente. |
| `V-PU` | `GET /api/public/anuncios` e `GET /api/public/anuncios/{slug}`. | DTOs públicos seguros e OpenAPI presentes; nenhum deles transporta `visualizacoes`. |
| `V-NO` | `PENDING_BACKEND_CONTRACTS.notices`, suporte, chat, feedback e indicações. | Não há backend; `ContractState` deve informar ausência. O manager de avisos está montado globalmente em `PublicChrome`. |
| `V-EF` | `/api/public/minha-conta/pagamentos/*` e webhook Efí. | Estrutura presente, `EFI_ENABLED=false`; não há chamada externa ou checkout habilitado na pré-produção. |

## 5. Matriz funcional

Convenções: `P` = produção, `V3` = `v3.esle.cloud`; `OA` = OpenAPI ausente, `OP` = OpenAPI presente; `S` = sessão; `C` = CSRF exigido em mutação V3. Quando o endpoint é `—`, a função não tem contrato correspondente. As colunas de dados incluem campos, métricas e filtros; as colunas de comportamento incluem ações, carregamento, vazio, erro e confirmação. O campo mobile foi confrontado no layout responsivo e nos componentes compartilhados, sem executar mutações.

### 5.1 Navegação

| ID | Área / subárea / finalidade | Rotas P → V3 | Produção | V3 | Dados, contrato e fonte | Segurança, estados, mobile e evidência | Classificação | Correção / fase |
|---|---|---|---|---|---|---|---|---|
| N01 | Navegação / Visão geral | `/painel` → `/painel` | Primeiro item, ativo na raiz. | Primeiro item, ativo na raiz. | `P-OV` → `V-AU`,`V-MO`; dados diferentes, destino real. | S; responsivo; live + ambos `PainelShell`. | EQUIVALENTE_E_FUNCIONAL | Preservar; F1. |
| N02 | Navegação / Meus anúncios | `/meus-anuncios` → igual | Segundo item e gestão completa. | Segundo item e lista própria. | `P-MA` → `V-MA`; OA → OP. | S; desktop/mobile; live. | EQUIVALENTE_E_FUNCIONAL | Preservar; F1. |
| N03 | Navegação / Performance | `/painel/performance` → mesma rota | Terceiro item visível. | Rota existe, item não aparece. | `P-PF` → `V-PF`; backend V3 ausente. | S; omissão em desktop/mobile comprovada no array `NAV_ITEMS`. | AUSENTE_NA_V3 | Repor item sem mascarar contrato; F1/F2. |
| N04 | Navegação / Créditos e planos | `/creditos` → `/creditos` | Quarto item visível. | Item ausente; rota redireciona para `/painel`. | `P-CR` → `V-MO`,`V-EF`. | S; ausência em desktop/mobile. | AUSENTE_NA_V3 | Repor destino funcional; F3. |
| N05 | Navegação / Recomendações | `/painel#recomendacoes` → — | Quinto item leva ao bloco calculado. | Item e bloco inexistentes. | `P-OV` → —. | S; nenhum CTA equivalente. | AUSENTE_NA_V3 | Reproduzir após score; F4. |
| N06 | Navegação / Conta | `/minha-conta` → igual | Sexto item. | Terceiro item. | `P-AC` → `V-AU`. | S; responsivo; live. | EQUIVALENTE_E_FUNCIONAL | Preservar; F1. |
| N07 | Navegação / ordem canônica | Seis itens → três itens | Ordem comercial completa. | Ordem truncada. | Configuração local de shell. | Não depende de backend; divergência em desktop/mobile. | DIVERGENTE_DA_PRODUCAO | Restaurar seis destinos; F1. |
| N08 | Navegação / mobile | Barra rolável com seis → barra com três | Todas as áreas alcançáveis. | Só três áreas alcançáveis pelo shell. | Mesmo destino dos itens acima. | Scroll horizontal funciona, inventário incompleto. | DIVERGENTE_DA_PRODUCAO | Restaurar itens e testar 320/390 px; F1. |
| N09 | Navegação / estado ativo em subrotas | Edição/monetização → detalhe/edição | Contexto Meus anúncios. | `startsWith('/meus-anuncios/')` mantém ativo. | Sem endpoint. | Desktop/mobile; fonte `painel-shell.tsx`. | EQUIVALENTE_E_FUNCIONAL | Preservar; F1. |
| N10 | Header / Minha conta | `/minha-conta` → igual | Submenu autenticado. | Submenu preservado. | `P-AC` → `V-AU`. | S; desktop/mobile. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| N11 | Header / Painel comercial | `/painel` → igual | Submenu autenticado. | Submenu preservado. | `P-OV` → `V-AU`,`V-MO`. | S; desktop/mobile. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| N12 | Header / Meus anúncios | `/meus-anuncios` → igual | Submenu autenticado. | Submenu preservado. | `P-MA` → `V-MA`. | S; desktop/mobile. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| N13 | Header / Créditos | `/creditos` → `/creditos` | Abre catálogo e compra. | Redireciona ao painel sem catálogo de pacotes. | `P-CR` → `V-MO`; contratos V3 existem, UI de pacotes não. | S; destino não cumpre finalidade. | DIVERGENTE_DA_PRODUCAO | Repor tela; F3. |
| N14 | Header / Indicações | `/indicacoes` → igual | Resumo real. | Link preservado, tela sem contrato. | `P-RF` → `V-NO`. | S; visual responsivo, dados falsos. | CONTRATO_BACKEND_AUSENTE | Criar contrato canônico; F4. |
| N15 | Header / Favoritos | `/favoritos` → igual | Lista real. | Lista real V3. | `P-FV` → `V-FV`; OP. | S, C nas mutações; desktop/mobile. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| N16 | Header / Chat | `/chat` → igual | Conversas reais. | Controles preservados, sem backend. | `P-SU` → `V-NO`. | S; erro explícito, sem polling. | CONTRATO_BACKEND_AUSENTE | Implementar contrato; fase posterior. |
| N17 | Header / Meus suportes | `/meus-tickets` → igual | Tickets reais. | Controles preservados, sem backend. | `P-SU` → `V-NO`. | S; erro explícito. | CONTRATO_BACKEND_AUSENTE | Implementar contrato; fase posterior. |
| N18 | Header / Feedback | Modal → modal | Envia sugestão/bug. | Formulário visível, ação pendente. | Produção sugestão → `V-NO`. | S; não simula sucesso. | CONTRATO_BACKEND_AUSENTE | Criar contrato; fase posterior. |
| N19 | Header / Logout | Ação → ação | Encerra sessão. | `POST /api/public/auth/logout`. | Sessão legada → `V-AU`; OP. | C; loading e redirect; live aprovado. | EQUIVALENTE_E_FUNCIONAL | Preservar. |

### 5.2 Visão geral

| ID | Área / subárea / finalidade | Rotas P → V3 | Produção | V3 | Dados, contrato e fonte | Segurança, estados, mobile e evidência | Classificação | Correção / fase |
|---|---|---|---|---|---|---|---|---|
| O01 | Visão geral / saudação | `/painel` → igual | Nome e saudação. | Username e saudação. | `P-OV` → `V-AU`. | S; sem dado privado adicional; responsivo. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| O02 | Visão geral / localização do anunciante | `/painel` → igual | Cidade/UF no resumo. | Não exibida. | `usuarios`/localidade → —. | S; campo ausente. | AUSENTE_NA_V3 | Expor DTO próprio sanitizado; F4. |
| O03 | Visão geral / score individual | `/painel` → igual | Score `38/100` na conta auditada. | Não existe. | `P-OV`; cálculo do `PainelAnuncianteService` → —. | S; não é texto estático. | AUSENTE_NA_V3 | Implementar contrato e regra; F4. |
| O04 | Visão geral / número de fotos | `/painel` → igual | Contagem real por anúncios. | Não existe. | Mídias vinculadas → —. | S; não usar limite como contagem. | AUSENTE_NA_V3 | Agregar mídias próprias; F4. |
| O05 | Visão geral / quantidade Premium | `/painel` → igual | KPI numérico. | Lista benefícios ativos, sem KPI equivalente. | `feature_ativacao` → `beneficio_ativacao` via `V-MO`. | S; dado existe, apresentação diverge. | DIVERGENTE_DA_PRODUCAO | Manter lista e repor KPI; F4. |
| O06 | Visão geral / score médio | `/painel` → igual | Média dos anúncios, `38/100` na amostra. | Não existe. | `P-OV` → —. | S. | AUSENTE_NA_V3 | Agregar score por anúncio; F4. |
| O07 | Visão geral / anúncios ativos | `/painel` → igual | KPI real. | Não existe no painel. | `anuncios` por status → —. | S; disponível em `V-MA`, não agregado. | AUSENTE_NA_V3 | Contrato overview; F1. |
| O08 | Visão geral / anúncios pendentes | `/painel` → igual | KPI real. | Não existe. | Status/revisão → —. | S. | AUSENTE_NA_V3 | Contrato overview; F1. |
| O09 | Visão geral / anúncios pausados | `/painel` → igual | KPI real. | Não existe. | Status → —. | S. | AUSENTE_NA_V3 | Contrato overview; F1. |
| O10 | Visão geral / prontos para escalar | `/painel` → igual | KPI derivado. | Não existe. | Score, status e Premium em `P-OV` → —. | S; regra precisa ser portada, não inferida. | AUSENTE_NA_V3 | Portar regra; F4. |
| O11 | Visão geral / saldo de créditos | `/painel` → igual | Saldo real. | Saldo real pelo ledger. | `P-OV`/crédito legado → `V-MO`/`movimento_credito`; OP. | S; V3 fonte de verdade imutável. | EQUIVALENTE_E_FUNCIONAL | Preservar V3. |
| O12 | Visão geral / e-mail da sessão | Conta → `/painel` | Não é card da visão geral. | Card autenticado novo. | `V-AU`; DTO próprio. | S; somente dono. | FUNCAO_NOVA_V3 | Preservar. |
| O13 | Visão geral / telefone da sessão | Conta → `/painel` | Não é card da visão geral. | Card autenticado novo. | `V-AU`. | S; somente dono. | FUNCAO_NOVA_V3 | Preservar. |
| O14 | Visão geral / status da conta | Conta → `/painel` | Resumo existe em Minha conta. | Card explícito na visão geral. | Usuário → `V-AU`. | S; sem estado interno sensível. | FUNCAO_NOVA_V3 | Preservar. |
| O15 | Visão geral / ranking por anúncio | `/painel` → igual | Título, score, faixa, Premium e próximo passo. | Não existe. | `P-OV` → —. | S; ordenação calculada. | AUSENTE_NA_V3 | Portar contrato; F4. |
| O16 | Visão geral / recomendações resumidas | `/painel#recomendacoes` → — | Até cinco recomendações condicionais. | Não existe. | `P-OV` → —. | S; templates comerciais acionados por dados. | AUSENTE_NA_V3 | Implementar após score; F4. |
| O17 | Visão geral / CTA gerenciar anúncios | `/meus-anuncios` → igual | Acesso direto. | CTA para escolher anúncio/benefício e tab. | Navegação local. | S; mobile correto. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| O18 | Visão geral / CTA comprar créditos | `/creditos` → `/meus-anuncios` | Leva à compra de pacotes. | Leva à escolha de benefício, não compra saldo. | `P-CR` → `V-MO`. | S; finalidade diferente. | DIVERGENTE_DA_PRODUCAO | Repor CTA de pacotes sem ativar Efí; F3. |
| O19 | Visão geral / ledger recente | — → `/painel` | Resumos de recarga/consumo, sem ledger imutável. | Últimos cinco movimentos com saldo posterior. | `V-MO`; `movimento_credito`; OP. | S; DTO sanitizado. | FUNCAO_NOVA_V3 | Preservar. |
| O20 | Visão geral / benefícios ativos | — → `/painel` | KPI Premium, detalhes nos cards. | Lista de benefícios ativos da conta. | `V-MO`; `beneficio_ativacao`. | S; somente vigentes. | FUNCAO_NOVA_V3 | Preservar. |
| O21 | Visão geral / erro de saldo | `/painel` → igual | Erro de overview impede os cards. | Mensagem específica se monetização falhar. | `P-OV` → `V-MO`. | Sem falso zero; retry ocorre em reload. | EQUIVALENTE_E_FUNCIONAL | Melhorar retry depois, sem mascarar. |
| O22 | Visão geral / carregamento | `/painel` → igual | Loading da consulta. | Placeholder textual do saldo. | Fetch `no-store`. | Responsivo. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| O23 | Visão geral / próximo passo do ranking | `/painel` → — | CTA calculada por anúncio. | Não existe. | `P-OV` → —. | S; rota condicionada. | AUSENTE_NA_V3 | Portar com recomendações; F4. |
| O24 | Visão geral / resumo comercial | `/painel` → igual | Foco em visibilidade e desempenho. | Foco em sessão e perfil mínimo. | Contratos diferentes. | S; ambos responsivos. | DIVERGENTE_DA_PRODUCAO | Combinar funções, sem remover as novas; F1/F4. |

### 5.3 Meus anúncios

| ID | Área / subárea / finalidade | Rotas P → V3 | Produção | V3 | Dados, contrato e fonte | Segurança, estados, mobile e evidência | Classificação | Correção / fase |
|---|---|---|---|---|---|---|---|---|
| M01 | Meus anúncios / listagem própria | `/meus-anuncios` → igual | Lista real por usuário. | Lista real por sessão. | `P-MA` → `V-MA`; OA → OP. | S; V3 usa `PublicUserPrincipal`. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| M02 | Meus anúncios / quantidade total | mesma | Quantidade derivada da lista. | “N anúncios encontrados”. | `P-MA` → `V-MA`. | S; 200 vazio distinto de erro na V3. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| M03 | Meus anúncios / contador ativos | mesma | KPI no topo. | Não existe. | `P-MA`, status → —. | S. | AUSENTE_NA_V3 | Repor agregação; F1. |
| M04 | Meus anúncios / contador pendentes | mesma | KPI no topo. | Não existe. | `P-MA`, revisão → —. | S. | AUSENTE_NA_V3 | Repor agregação; F1. |
| M05 | Meus anúncios / contador pausados | mesma | KPI no topo. | Não existe. | `P-MA`, status → —. | S. | AUSENTE_NA_V3 | Repor agregação; F1. |
| M06 | Meus anúncios / contador Premium | mesma | KPI por features/impulsionamento. | Não existe. | `P-FT` → `V-MO`. | S; dado existe em outro contrato. | AUSENTE_NA_V3 | Compor overview sem duplicar catálogo; F1/F3. |
| M07 | Card / título | mesma | Exibido. | Exibido. | `P-MA` → `MeuAnuncioDto`. | S; texto sanitizado. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| M08 | Card / capa | mesma | Imagem de capa. | Primeira mídia pública segura ou placeholder. | Mídia legado → `V-MA`,`arquivo_midia`. | V3 não expõe restrita; responsivo. | EQUIVALENTE_E_FUNCIONAL | Preservar V3. |
| M09 | Card / localização | mesma | Cidade/localização. | Bairro, cidade e UF. | Localidades → `MeuAnuncioLocalizacaoDto`. | S. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| M10 | Card / preço | mesma | Exibido. | DTO possui preço, card não renderiza. | `P-MA` → `V-MA`; campo perdido na apresentação. | S; não é erro de backend. | AUSENTE_NA_V3 | Renderizar valor; F1. |
| M11 | Card / status de publicação | mesma | Postado, pendente, pausado. | Rascunho, revisão, aprovado, publicado, pausado, rejeitado, removido. | `P-MA` → `V-MA`. | S; V3 tem enum mais completo. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| M12 | Card / moderação | mesma | `pendingRevision` e estado resumido. | `statusModeracao` explícito. | Revisão legado → `MeuAnuncioDto`. | S; sem dados internos. | EQUIVALENTE_E_FUNCIONAL | Preservar V3. |
| M13 | Card / badge revisão | mesma | Badge de revisão pendente. | Texto de moderação pendente. | Mesmo domínio. | Responsivo. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| M14 | Card / badges Premium | mesma | Features ativas e expiração. | Não renderiza benefícios. | `P-FT` → `V-MO`, mas `V-MA` não transporta. | S. | AUSENTE_NA_V3 | Compor DTO próprio seguro; F3. |
| M15 | Card / contagem regressiva Premium | mesma | Atualiza a cada minuto. | Não existe. | `feature_ativacao.expiraEm` → `beneficio_ativacao`. | S; não confiar só no relógio cliente. | AUSENTE_NA_V3 | Backend decide vigência; UI exibe; F3. |
| M16 | Card / aviso de expiração | mesma | Alerta 24/48h. | Não existe. | Ativações vigentes. | S; recomendação comercial. | AUSENTE_NA_V3 | Repor sem alterar política; F3. |
| M17 | Meus anúncios / publicar novo | `/anunciar/wizard` → rota existente | CTA visível. | Wizard existe, CTA ausente na página. | Navegação; backend de criação existe. | S; desktop/mobile. | AUSENTE_NA_V3 | Restaurar botão; F1. |
| M18 | Card / editar | `/meus-anuncios/{slug}/editar` → igual | Abre wizard. | Abre o mesmo wizard V3. | `P-ED` → `V-MA`. | S, C na gravação; 409 em revisão. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| M19 | Card / detalhe próprio | — → `/meus-anuncios/{slug}` | Sem rota própria; card leva à edição. | Rota própria segura. | `V-MA`; OP. | S; 403/404 explícitos. | FUNCAO_NOVA_V3 | Preservar e completar dados; F1. |
| M20 | Card / monetizar | `/meus-anuncios/{slug}/monetizar` → rota existente | Botão visível. | Rota existe, botão ausente do card. | `P-FT` → `V-MO`. | S, C na compra. | AUSENTE_NA_V3 | Restaurar entrada; F1/F3. |
| M21 | Card / adicionar Story | Modal/`POST /stories` → — | Botão condicional a anúncio ativo e catálogo. | Não há ação no card. | `P-ST` → sem contrato de criação Story do anunciante. | S; não converter Stories em Premium. | AUSENTE_NA_V3 | Contrato próprio de Story; decisão posterior. |
| M22 | Card / pausar | `PUT /anuncios/{id}/pausar` → — | Executa imediatamente. | Ação ausente. | `P-LC` → —. | P valida dono, mas sem CSRF/confirmar. | AUSENTE_NA_V3 | Criar contrato V3 com confirmação; F1. |
| M23 | Card / reativar | `PUT /anuncios/{id}/postar` → — | Executa imediatamente. | Ação ausente. | `P-LC` → —. | P valida dono; sem CSRF. | AUSENTE_NA_V3 | Criar contrato V3; F1. |
| M24 | Card / excluir | `DELETE /anuncios/{id}` → — | Exclusão disponível. | Ação ausente. | `P-LC` → —. | P valida dono; sem CSRF. | AUSENTE_NA_V3 | Definir remoção lógica V3; F1. |
| M25 | Card / confirmar exclusão | Modal → — | Motivo e irreversibilidade; cancelar/confirmar. | Não existe porque ação não existe. | UI local. | Proteção visual presente na produção. | AUSENTE_NA_V3 | Repor com contrato; F1. |
| M26 | Card / confirmar pausa | Ação direta → — | Não há confirmação; um clique altera status. | Não há ação. | `P-LC` → —. | Risco comprovado na auditoria; produção sem CSRF. | SEGURANCA_INCORRETA | Exigir confirmação e CSRF na V3; F1. |
| M27 | Lista / carregamento | mesma | Estado de loading. | Estado de loading. | Fetch próprio. | Responsivo. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| M28 | Lista / vazio legítimo | mesma | Mostra ausência, mas também mascara falha. | Só mostra vazio após 200 real. | `P-MA` catch `setAnuncios([])`; V3 separa `erro`. | V3 mais confiável. | EQUIVALENTE_E_FUNCIONAL | Preservar distinção V3. |
| M29 | Lista / erro e retry | mesma | Erro vira lista vazia. | Erro explícito e “Tentar novamente”. | `P-MA` → `V-MA`. | Sem endpoint exposto ao usuário. | FUNCAO_NOVA_V3 | Preservar; não reintroduzir falso vazio. |
| M30 | Card / galeria/carrossel | mesma | Array de imagens e navegação no card. | Só uma capa. | Mídia legado → `V-MA` retorna mídias, card ignora. | S; V3 protege restritas. | DIVERGENTE_DA_PRODUCAO | Repor galeria só com URLs autorizadas; F1. |
| M31 | Card / capa restrita | mesma | Tratamento legado não individualizado. | Sinaliza “Capa protegida” sem URL. | `V-MA`,`MidiaPublicaSeguraPolicy`. | Privacidade e mobile aprovados. | FUNCAO_NOVA_V3 | Preservar. |

### 5.4 Detalhe autenticado do próprio anúncio

| ID | Área / subárea / finalidade | Rotas P → V3 | Produção | V3 | Dados, contrato e fonte | Segurança, estados, mobile e evidência | Classificação | Correção / fase |
|---|---|---|---|---|---|---|---|---|
| D01 | Detalhe / rota dedicada | — → `/meus-anuncios/{slug}` | Não existe. | Existe. | `V-MA`; OP. | S; layout responsivo. | FUNCAO_NOVA_V3 | Preservar. |
| D02 | Detalhe / título | Card/edit → detalhe | Disponível no card/wizard. | Exibido. | `V-MA`. | S. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| D03 | Detalhe / capa | Card/edit → detalhe | Disponível no card/wizard. | Exibida se pública. | `V-MA`; mídia segura. | Restrita sem URL. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| D04 | Detalhe / publicação | Card → detalhe | Status no card. | Status no detalhe. | `V-MA`. | S. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| D05 | Detalhe / moderação | Card/edit → detalhe | Revisão resumida. | Estado explícito. | `V-MA`. | S; sem decisão administrativa. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| D06 | Detalhe / localização | Card/edit → detalhe | Exibida. | Exibida. | `V-MA`. | S. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| D07 | Detalhe / categoria | Wizard → detalhe | Existe no wizard. | DTO contém, tela não renderiza. | `V-MA`; OP. | S. | DIVERGENTE_DA_PRODUCAO | Renderizar no detalhe; F1. |
| D08 | Detalhe / descrição | Wizard → detalhe | Existe no wizard. | DTO contém, tela não renderiza. | `V-MA`. | S. | DIVERGENTE_DA_PRODUCAO | Renderizar; F1. |
| D09 | Detalhe / serviços | Wizard → detalhe | Existe no wizard. | DTO contém, tela não renderiza. | `V-MA`. | S; dados estruturados. | DIVERGENTE_DA_PRODUCAO | Renderizar; F1. |
| D10 | Detalhe / locais de atendimento | Wizard → detalhe | Existe no wizard. | DTO contém, tela não renderiza. | `V-MA`. | S; dados estruturados. | DIVERGENTE_DA_PRODUCAO | Renderizar; F1. |
| D11 | Detalhe / contato próprio | Wizard → detalhe | WhatsApp editável. | DTO contém WhatsApp, tela não mostra. | `V-MA`. | Somente dono; não usar DTO público. | DIVERGENTE_DA_PRODUCAO | Exibir contexto próprio seguro; F1. |
| D12 | Detalhe / galeria e estados de mídia | Wizard → detalhe | Fotos no wizard/card. | DTO traz mídias, tela mostra só capa. | `V-MA`,`V-MM`. | Pendente/restrita sem URL pública. | DIVERGENTE_DA_PRODUCAO | Exibir gestão segura; F1. |
| D13 | Detalhe / métricas | Performance → detalhe | Métricas por anúncio no ranking. | Não existe. | `P-PF` → —. | S; não fabricar zero. | AUSENTE_NA_V3 | Contrato proprietário; F2. |
| D14 | Detalhe / Premium | Card/monetização → detalhe | Ativos e expiração disponíveis. | Não renderiza. | `P-FT` → `V-MO`. | S. | DIVERGENTE_DA_PRODUCAO | Compor benefícios vigentes; F3. |
| D15 | Detalhe / Stories | Card → detalhe | Entrada para Story. | Não existe. | `P-ST` → —. | S. | AUSENTE_NA_V3 | Fluxo próprio; fase posterior. |
| D16 | Detalhe / histórico | Conta/cards → detalhe | Histórico operacional disperso. | Não existe. | Logs/status legado → —. | S; não expor auditoria interna. | AUSENTE_NA_V3 | Definir DTO proprietário; F1/F2. |
| D17 | Detalhe / editar | Edit → edit | Acesso direto. | Botão real. | `P-ED` → `V-MA`. | S, C na gravação. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| D18 | Detalhe / pausar | Card → detalhe | Ação no card. | Não existe. | `P-LC` → —. | Requer confirmação/CSRF. | AUSENTE_NA_V3 | Implementar; F1. |
| D19 | Detalhe / excluir | Card → detalhe | Ação com confirmação no card. | Não existe. | `P-LC` → —. | Requer remoção lógica/CSRF. | AUSENTE_NA_V3 | Implementar; F1. |
| D20 | Detalhe / voltar | Lista → lista | Navegação natural. | Botão explícito. | Sem endpoint. | Responsivo. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| D21 | Detalhe / outro proprietário | Fluxo ID legado → slug V3 | Serviço de anúncio valida proprietário. | GET alheio retorna 403 e UI informa acesso negado. | `V-MA`; `MeusAnunciosConsultaService`. | Prova live sem revelar slug; sem mutação. | FUNCAO_NOVA_V3 | Preservar. |
| D22 | Detalhe / slug inexistente | — → slug V3 | Sem rota dedicada. | Backend e UI distinguem 404. | `V-MA`; OP. | Sem falso vazio. | FUNCAO_NOVA_V3 | Preservar. |

### 5.5 Wizard em modo edição

| ID | Área / subárea / finalidade | Rotas P → V3 | Produção | V3 | Dados, contrato e fonte | Segurança, estados, mobile e evidência | Classificação | Correção / fase |
|---|---|---|---|---|---|---|---|---|
| E01 | Edição / wizard único | `/anunciar/wizard`, `/editar` → equivalentes | Criação e edição compartilham wizard. | `AnuncioWizard` único com `mode`. | `P-ED` → `V-MA`. | S; desktop/mobile. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| E02 | Edição / hidratação backend | `/editar` → igual | Carrega anúncio do dono. | Carrega pelo slug próprio. | `P-ED` → `GET V-MA`. | S; 401/403/404. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| E03 | Edição / título | wizard → wizard | Editável. | Editável. | DTO de atualização. | C; validação backend. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| E04 | Edição / descrição | wizard → wizard | Editável. | Editável. | DTO de atualização. | C; dados preservados em erro. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| E05 | Edição / categoria | wizard → wizard | Taxonomia canônica. | Mesmas cinco categorias canônicas. | `CategoriaAnuncio`; OP. | C; sem código livre. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| E06 | Edição / preço | wizard → wizard | Editável. | Editável. | Decimal persistido. | C; validação backend. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| E07 | Edição / UF, cidade, bairro | wizard → wizard | Dependência legado. | Contratos V3 de localidades. | P localidade legado → `/api/public/localidades`. | C; erro não vira lista vazia. | EQUIVALENTE_E_FUNCIONAL | Preservar V3. |
| E08 | Edição / serviços | wizard → wizard | Estruturados. | Estruturados. | Enum/coleção V3. | C. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| E09 | Edição / locais de atendimento | wizard → wizard | Estruturados. | Estruturados. | Enum/coleção V3. | C. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| E10 | Edição / WhatsApp | wizard → wizard | Editável. | Editável. | DTO próprio; não público. | C; somente dono. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| E11 | Edição / mídias existentes | wizard → wizard | Lista mídias. | Hidrata e preserva ordem. | `P-ED` → `V-MM`. | Restrita sem URL pública. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| E12 | Edição / novo upload | wizard → wizard | Upload de foto no fluxo. | Foto/vídeo por contrato V3 e R2. | `V-MM`; multipart; OP. | C; tipo real, tamanho, formato. | EQUIVALENTE_E_FUNCIONAL | Preservar V3. |
| E13 | Edição / reordenar mídias | wizard → wizard | Ordem do wizard legado. | PATCH por ID real. | `V-MM`; `anuncio_midia`. | C; dono e ordem única. | FUNCAO_NOVA_V3 | Preservar. |
| E14 | Edição / remover mídia | wizard → wizard | Remoção legado. | Remoção lógica, sem apagar objeto. | `DELETE V-MM`. | C; dono; sem exclusão física. | FUNCAO_NOVA_V3 | Preservar. |
| E15 | Edição / proteção de mídia | wizard → wizard | Classificação legado menos granular. | Pendente/restrita não recebe URL. | R2 privado e política segura. | S; zero chave/bucket em DTO. | FUNCAO_NOVA_V3 | Preservar. |
| E16 | Edição / preservar slug | wizard → wizard | Slug atual permanece. | Contrato proíbe alteração. | `PATCH V-MA`; OP. | C; URL canônica preservada. | FUNCAO_NOVA_V3 | Preservar. |
| E17 | Edição / preservar primeira publicação | wizard → wizard | Histórico usado pelo painel. | Contrato não altera publicação/“Anuncia desde”. | Histórico status/publicação. | C; SEO preservado. | FUNCAO_NOVA_V3 | Preservar. |
| E18 | Edição / retorno à moderação | wizard → wizard | Revisão pendente. | Regra vigente envia a revisão; 409 se já em análise. | `PATCH V-MA`. | C; backend decide status. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| E19 | Edição / dupla submissão | wizard → wizard | Loading bloqueia ações. | Lock explícito e idempotência onde aplicável. | Frontend + backend. | C. | FUNCAO_NOVA_V3 | Preservar. |
| E20 | Edição / KYC etapa 7 | completar cadastro separado → wizard | Produção usa cadastro/documentos fora desta sequência. | Integrado no mesmo wizard. | `V-KY`; OP. | Privado; validação idade/CPF. | FUNCAO_NOVA_V3 | Preservar. |
| E21 | Edição / monetização no wizard | rota separada → etapa 6 | Fluxo separado por rota. | Etapa no wizard e rota dedicada coexistem. | `V-MO`. | S, C. | FUNCAO_NOVA_V3 | Preservar sem duplicar regra. |
| E22 | Edição / responsividade | wizard → wizard | Desktop/mobile. | Desktop/mobile, 320–390 px cobertos pelos validadores canônicos. | Mesmo componente. | Sem scroll lock. | EQUIVALENTE_E_FUNCIONAL | Preservar. |

### 5.6 Monetização do anúncio

| ID | Área / subárea / finalidade | Rotas P → V3 | Produção | V3 | Dados, contrato e fonte | Segurança, estados, mobile e evidência | Classificação | Correção / fase |
|---|---|---|---|---|---|---|---|---|
| Z01 | Monetização / rota | `/meus-anuncios/{slug}/monetizar` → igual | Existe. | Existe. | `P-FT`,`P-CR` → `V-MO`. | S. | EQUIVALENTE_E_FUNCIONAL | Restaurar apenas entrada no card. |
| Z02 | Monetização / sequência | mesma | Cinco etapas. | Anúncio, benefícios, resumo, saldo, sucesso. | Wizard compartilhado por origem. | Desktop/mobile. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| Z03 | Monetização / elegibilidade | mesma | Estado do anúncio e bloqueios. | Cotação backend e bloqueio de revisão. | `P-FT` → `V-MO`. | S; backend decide. | EQUIVALENTE_E_FUNCIONAL | Preservar V3. |
| Z04 | Monetização / saldo | mesma | Saldo disponível. | Saldo derivado do ledger. | Crédito legado → `movimento_credito`. | S. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| Z05 | Monetização / catálogo | mesma | Features ativas. | Benefícios ativos/configuráveis. | Catálogo legado → catálogo V3. | Sem hardcode de preço. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| Z06 | Monetização / custo e duração | mesma | Valores do backend. | Opções 1/7/14/30 e custo do backend. | `V-MO`; OP. | S. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| Z07 | Monetização / ativos e expiração | mesma | Exibe ativos. | Exibe ativos e vigência. | Ativações. | Backend decide expiração. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| Z08 | Monetização / seleção | mesma | Seleção de upgrades. | Seleção de benefícios/duração. | UI + cotação. | Sem mutação até confirmar. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| Z09 | Monetização / resumo | mesma | Resumo antes de pagar/ativar. | Itens, custo e saldo posterior. | UI + `V-MO`. | S. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| Z10 | Monetização / débito e ativação | mesma | Ativação legado. | Transação atômica no ledger. | `POST V-MO`; `movimento_credito`,`beneficio_ativacao`. | C; rollback integral. | FUNCAO_NOVA_V3 | Preservar. |
| Z11 | Monetização / saldo insuficiente | mesma | Bloqueia ação. | Mostra créditos faltantes e não debita. | Cotação backend. | C; sem mutação parcial. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| Z12 | Monetização / idempotência | mesma | Não comprovada como chave canônica. | Requisição repetida não debita duas vezes. | Idempotency key V3. | C. | FUNCAO_NOVA_V3 | Preservar. |
| Z13 | Monetização / confirmação | mesma | Estado final. | Tela de sucesso e atualização da conta. | Resposta de compra. | S. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| Z14 | Monetização / conflito de revisão | mesma | Bloqueio por estado. | 409 real e mensagem. | `V-MO`,`V-MA`. | S, C. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| Z15 | Monetização / comprar créditos via Pix | Checkout → estrutura V3 | Operação existe na produção. | Efí estrutural existe, mas está desabilitada por decisão. | `P-CR` → `V-EF`. | `EFI_ENABLED=false`; não testado. | REQUER_DECISAO_DO_PROPRIETARIO | Ativar só no gate Efí/cutover; F3. |
| Z16 | Monetização / entrada pela lista | Botão → sem botão | Acessível no card. | Só por URL/etapa do wizard. | Mesmo `V-MO`. | S; função escondida na principal gestão. | DIVERGENTE_DA_PRODUCAO | Repor CTA; F1/F3. |

### 5.7 Performance e contadores

| ID | Área / subárea / finalidade | Rotas P → V3 | Produção | V3 | Dados, contrato e fonte | Segurança, estados, mobile e evidência | Classificação | Correção / fase |
|---|---|---|---|---|---|---|---|---|
| P01 | Performance / tela | `/painel/performance` → igual | Dados reais. | Layout completo, sem dados. | `P-PF` → `V-PF`. | S; mensagem de erro explícita. | FUNCAO_APENAS_VISUAL | Ligar a contrato proprietário; F2. |
| P02 | Performance / navegação | tab → sem tab | Acesso principal. | Rota escondida do shell. | Sem endpoint. | Desktop/mobile. | AUSENTE_NA_V3 | Repor item; F1/F2. |
| P03 | Performance / visualizações totais | mesma | Soma `anuncios.visualizacoes`. | Não carrega. | `P-PF` → `V-PF`; OA. | S; não usar zero. | CONTRATO_BACKEND_AUSENTE | Endpoint e DTO; F2. |
| P04 | Performance / cliques WhatsApp | mesma | `count(cliques_whatsapp)`. | Não carrega. | `P-PF` → `V-PF`. | S. | CONTRATO_BACKEND_AUSENTE | Endpoint e query; F2. |
| P05 | Performance / CTR | mesma | `cliques / visualizações * 100`, 2 casas; zero se denominador zero. | Não carrega. | `P-PF` → `V-PF`. | S; definição precisa ser preservada. | CONTRATO_BACKEND_AUSENTE | Portar fórmula; F2. |
| P06 | Performance / anúncios Premium | mesma | Conta anúncios com recursos ativos. | Não carrega. | `feature_ativacao` → `beneficio_ativacao`. | S. | CONTRATO_BACKEND_AUSENTE | Compor vigência V3; F2. |
| P07 | Performance / série de 14 dias | mesma | Cliques diários de hoje-13 a hoje. | Não carrega. | `cliques_whatsapp` → agregados V3. | S; labels/datas. | CONTRATO_BACKEND_AUSENTE | Contrato temporal; F2. |
| P08 | Performance / últimos sete dias | mesma | Hoje-6 a hoje. | Não carrega. | `P-PF` → `V-PF`. | S. | CONTRATO_BACKEND_AUSENTE | Portar período; F2. |
| P09 | Performance / sete dias anteriores e variação | mesma | Hoje-13 a hoje-7; 100% se anterior zero e atual positivo; 0% se ambos zero. | Não carrega. | `P-PF` → `V-PF`. | S; regra exata auditada. | CONTRATO_BACKEND_AUSENTE | Portar regra; F2. |
| P10 | Performance / ranking por anúncio | mesma | Cliques desc, views desc, score desc. | Não carrega. | `P-PF` → `V-PF`. | S; somente anúncios do dono. | CONTRATO_BACKEND_AUSENTE | Query owner-scoped; F2. |
| P11 | Performance / score e faixa | mesma | Score e faixa em cada linha. | Tipos frontend existem, backend não. | Adapter V3 espera campos da produção. | S. | CONTRATO_BACKEND_AUSENTE | Implementar score canônico; F2/F4. |
| P12 | Performance / erro | mesma | Falha pode impedir tela. | “Não foi possível carregar a performance”; não mostra zeros. | `V-PF` 404. | Sem endpoint interno no texto. | EQUIVALENTE_E_FUNCIONAL | Preservar tratamento ao criar contrato. |
| V01 | Contadores / histórico dos cinco anúncios | Listagens/painel → equivalentes | Valores 414, 360, 359, 278 e 246. | Tabelas V3 têm zero. | P `anuncios.visualizacoes`; V3 eventos/agregados. | Consultas somente leitura; IDs mascarados A1–A5. | DIVERGENTE_DA_PRODUCAO | Importar histórico validado; F2. |
| V02 | Contadores / importador histórico | Snapshot → importação V3 | Campo existe na origem. | Staging normalizado não contém `visualizacoes`; scripts não o reconciliam. | Importadores canônicos. | Nenhuma importação executada agora. | AUSENTE_NA_V3 | Novo passo idempotente aprovado antes de cutover; F2. |
| V03 | Contadores / DTO de lista pública | API lista → `V-PU` | DTO legado entrega contagem à UI. | `AnuncioCardPublicoDto` não possui campo. | `V-PU`; OP sem `visualizacoes`. | Público; dado agregado não sensível. | CONTRATO_FRONTEND_INCOMPATIVEL | Decidir DTO e definição; F2. |
| V04 | Contadores / DTO de detalhe público | API detalhe → `V-PU` | Fonte de visualização disponível. | `AnuncioDetalhePublicoDto` não possui campo. | `V-PU`; OP. | Público. | CONTRATO_FRONTEND_INCOMPATIVEL | Incluir somente após regra; F2. |
| V05 | Contadores / DTO autenticado | API meus → `V-MA` | Performance própria disponível em contrato separado. | `MeuAnuncioDto` não possui métricas. | `V-MA`; OP. | Deve ser owner-scoped. | CONTRATO_FRONTEND_INCOMPATIVEL | DTO de performance separado; F2. |
| V06 | Contadores / card público | Card → card | Recebe contagem. | Prop opcional assume `0`; grids não a passam. | `anuncio-card.tsx`, linhas auditadas. | Zero aparenta dado real. | FALLBACK_HARDCODED | Omitir/estado indisponível até contrato; F2. |
| V07 | Contadores / registrar nova view | Registro legado → `POST V-VW` | Incremento histórico legado. | Serviço real grava evento com hashes. | `V-VW`; `evento_visualizacao`; OP. | Público, minimizado; não acionado nesta auditoria. | EQUIVALENTE_E_FUNCIONAL | Testar de modo controlado na implementação; F2. |
| V08 | Contadores / consulta do anunciante | `P-PF` → `V-PF` | Contrato real. | Adapter aponta para endpoint inexistente. | Frontend `/painel-anunciante/performance`; backend/OpenAPI ausentes. | Erro explícito. | CONTRATO_BACKEND_AUSENTE | Implementar um único contrato; F2. |

### 5.8 Créditos e planos

| ID | Área / subárea / finalidade | Rotas P → V3 | Produção | V3 | Dados, contrato e fonte | Segurança, estados, mobile e evidência | Classificação | Correção / fase |
|---|---|---|---|---|---|---|---|---|
| C01 | Créditos / página | `/creditos` → `/creditos` | Página completa. | Redireciona a `/painel`. | `P-CR` → `V-MO`,`V-EF`. | S; rota não cumpre finalidade. | AUSENTE_NA_V3 | Construir tela sobre contratos V3; F3. |
| C02 | Créditos / saldo | `/creditos` → `/painel`,`/monetizar` | Saldo real. | Saldo real em painel/wizard. | Crédito legado → ledger V3. | S; sem saldo direto. | EQUIVALENTE_E_FUNCIONAL | Preservar e exibir na tela futura. |
| C03 | Créditos / pacotes | `/creditos` → contrato sem UI | Prata, Ouro e Diamante. | `pacotesCredito` vem do backend, mas não é renderizado. | `P-CR` → `V-MO`. | S; nenhum preço hardcoded V3. | AUSENTE_NA_V3 | Renderizar catálogo ativo; F3. |
| C04 | Créditos / quantidade e preço | `/creditos` → contrato sem UI | 50/R$9,99; 150/R$19,99; 400/R$29,99 no snapshot. | Valores configuráveis existem no contrato, invisíveis. | Pacotes backend. | S; valores podem mudar. | AUSENTE_NA_V3 | Usar somente resposta backend; F3. |
| C05 | Créditos / catálogo de benefícios | `/creditos` → `/monetizar` | Carrossel, fotos, vídeo, Story, WhatsApp, idade, topo. | Catálogo V3 no wizard. | `P-FT` → `V-MO`; OP. | S. | EQUIVALENTE_E_FUNCIONAL | Preservar; Story continua fluxo próprio. |
| C06 | Créditos / custos e durações | `/creditos` → `/monetizar` | Backend entrega custo/duração. | Backend entrega 1/7/14/30 e custo. | Catálogo V3. | S; sem hardcode. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| C07 | Créditos / histórico | Resumo limitado → `/painel` | Recarga e consumo recentes. | Ledger recente detalhado. | `V-MO`; OP. | S. | FUNCAO_NOVA_V3 | Preservar e ampliar paginação; F3. |
| C08 | Créditos / ledger imutável | — → `/painel` | Saldo/histórico legados. | Fonte de verdade por lançamentos. | `movimento_credito`. | S; auditoria e saldo derivado. | FUNCAO_NOVA_V3 | Preservar. |
| C09 | Créditos / benefícios ativos | Cards → `/painel` | Ativos por anúncio. | Lista ativa por conta. | `beneficio_ativacao`. | S; vigência backend. | FUNCAO_NOVA_V3 | Preservar. |
| C10 | Créditos / comprar saldo | Botão → — | Abre pacote/checkout. | Não há UI de compra. | `P-CR` → `V-EF` estrutural. | S. | AUSENTE_NA_V3 | Repor quando gate financeiro autorizado; F3. |
| C11 | Créditos / checkout Pix | `/checkout/creditos/{planoId}` → estrutura | Produção cria cobrança. | Efí desabilitada. | `P-CR` → `V-EF`. | Não executar no HML/preprod. | REQUER_DECISAO_DO_PROPRIETARIO | Gate Efí/cutover. |
| C12 | Créditos / pagamento pendente | checkout → estrutura | Estado exibido/consultado. | Modelo existe, fluxo indisponível. | Pagamento V3. | Sem polling externo ativo. | REQUER_DECISAO_DO_PROPRIETARIO | Gate Efí; F3. |
| C13 | Créditos / pagamento aprovado | checkout → estrutura | Confirma crédito. | Webhook/ledger estruturados, desativados. | `V-EF` + ledger. | Idempotência obrigatória. | REQUER_DECISAO_DO_PROPRIETARIO | Gate Efí; F3. |
| C14 | Créditos / pagamento expirado | checkout → estrutura | Estado conhecido. | Modelo existe, UI ausente. | Pagamento V3. | Sem chamada externa. | REQUER_DECISAO_DO_PROPRIETARIO | Gate Efí; F3. |
| C15 | Créditos / pagamento cancelado | checkout → estrutura | Cancelamento/retorno. | Modelo existe, UI ausente. | Pagamento V3. | Sem mutação nesta auditoria. | REQUER_DECISAO_DO_PROPRIETARIO | Gate Efí; F3. |
| C16 | Créditos / retorno ao checkout | checkout → — | Retorno e verificação. | Não há jornada pública ativa. | `P-CR` → `V-EF`. | S. | REQUER_DECISAO_DO_PROPRIETARIO | Definir junto ao gate Efí; F3. |
| C17 | Créditos / comprar benefício com saldo | Monetizar → igual | Ativa upgrade. | Debita e ativa benefício. | `POST V-MO`. | C; owner-scoped. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| C18 | Créditos / saldo insuficiente | mesma | Bloqueia. | Bloqueia sem lançamento/ativação. | Cotação e transação V3. | S, C. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| C19 | Créditos / atomicidade | mesma | Não comprovada como ledger único. | Débito, ativação e auditoria na mesma transação. | Serviço V3. | C; rollback. | FUNCAO_NOVA_V3 | Preservar. |
| C20 | Créditos / repetição | mesma | Não comprovada. | Idempotência impede débito duplo. | Chave de operação V3. | C. | FUNCAO_NOVA_V3 | Preservar. |

### 5.9 Recomendações e score

| ID | Área / subárea / finalidade | Rotas P → V3 | Produção | V3 | Dados, contrato e fonte | Segurança, estados, mobile e evidência | Classificação | Correção / fase |
|---|---|---|---|---|---|---|---|---|
| R01 | Recomendações / bloco | `/painel#recomendacoes` → — | Lista personalizada. | Não existe. | `P-OV` → —. | S. | AUSENTE_NA_V3 | Contrato overview; F4. |
| R02 | Recomendações / score base | mesma | 20 ativo; descrição 8/12/16; fotos 4/8/12/16; local 5/10; boost 12; carrossel 6; WhatsApp 4; extras 4; vídeo 8; recência 5/8; clique 6; teto 100. | Não existe. | `PainelAnuncianteService`. | Regra calculada, não armazenada. | AUSENTE_NA_V3 | Portar e testar fórmula; F4. |
| R03 | Recomendações / perfil incompleto | mesma | Condicional a score/conteúdo. | Não existe. | `P-OV`. | S. | AUSENTE_NA_V3 | Portar condição e CTA; F4. |
| R04 | Recomendações / poucas fotos | mesma | Condicional à quantidade. | Não existe. | Mídias + score. | Não confundir limite com contagem. | AUSENTE_NA_V3 | Portar; F4. |
| R05 | Recomendações / vídeo | mesma | Condicional à ausência do recurso. | Não existe. | Catálogo/ativação. | S. | AUSENTE_NA_V3 | Portar; F4. |
| R06 | Recomendações / impulsionamento | mesma | Escolhe melhor anúncio. | Não existe. | Score/status/Premium. | CTA a monetização. | AUSENTE_NA_V3 | Portar; F4. |
| R07 | Recomendações / carrossel | mesma | Condicional à mídia/recurso. | Não existe. | Mídias/ativação. | S. | AUSENTE_NA_V3 | Portar; F4. |
| R08 | Recomendações / saldo baixo | mesma | Condicional ao saldo. | Painel mostra saldo, não recomendação. | Crédito legado → ledger V3. | S. | AUSENTE_NA_V3 | Portar sem ativar checkout; F4. |
| R09 | Recomendações / alvo por anúncio | mesma | Título/slug e próximo passo. | Não existe. | Ranking P. | Somente anúncios do dono. | AUSENTE_NA_V3 | Owner-scoped; F4. |
| R10 | Recomendações / CTA | mesma | Gerenciar, impulsionar, vídeo, carrossel, créditos. | Não existe. | Rotas condicionais P. | Não simular ação. | AUSENTE_NA_V3 | Restaurar após contratos; F4. |

### 5.10 Conta e configurações

| ID | Área / subárea / finalidade | Rotas P → V3 | Produção | V3 | Dados, contrato e fonte | Segurança, estados, mobile e evidência | Classificação | Correção / fase |
|---|---|---|---|---|---|---|---|---|
| A01 | Conta / username visível | `/minha-conta` → igual | Exibido. | Exibido. | `P-AC` → `V-AU`. | S. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| A02 | Conta / editar username | mesma | Editável. | Editável. | `PUT P-AC` → `PATCH V-AU`. | V3 deriva usuário da sessão e exige C. | EQUIVALENTE_E_FUNCIONAL | Preservar V3. |
| A03 | Conta / e-mail visível | mesma | Exibido/editável. | Exibido somente leitura. | `P-AC` → `V-AU`. | S; sem exposição pública. | EQUIVALENTE_E_FUNCIONAL | Preservar leitura. |
| A04 | Conta / editar e-mail | mesma | Editável. | Bloqueado com explicação. | `PUT P-AC` → sem campo V3. | Produção tem IDOR pelo e-mail da rota. | DIVERGENTE_DA_PRODUCAO | Só reintroduzir com reautenticação e sessão; F4. |
| A05 | Conta / telefone | mesma | Exibido/editável. | Exibido/editável. | `P-AC` → `V-AU`. | V3 sessão + C. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| A06 | Conta / UF | mesma | Selecionável. | Ausente. | `P-LC2`,`P-AC` → —. | S. | AUSENTE_NA_V3 | Usar localidades V3; F4. |
| A07 | Conta / cidade | mesma | Dependente de UF. | Ausente. | `P-LC2` → —. | S. | AUSENTE_NA_V3 | Usar adapter canônico; F4. |
| A08 | Conta / bairro | mesma | Dependente de cidade. | Ausente. | `P-LC2` → —. | S. | AUSENTE_NA_V3 | Usar adapter canônico; F4. |
| A09 | Conta / descrição de perfil | mesma | Editável. | Ausente. | `P-AC` → —. | S; texto privado do dono até publicação. | AUSENTE_NA_V3 | Definir campo canônico; F4. |
| A10 | Conta / alterar senha | mesma | Senha atual, nova, confirmação. | Ausente. | `P-AC` → sem endpoint V3 autenticado. | Produção usa e-mail da rota; V3 deve usar principal e C. | AUSENTE_NA_V3 | Criar contrato seguro; F4. |
| A11 | Conta / 2FA | mesma | Ativar, verificar e desativar. | Desativado por decisão definitiva de migração. | `P-2F` → —. | Produção não vincula e-mail ao principal; segredos não migrados. | DIVERGENTE_DA_PRODUCAO | Não reintroduzir sem nova decisão. |
| A12 | Conta / excluir conta | mesma | Ação disponível. | Ausente. | `DELETE P-AC` → —. | Produção seleciona alvo pelo e-mail sem conferir principal. | AUSENTE_NA_V3 | Criar remoção segura por sessão; F4. |
| A13 | Conta / confirmar exclusão | mesma | Aviso irreversível e confirmação. | Ausente. | UI P. | Exigir reautenticação/CSRF na V3. | AUSENTE_NA_V3 | Implementar com A12; F4. |
| A14 | Conta / saldo | mesma | Resumo. | Painel mostra saldo do ledger. | `P-CR` → `V-MO`. | S. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| A15 | Conta / total de anúncios | mesma | Resumo numérico. | Não aparece na conta; lista existe. | `P-MA` → `V-MA`. | S. | AUSENTE_NA_V3 | Agregar sem nova fonte; F4. |
| A16 | Conta / documentos verificados | mesma | Resumo documental. | KYC existe no wizard, não no resumo da conta. | Docs legado → `V-KY`. | Documento sempre privado. | DIVERGENTE_DA_PRODUCAO | Exibir somente status/contagem sanitizada; F4. |
| A17 | Conta / status | mesma | Resumo. | Card no painel. | Usuário → `V-AU`. | S. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| A18 | Conta / recarga recente | mesma | Resumo específico. | Ledger genérico; pagamentos reais desabilitados. | `P-CR` → `V-MO`. | S. | DIVERGENTE_DA_PRODUCAO | Filtro de entradas sem ativar Efí; F3/F4. |
| A19 | Conta / consumo recente | mesma | Resumo. | Débitos aparecem no histórico. | `V-MO`. | S. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| A20 | Conta / KYC no wizard | cadastro separado → etapa 7 | Fluxo separado. | Integrado ao wizard. | `V-KY`. | S, C, R2 privado. | FUNCAO_NOVA_V3 | Preservar. |
| A21 | Conta / documentos privados | URLs legado → R2 privado | Fluxo legado. | Sem URL pública; admin recebe URL temporária. | `V-KY`, ObjectStorage. | RBAC admin, expiração curta. | FUNCAO_NOVA_V3 | Preservar. |
| A22 | Conta / logout | header → painel/header | Funcional. | Funcional. | `V-AU`. | C; invalida sessão. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| A23 | Conta / validação e erro | mesma | Validação de formulário. | Erro explícito, loading e preservação do input. | `V-AU`; OP. | C; responsivo. | EQUIVALENTE_E_FUNCIONAL | Preservar. |

### 5.11 Áreas adicionais do anunciante

| ID | Área / subárea / finalidade | Rotas P → V3 | Produção | V3 | Dados, contrato e fonte | Segurança, estados, mobile e evidência | Classificação | Correção / fase |
|---|---|---|---|---|---|---|---|---|
| X01 | Indicações / página | `/indicacoes` → igual | Dados reais. | Layout renderiza sem contrato. | `P-RF` → `V-NO`. | S; responsivo. | FUNCAO_APENAS_VISUAL | Criar contrato; fase posterior. |
| X02 | Indicações / link pessoal | mesma | Link real. | `linkIndicacao` é `''` por default do `AuthContext`. | `P-RF` → —. | S; parece dado válido vazio. | FALSO_VAZIO | Remover default operacional; contrato explícito. |
| X03 | Indicações / copiar link | mesma | Copia URL válida. | Copia string vazia e mostra sucesso. | Sem backend. | Simula sucesso. | QUEBRADO | Bloquear e informar contrato ausente. |
| X04 | Indicações / pessoas indicadas | mesma | Contagem real. | `0` hardcoded no mapeamento da sessão. | `P-RF` → —. | S; falso indicador. | FALSO_VAZIO | Contrato real; fase posterior. |
| X05 | Indicações / créditos ganhos | mesma | Contagem real. | `0` hardcoded no mapeamento. | `P-RF` → —. | S; falso indicador. | FALSO_VAZIO | Contrato real. |
| X06 | Indicações / crédito por indicação | mesma | Configuração real. | `0` hardcoded. | `P-RF` → —. | S; falso indicador. | FALSO_VAZIO | Contrato real. |
| X07 | Favoritos / listar | `/favoritos` → igual | Lista própria. | Lista própria. | `P-FV` → `V-FV`; OP. | S; 200 vazio legítimo. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| X08 | Favoritos / incluir e remover | Cards → cards | Funcional. | PUT/DELETE idempotentes. | `V-FV`. | S, C; usuário da sessão. | EQUIVALENTE_E_FUNCIONAL | Preservar. |
| X09 | Chat / tela | `/chat` → igual | Conversas reais. | Controles e erro explícito. | `P-SU` → `V-NO`. | S; sem polling inexistente. | CONTRATO_BACKEND_AUSENTE | Contrato chat; fase posterior. |
| X10 | Chat / buscar usuário | mesma | `/usuarios/username/{username}`. | Campo existe, ação pendente. | P usuário → —. | Evitar enumeração. | CONTRATO_BACKEND_AUSENTE | Busca sanitizada; fase posterior. |
| X11 | Chat / badge de mensagens | header → header | Atualizado por chat/WebSocket. | Sem fonte e sem contador falso. | `P-SU` → `V-NO`. | Sem polling de endpoint ausente. | CONTRATO_BACKEND_AUSENTE | Criar somente com contrato chat. |
| X12 | Suporte / listar tickets | `/meus-tickets` → igual | Lista e filtros reais. | Tabela/filters preservados, sem dados. | `P-SU` → `V-NO`. | S; erro explícito. | CONTRATO_BACKEND_AUSENTE | Contrato tickets; fase posterior. |
| X13 | Suporte / abrir ticket | mesma | Motivo, descrição, anexo. | Formulário preservado, não executa. | `P-SU` → `V-NO`. | S; sem sucesso simulado. | CONTRATO_BACKEND_AUSENTE | Criar contrato seguro. |
| X14 | Suporte / conversa | mesma | Mensagens/WebSocket. | Modal e textarea preservados, não executam. | `P-SU` → `V-NO`. | S; anexos não enviados. | CONTRATO_BACKEND_AUSENTE | Contrato e canal autenticado. |
| X15 | Suporte / encerrar | mesma | Fecha ticket. | Botão preservado, não executa. | `P-SU` → `V-NO`. | S; confirmação necessária. | CONTRATO_BACKEND_AUSENTE | Criar contrato. |
| X16 | Feedback / enviar | modal → modal | Sugestão/bug real. | Campos preservados, não executa. | Produção sugestão → `V-NO`. | S; erro explícito. | CONTRATO_BACKEND_AUSENTE | Criar contrato. |
| X17 | Suporte / botão flutuante | global → global | Abre suporte. | Abre superfície pendente. | `P-SU` → `V-NO`. | Não chama endpoint repetidamente. | CONTRATO_BACKEND_AUSENTE | Ligar ao contrato futuro. |
| X18 | Avisos de conta | pós-login → global | Avisos reais conforme configuração. | Popup pendente após login. | Produção avisos → `V-NO`. | S; não há contrato. | CONTRATO_BACKEND_AUSENTE | Contrato específico de avisos. |

### 5.12 Aviso “Integração pendente”

| ID | Área / subárea / finalidade | Rotas P → V3 | Produção | V3 | Dados, contrato e fonte | Segurança, estados, mobile e evidência | Classificação | Correção / fase |
|---|---|---|---|---|---|---|---|---|
| G01 | Avisos / banner global | Avisos contextuais → todas as rotas | Não marca todo o site como indisponível. | `SitePopupManager` monta banner em todo `PublicChrome`. | `PENDING_BACKEND_CONTRACTS.notices`. | Sem fetch; desktop/mobile. | DIVERGENTE_DA_PRODUCAO | Contextualizar por função; F1. |
| G02 | Avisos / popup pós-login | Avisos reais → popup pendente | Conteúdo real configurado. | Abre apenas mensagem de contrato ausente. | `V-NO`. | S; não expõe endpoint. | CONTRATO_BACKEND_AUSENTE | Criar contrato de avisos. |
| G03 | Avisos / associação a Meus anúncios | — → `/meus-anuncios` | Página não recebe aviso global indevido. | Banner aparece embora lista, detalhe e imagem funcionem. | Manager está fora da árvore da página. | Diagnóstico: `GLOBAL_INDEVIDO`. | DIVERGENTE_DA_PRODUCAO | Remover associação global, não a função; F1. |
| G04 | Avisos / rodapé | Conteúdo real → rodapé global | Rodapé carrega conteúdo. | Segundo estado de integração pendente por conteúdo institucional. | Contrato site-content ausente. | Não torna a página indisponível, mas duplica alerta. | CONTRATO_BACKEND_AUSENTE | Contextualizar no rodapé; fase posterior. |

### 5.13 Segurança transversal

| ID | Área / subárea / finalidade | Rotas P → V3 | Produção | V3 | Dados, contrato e fonte | Segurança, estados, mobile e evidência | Classificação | Correção / fase |
|---|---|---|---|---|---|---|---|---|
| S01 | Segurança / CSRF em mutações | Ações P → ações V3 | `SecurityConfig` desabilita CSRF globalmente. | Cookie CSRF e header nas mutações; só webhook Efí é exceção. | Configurações de segurança. | Risco alto na produção com cookie de sessão. | SEGURANCA_INCORRETA | Não copiar padrão P; preservar V3. |
| S02 | Segurança / edição de perfil por alvo | `PUT /usuarios/{email}/editar` → `PATCH /auth/me` | Qualquer papel autenticado pode informar outro e-mail; serviço não confere principal. | Usuário vem exclusivamente da sessão. | `P-AC` → `V-AU`. | IDOR comprovada por código, não explorada. | SEGURANCA_INCORRETA | Manter contrato V3; tratar produção separadamente. |
| S03 | Segurança / exclusão por alvo | `DELETE /usuarios/{email}/excluir-conta` → — | Serviço seleciona pelo e-mail e usa autenticação só para log. | Função ainda ausente. | `P-AC` → —. | IDOR destrutiva comprovada por código, não executada. | SEGURANCA_INCORRETA | V3 deve ignorar alvo do cliente e exigir reautenticação. |
| S04 | Segurança / 2FA por alvo | `/2fa/*` → — | Status/ativação/verificação usam e-mail informado sem conferir principal. | 2FA desativado por decisão e segredos não migrados. | `P-2F` → —. | Risco legado; não explorado. | SEGURANCA_INCORRETA | Não reintroduzir; corrigir produção em tarefa própria. |
| S05 | Segurança / pausa sem confirmação | `PUT /anuncios/{id}/pausar` → — | Um clique muta, sem diálogo; confirmado pelo evento acidental restaurado. | Ação ausente. | `P-LC` → —. | Dono validado, mas sem CSRF/confirmação. | SEGURANCA_INCORRETA | Confirmação + C + auditoria; F1. |

## 6. Diagnóstico comprovado dos contadores zerados

### 6.1 Amostra de cinco anúncios

Os anúncios foram associados por mapa técnico apenas durante a consulta. Nenhum slug, proprietário ou contato é registrado aqui.

| Amostra | Produção: `anuncios.visualizacoes` | V3: eventos | V3: agregados | API lista/detalhe/própria | Card V3 |
|---|---:|---:|---:|---|---|
| A1 | 414 | 0 | 0 | Campo ausente | Exibe 0 por default |
| A2 | 360 | 0 | 0 | Campo ausente | Exibe 0 por default |
| A3 | 359 | 0 | 0 | Campo ausente | Exibe 0 por default |
| A4 | 278 | 0 | 0 | Campo ausente | Exibe 0 por default |
| A5 | 246 | 0 | 0 | Campo ausente | Exibe 0 por default |

### 6.2 Cadeia causal

1. **`HISTORICO_NAO_IMPORTADO`**: o campo legado existe na produção, mas o payload normalizado do staging não o contém e nenhum importador aprovado cria saldo inicial de visualizações.
2. **`DTO_NAO_TRANSPORTA`**: `AnuncioCardPublicoDto`, `AnuncioDetalhePublicoDto` e `MeuAnuncioDto` não têm `visualizacoes`.
3. **`CONTRATO_BACKEND_AUSENTE`**: a tela do anunciante chama `/api/public/painel-anunciante/performance`, que não existe no backend nem no OpenAPI.
4. **`FRONTEND_APLICA_ZERO`**: `AnuncioCard` define `visualizacoes = 0` e as grids não passam a propriedade.
5. **`ENDPOINT_DE_REGISTRO_QUEBRADO` não foi comprovado**: o controller, serviço, repository, migration e OpenAPI de `POST /api/public/anuncios/{slug}/visualizacao` existem e gravam eventos minimizados. O endpoint não foi acionado nesta auditoria para evitar escrita.
6. **`DEFINICAO_DIVERGENTE`**: a produção usa contador acumulado em `anuncios.visualizacoes`; a V3 modela eventos e agregados diários. A migração precisa definir um agregado inicial rastreável, sem transformar pageview, card impression e visitante único na mesma métrica.

Conclusão: **é necessário importar o histórico confiável** para preservar os números existentes. A importação deve ser idempotente, por anúncio mapeado, e separada dos novos eventos V3. Sem isso, mesmo um futuro endpoint de consulta retornaria apenas dados pós-cutover.

## 7. Regras exatas da produção

### 7.1 Score

- anúncio ativo: `+20`;
- descrição com 60/120/220 caracteres: `+8/+12/+16`;
- 1/3/5/8 fotos: `+4/+8/+12/+16`;
- cidade: `+5`; cidade e bairro: `+10`;
- impulsionamento: `+12`;
- carrossel: `+6`; WhatsApp card: `+4`; fotos extras: `+4`; vídeo: `+8`;
- criado há menos de 30/15 dias: `+5/+8`;
- clique WhatsApp recente: `+6`;
- teto: `100`; faixas: até 30 baixo, até 60 pode melhorar, até 80 bom, acima de 80 alto.

### 7.2 Performance

- visualizações: soma do contador acumulado dos anúncios próprios;
- cliques: contagem de `clique_whatsapp` dos anúncios próprios;
- CTR: `cliques / visualizações * 100`, arredondado a duas casas;
- período atual: hoje menos seis dias até hoje;
- período anterior: hoje menos treze até hoje menos sete;
- variação: `100%` quando anterior é zero e atual positivo; `0%` quando ambos são zero;
- série: 14 pontos diários de cliques;
- ranking: cliques desc, visualizações desc e score desc.

### 7.3 Recomendações

As recomendações não são armazenadas. O serviço calcula condições reais e aplica templates comerciais: impulsionamento, saldo baixo, vídeo, carrossel, fotos e melhoria de perfil. Logo, o texto é template, mas a seleção é personalizada e vinculada aos dados.

## 8. Contratos backend ainda ausentes por módulo

- **Painel/performance**: overview comercial, métricas próprias, série, comparativo, ranking, score e recomendações.
- **Ciclo de vida do anúncio**: pausar, reativar e remover logicamente pelo proprietário.
- **Stories do anunciante**: criação/gestão no fluxo próprio.
- **Conta**: alteração segura de senha, localização/descrição, exclusão e resumos completos.
- **Indicações**: link, contagens, regra de crédito e histórico.
- **Chat/suporte**: conversas, badge, tickets, mensagens, anexos e encerramento.
- **Feedback**: sugestão e reporte de erro.
- **Avisos**: avisos de conta e conteúdo institucional contextualizado.
- **Créditos externos**: a estrutura Efí existe, mas permanece desabilitada por decisão; não é um contrato a ativar nesta fase.

## 9. Contratos frontend incompatíveis

- `fetchPainelPerformance` conserva o shape da produção, mas aponta para um contrato V3 inexistente.
- O card público aceita `visualizacoes`, enquanto os DTOs públicos e os adapters de grid não fornecem esse campo; o default `0` fabrica um valor.
- `MeuAnuncioDto` possui preço, categoria, descrição, serviços, locais, WhatsApp e mídias, mas a lista/detalhe exibem somente um subconjunto.
- `/creditos` e `/planos-e-creditos` mantêm rotas, porém redirecionam e não renderizam `pacotesCredito` já retornados por `V-MO`.
- `/indicacoes` lê propriedades preenchidas com `''`/`0` no `AuthContext`, sem contrato backend.

## 10. Riscos e controles de segurança

### Riscos comprovados

1. CSRF desabilitado na produção para mutações autenticadas por cookie.
2. IDOR na edição do perfil da produção por e-mail de rota sem conferência do principal.
3. IDOR destrutiva na exclusão da conta da produção pelo mesmo padrão.
4. 2FA legado seleciona conta por e-mail informado sem vínculo ao principal.
5. Pausa/reativação sem confirmação; a pausa acidental e a restauração imediata provaram o risco.

### Controles V3 comprovados

- sessão pública e administrativa separadas;
- CSRF nas mutações, com exceção explícita apenas do webhook Efí;
- listagem, detalhe, mídia, favoritos, KYC, saldo e ledger resolvem o usuário pela sessão;
- acesso autenticado a anúncio de outro proprietário retornou 403 no smoke somente leitura;
- slug inexistente possui 404 próprio;
- documentos, mídias pendentes e restritas não expõem URL pública, bucket, object key ou URL assinada em DTO público;
- saldo e ledger não aceitam `usuarioId` fornecido pelo frontend público.

Os padrões inseguros de IDOR e CSRF observados na produção não serão reproduzidos; os controles superiores já presentes na V3 devem ser preservados em toda implementação de paridade.

Uma segunda conta não foi necessária para comprovar o isolamento do detalhe: a conta autorizada consultou, sem mutação, um slug público pertencente a outro usuário e recebeu 403. Não foram tentadas métricas ou ledger de terceiro porque os serviços V3 já derivam o proprietário da sessão e não aceitam alvo arbitrário.

## 11. Ordem recomendada de implementação

Cada fase abaixo define apenas a ordem macro; a execução será subdividida em tarefas cirúrgicas independentes, e nenhuma fase será tratada como um único delta.

### Fase 1 — gestão essencial e contexto

1. Restaurar os seis itens do painel em desktop/mobile.
2. Completar lista e detalhe próprios com dados já presentes no DTO.
3. Adicionar criar, monetizar, pausar, reativar e remoção lógica com sessão, CSRF, confirmação e auditoria.
4. Contextualizar o aviso de integração, sem removê-lo de funções realmente pendentes.

### Fase 2 — métricas

1. Definir e importar o agregado inicial de visualizações por anúncio.
2. Criar um contrato proprietário único para views, cliques, CTR, períodos, série e ranking.
3. Remover o zero fabricado do card e transportar a métrica apenas após a definição canônica.
4. Validar isolamento por dono, novos registros e comparação com o histórico.

### Fase 3 — créditos e jornada comercial

1. Renderizar pacotes, catálogo, custos, durações, saldo, ledger e benefícios já existentes no backend.
2. Restaurar entradas para monetização e compra de saldo.
3. Manter Efí desabilitada até autorização específica; não simular Pix.

### Fase 4 — recomendações e conta

1. Portar score, faixas, ranking e recomendações com as fórmulas auditadas.
2. Completar localização, descrição, senha, resumos e exclusão segura da conta.
3. Implementar indicações, avisos, feedback e suporte em contratos próprios, mantendo 2FA desativado conforme decisão vigente.

## 12. Síntese

- A V3 preserva autenticação, perfil mínimo, favoritos, listagem/detalhe/edição próprios, monetização por ledger, Premium, mídias R2 e KYC, com controles de segurança superiores.
- A paridade comercial não está fechada: navegação, KPIs, ciclo de vida, performance, recomendações, pacotes e conta completa permanecem ausentes ou divergentes.
- As telas de chat, suporte, feedback, avisos e performance preservam superfície visual, mas carecem de contratos reais.
- Indicações é o falso-vazio mais grave do painel adicional: link vazio e três indicadores zero aparentam dados legítimos.
- O aviso “Integração pendente” em Meus anúncios é `GLOBAL_INDEVIDO`; ele descreve avisos do site, não a lista própria que está funcional.

### Contagem da matriz

| Classificação | Quantidade |
|---|---:|
| `EQUIVALENTE_E_FUNCIONAL` | 76 |
| `FUNCAO_NOVA_V3` | 28 |
| `AUSENTE_NA_V3` | 58 |
| `DIVERGENTE_DA_PRODUCAO` | 22 |
| `FUNCAO_APENAS_VISUAL` | 2 |
| `CONTRATO_BACKEND_AUSENTE` | 26 |
| `CONTRATO_FRONTEND_INCOMPATIVEL` | 3 |
| `FALSO_VAZIO` | 4 |
| `FALLBACK_HARDCODED` | 1 |
| `QUEBRADO` | 1 |
| `SEGURANCA_INCORRETA` | 6 |
| `REQUER_DECISAO_DO_PROPRIETARIO` | 7 |
| **Total** | **234** |
