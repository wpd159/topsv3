# Auditoria Canônica Integral — Produção x V3

Data da auditoria: 2026-07-19
V3 auditada: `2706f80676447d00b34de4abc1e2123a70a710b1`
Clone de produção auditado: `9c16d58f542171ac23c0b9780feba8fb3c686bce`
Ambiente vivo: `https://v3.esle.cloud`

> Este documento é somente auditoria. Nenhuma correção, migração, escrita em banco/R2, commit, push ou deploy foi executado.

## Prioridades da baseline

1. SEO.
2. Eliminação de bugs, contratos inexistentes e legado indevido.
3. Preservação integral das funções da produção.
4. Segurança, estabilidade e velocidade como gates obrigatórios.

## 1. Método e cobertura

- Leitura linear integral de 851 arquivos em `docs/v3`: 605 Markdown, 15 JSON e 231 PNGs.
- Os 620 arquivos textuais foram lidos em ordem; os 231 PNGs foram inspecionados em ordem por folhas de contato privadas fora do repositório.
- A relação nominal dos 851 arquivos lidos está registrada em `metadados.documentosLidos` da matriz JSON.
- Inventário estático de 308 arquivos fonte/72 páginas da produção e 311 arquivos fonte/75 páginas da V3.
- Extração de 1.811 controles, 261 fetches, 555 candidatos de endpoint e 558 navegações na produção.
- Extração de 1.593 controles, 198 fetches, 500 candidatos de endpoint e 490 navegações na V3.
- Confronto de 115 operações backend com 114 operações OpenAPI.
- Comparação de 283 arquivos fonte comuns: 151 idênticos e 132 alterados; 27 exclusivos da produção e 30 exclusivos da V3.
- Navegação pública ao vivo em produção e V3 para Home, catálogo, cidade e detalhe; navegação autenticada visível na produção como anunciante.
- A auditoria autenticada da V3 não foi promovida a equivalência porque não havia credencial de auditoria disponível nesta execução. Evidência de código/teste não substituiu ação real ao vivo.

## 2. Inventário quantitativo

| Superfície | Produção | V3 |
|---|---:|---:|
| Arquivos fonte frontend | 308 | 311 |
| Páginas | 72 | 75 |
| Controles extraídos | 1.811 | 1.593 |
| Fetches | 261 | 198 |
| Candidatos de endpoint | 555 | 500 |
| Navegações | 558 | 490 |

- Rotas de página únicas no conjunto: 76.
- Navegação explicitamente extraída: 23 destinos no menu administrativo da produção, 24 na V3 e 24 comandos distintos compartilhados nos headers/blog/sidebar públicos e autenticados.
- Identificadores de menu/ação usados para organizar a matriz: 252.
- Funções auditadas na matriz: 1469.
- Operações backend: 115; OpenAPI: 114; lacuna: `POST /api/admin/outbox/{id}/auth-test-code`.

### Por perfil

| Perfil | Funções |
|---|---:|
| ADMINISTRADOR | 554 |
| VISITANTE | 379 |
| ANUNCIANTE | 350 |
| ADMINISTRADOR/MODERADOR | 164 |
| PROPRIETÁRIO | 10 |
| ANUNCIANTE/ADMINISTRADOR | 4 |
| VISITANTE/ADMINISTRADOR | 4 |
| VISITANTE/ANUNCIANTE | 2 |
| VISITANTE/ANUNCIANTE/ADMINISTRADOR | 2 |

### Por classificação

| Classificação | Funções |
|---|---:|
| EQUIVALENTE_E_FUNCIONAL | 154 |
| FUNCAO_NOVA_V3 | 112 |
| AUSENTE_NA_V3 | 282 |
| CONTRATO_BACKEND_AUSENTE | 354 |
| CONTRATO_FRONTEND_INCOMPATIVEL | 30 |
| DIVERGENTE_DA_PRODUCAO | 429 |
| QUEBRADO | 3 |
| FALSO_VAZIO | 33 |
| FALLBACK_HARDCODED | 45 |
| RBAC_INCORRETO | 1 |
| CSRF_INCORRETO | 0 |
| SEO_DIVERGENTE | 4 |
| PERFORMANCE_DEGRADADA | 4 |
| RISCO_DE_SEGURANCA | 1 |
| LEGADO_INDEVIDO | 6 |
| REQUER_DECISAO_DO_PROPRIETARIO | 11 |

## 3. Decisões aplicáveis do SDD

1. Paridade funcional da produção é obrigatória; função não pode ser removida, ocultada, renomeada, fundida ou simplificada sem autorização expressa.
2. Backend é fonte dos dados; falha não pode ser convertida em `{}`, lista vazia, número zero ou conteúdo inventado.
3. Contratos públicos canônicos ficam em `/api/public`; administração em `/api/admin`; sem aliases do clone.
4. Sessão pública usa `JSESSIONID`, `credentials: include` e CSRF; propriedade é derivada da sessão.
5. Mídias pendentes/rejeitadas/restritas e documentos KYC não recebem URL pública; R2 é o único storage.
6. Ledger é fonte imutável do saldo; Premium vigente produz efeito e expirado perde integralmente o efeito sem apagar mídia.
7. Slugs, primeira publicação, canonical e URLs indexadas devem ser preservados.
8. Pré-produção permanece `noindex` e `Disallow: /` até decisão de cutover.
9. Stories administrativos devem estar dentro de Anúncios admin, durar 24 horas exatas e, junto aos pagos, ter ordem pública aleatória; mídia e histórico não são apagados ao expirar.
10. Efí continua desabilitada até gate de cutover; sem mock ou pagamento simulado.

## 4. Divergências internas dos documentos

- Documentos iniciais de skeleton/local admitem fallback neutro; decisões posteriores e este gate absoluto proíbem fallback e exigem fonte real.
- O SDD consolidado anterior descreve Story administrativo primeiro e ordem paga preservada; a decisão expressa desta auditoria exige aleatoriedade e 24h exatas.
- Documentos antigos ainda descrevem runner/fixture e deploy HML; o runtime HML foi removido e o CI foi convertido para pré-produção.
- Textos iniciais tratam Efí como integração a ativar; decisões posteriores congelam `EFI_ENABLED=false` até cutover.
- Baselines Flyway V017/V026 aparecem em histórico; a linha vigente é V028. Migrations históricas permanecem imutáveis.
- Documentos de transplante visual exigem paridade; documentos posteriores registram remoção de blocos de confiança/segurança. A autorização de remoção precisa ficar inequívoca.

## 5. SEO — prioridade máxima

### Evidência positiva

- Home e rotas de estado/cidade/bairro/detalhe entregam conteúdo principal por SSR.
- Title, H1 e canonical seguem o padrão geográfico principal nas amostras.
- `/sitemap.xml` retornou 754 URLs: 114 anúncios e 627 URLs de localidades.
- `/api/public/seo/sitemap` retornou 114 entradas e não consulta detalhe por slug.
- `robots.txt` mantém `Disallow: /`; páginas e sitemap mantêm `X-Robots-Tag: noindex, nofollow, noarchive` na pré-produção.
- Categorias da Home são cinco registros reais e vinculados à taxonomia canônica.

### Divergências bloqueadoras

1. Ordenação pública não implementa a regra decidida: V3 usa `topoAtivo DESC, publicadoEm DESC, id`, sem randomização separada de pagos e gratuitos.
2. Listagem carrega candidatos, filtra/ordena em Java e só então usa `subList`; há risco crescente de TTFB, memória e crawl.
3. Cidade Goiânia diverge em ordem, views e mídia; muitos cards mostram “Galeria em atualização”.
4. `ItemList` e detalhe amostrado usam o logotipo institucional como imagem social/fallback, em vez da foto pública real exibida na produção.
5. O WebPage JSON-LD existe no detalhe V3, mas `primaryImageOfPage` é omitido quando a API não entrega mídia LIVRE; `og:image` então cai no logotipo.
6. Blog responde com shell/placeholder e “Sem categorias publicadas”; endpoints estão ausentes e o erro vira lista vazia.
7. Sobre/Contato/FAQ usam conteúdo padrão e metadata genérica por falta de `site-content`/FAQ backend.
8. Cache público efetivo está degradado: respostas amostradas são `private, no-cache, no-store`, apesar de TTL/tag seletiva em partes da Home.
9. `/anuncios` com parâmetro `categoria` calcula o contexto da categoria, mas gera title genérico e não renderiza H1.
10. Estado/cidade inserem texto programático fixo/fallback com afirmações não fornecidas pelo agregado backend.
11. Titles/canonical geográficos e title/H1 do detalhe foram preservados nas amostras, mas conteúdo real, imagem, ordenação e editorial impedem equivalência SEO global.

## 6. Funções da produção ausentes ou incompletas

- Minha Conta: segurança/2FA, exclusão e conjunto amplo de perfil.
- Meus Anúncios: pausar, reativar/publicar, excluir, impulsionar e adicionar Story no mesmo nível da produção.
- Créditos e checkout completos da produção; Efí está corretamente fail-closed, mas a função visual não é equivalente.
- Suporte, tickets, chat e anexos.
- Performance do anunciante e progresso do wizard.
- Administração de usuários e equipe.
- Blog público/admin, categorias e conteúdo programático.
- FAQ, avisos e conteúdo institucional administrável.
- Compliance administrativo.
- Denúncias e sugestões.
- Indicações.
- Logs gerais da produção; `/admin/registros` não preserva o contrato `/logs`.
- Tela integral de Benefícios Premium.
- Ações completas de Anúncios admin/Moderação V2, inclusive lote, ciclo de vida, logs e Stories embutidos.

## 7. Contratos backend ausentes

Famílias ativas no frontend sem controller/OpenAPI V3:

- `/suporte/*`, `/chat/*`, `/ws-suporte`;
- `/wizard-progress/*` e `/admin/wizard-progress/*`;
- `/blog-posts/*`, `/blog-categorias/*`, `/blog-programmatic/*`;
- `/site-content/*` e `/admin/site-content/*`;
- `/estatisticas/*` e dashboard legado;
- `/admin/premium-benefits/*`;
- `/anuncios/staff/*` e ações administrativas legadas de anúncio;
- `/localidades/estados` e contratos por IDs legados;
- `/usuarios/*` e `/staff/*`;
- `/denuncias/*`, `/sugestoes/*`, `/financeiro/*`, `/indicacoes/*`;
- `/admin/compliance/*` e compliance de visitante;
- `/logs`, `/faq/*`, `/avisos/*`, `/creditos/ranking`.

## 8. Contratos frontend incompatíveis

- Clique WhatsApp monta `/api/public/api/public/anuncios/{slug}/clique-whatsapp`.
- Confirmação reforçada de idade possui ocorrência que monta `/api/public/api/public/idade/confirmar`.
- Criação do wizard chama `PUT /usuarios/{email}/editar`, ausente, antes do fluxo canônico em certas alterações de perfil.
- UIs administrativas continuam ligadas a contratos do clone, mesmo quando o backend V3 possui contratos canônicos com outros caminhos/DTOs.
- Bean Validation retorna 422 pelo handler global, enquanto decisões de contratos canônicos exigem 400.
- Backend possui uma operação não publicada no OpenAPI: `POST /api/admin/outbox/{id}/auth-test-code`.

## 9. Falsos vazios e fallbacks

- Blog e programático convertem resposta não OK/JSON inválido em `[]`.
- Sitemap frontend tem catch que retorna `[]` em falha, embora o contrato ao vivo esteja funcional.
- Dashboard/sidebar convertem falha em contadores zero/listas vazias.
- Tickets, grids e filtros zeram dados após erro.
- `site-content` devolve textos padrão; `cidadeSeo` fabrica conteúdo SEO de fallback.
- Categorias de filtros podem virar lista vazia silenciosa.

## 10. Área do anunciante

### Estruturalmente forte/novo na V3

- Auth público por sessão; logout com remoção explícita do cookie.
- Perfil básico com campos autorizados.
- Listagem/detalhe próprio e edição pelo wizard único.
- Upload/gestão de mídia com R2, limites e moderação individual.
- KYC documental no wizard e fila administrativa privada.
- Ledger, saldo, catálogo Premium e compra idempotente com créditos.
- Favoritos canônicos.

### Não equivalente

- Ações de ciclo de vida de Meus Anúncios ausentes.
- Minha Conta reduzida de 51 para 8 controles.
- Suporte/chat/performance/progresso sem backend.
- Créditos/checkout da produção sem experiência correspondente.
- Criação do anúncio tem chamada legada e sequência multi-request não atômica.
- Não houve sessão autenticada V3 disponível nesta execução para cumprir o gate de ação real ao vivo.

## 11. Painel administrativo completo

O painel conserva grande parte da superfície visual, mas não é funcionalmente equivalente. Categorias da Home e créditos usam contratos V3 reais; KYC, mídia, Premium, pagamentos e desempenho possuem núcleos backend novos. Entretanto usuários, equipe, blog, FAQ, compliance, suporte, denúncias, sugestões, indicações, avisos, conteúdo e logs gerais permanecem conectados a endpoints inexistentes. Dashboard usa falsos zeros. A mera abertura das rotas não satisfaz o critério de aprovação.

## 12. Anúncios admin e Moderação V2

- Produção: 57 controles em Moderação V2 e 96 no detalhe administrativo.
- V3: 35 e 81, respectivamente.
- Backend V3 possui leitura sanitizada, fila/moderação, mídia e documentos.
- Frontend ainda mistura `/api/admin/*` canônico com `/anuncios/staff/*` e ações legadas.
- Não há paridade de ações em lote, ciclo de vida, logs, benefícios e integração dos Stories.
- Story administrativo permanece em rota própria, sem expiração automática de 24h e sem ordem aleatória pública.

## 13. Segurança

### Controles adequados

- `SecurityConfig` mantém CSRF, deny-all para `/api/**` desconhecido e separação de sessão pública/admin.
- Serviços de anúncio próprio derivam usuário da sessão.
- R2/KYC/mídias usam minimização e visibilidade individual; documentos permanecem privados.
- Ledger é imutável e operações críticas são transacionais/idempotentes.

### Riscos

- Endpoint `auth-test-code` existe no backend sem OpenAPI e exige revisão de necessidade/exposição.
- Stories administrativos usam papel ADMIN em vez da granularidade de autoridade dos demais domínios.
- Telas herdadas podem induzir operador a acreditar que uma ação protegida foi executada quando o contrato não existe.
- Validação 422/400 divergente pode quebrar tratamento explícito e preservar dados incorretamente no cliente.
- Nenhuma função autenticada V3 recebeu aprovação ao vivo nesta auditoria.

## 14. Estabilidade e desempenho

- Catálogo público materializa e ordena todos os candidatos em memória antes de paginar.
- Serviços administrativos de consistência/performance usam `findAll` em movimentos, pagamentos, ativações ou eventos.
- Sidebar consulta cinco endpoints mortos a cada 30 segundos.
- Suporte tenta polling/WebSocket sobre contratos ausentes.
- `/auth/me` é chamado por múltiplos bootstraps.
- Cache compartilhado público não aparece efetivo nos headers amostrados.
- Fluxo create do wizard é composto por criação, patch e uploads; falha intermediária pode deixar estado parcial.
- Fallbacks/listas vazias escondem 404 e indisponibilidade, prejudicando observabilidade e retry correto.

## 15. Funções novas V3 preserváveis

- Login administrativo dedicado.
- Detalhe próprio `/meus-anuncios/[slug]`.
- Wizard único create/edit com cache isolado.
- Mídias R2, limites backend e remoção lógica.
- KYC documental no wizard com R2 privado e moderação.
- Ledger imutável, ajustes, estorno e migração de saldo inicial.
- Catálogo/compra Premium atômica e expiração integral.
- Categorias Home ligadas à taxonomia canônica com cache seletivo.
- Contratos públicos V3 de catálogo/localidades/sitemap sem N+1.
- Pré-produção isolada e importadores idempotentes/parametrizados.

## 16. Decisões exigidas do proprietário

1. Resolver a regra canônica de Stories entre documentos antigos e a decisão expressa de 24h/aleatoriedade/integração em Anúncios admin.
2. Ratificar remoção ou restauração dos blocos de confiança/segurança e diferenças de rodapé.
3. Definir reintrodução do 2FA na V3 após a migração que o desativa.
4. Definir gate real da Efí e tratamento dos pagamentos não conciliados.
5. Tratar Premium incompatível em quarentena.
6. Confirmar conteúdo/imagens finais das cinco categorias configuradas manualmente.
7. Autorizar o plano de preservação integral de Blog, FAQ, avisos, site-content, Compliance, suporte e demais módulos ausentes.
8. Definir o momento exato de troca de robots/canonical/sitemap no cutover.
9. Ratificar a exclusão de Stories legados diante da regra absoluta de preservação funcional/histórica.
10. Decidir se `auth-test-code` deve existir fora de ambiente de teste e documentá-lo/removê-lo em fase própria.

## 17. Ordem recomendada de correção

1. Gate de segurança: revisar `auth-test-code`, RBAC de Stories, 400/422 e confirmar CSRF de todas as mutações.
2. SEO crítico: ordenação paga/gratuita aleatória, paginação no banco, mídia real em cards/og:image/JSON-LD e cache SSR.
3. Corrigir caminhos duplicados e adapters canônicos já existentes.
4. Restaurar Anúncios admin/Moderação V2 completos e integrar Stories no módulo com 24h/aleatoriedade.
5. Completar Meus Anúncios/Minha Conta e tornar a finalização create resiliente/idempotente.
6. Implementar contratos reais de suporte/chat/performance/progresso.
7. Restaurar Blog/editorial/FAQ/site-content/avisos com SEO e sem fallback.
8. Restaurar usuários/equipe/Compliance/denúncias/sugestões/indicações/logs.
9. Integrar as telas administrativas aos contratos V3 já existentes de Premium, pagamentos, mídia, KYC e desempenho.
10. Remover legado somente após cada substituição ponta a ponta e repetir auditoria autenticada desktop/mobile.

## 18. Evidências e limitações

- Matriz detalhada: `docs/v3/evidencias/paridade/matriz-funcional-integral.json`.
- Evidências temporárias de extração e folhas de contato ficaram em `C:\topsv3-auditoria-local` e não fazem parte do Git.
- Nenhuma senha, cookie, token, documento, URL assinada, object key privada ou dado pessoal foi lido ou registrado.
- Produção, pré-produção, bancos, R2, Nginx e serviços foram mantidos sem escrita.
- O ambiente `v3.esle.cloud` continua sendo pré-produção com noindex; resultados não autorizam cutover.
