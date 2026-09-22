# Blog — reconciliação e correções focais

Base examinada: `cd49e006c2eb52e2b347ce791c2a1e50cd322da7`.
Escopo: blog editorial público/administrativo e avaliação da capacidade programática.
Nenhum post real foi criado, alterado, publicado ou removido. Sem merge/deploy nesta entrega.

## Achados reconciliados

| Achado / expectativa | Estado observado e evidência | Classificação / ação |
| --- | --- | --- |
| Auditoria Work de 14/09: três URLs `/blog/{tema}/{cidade}` redirecionavam para 404 | PR42 retirou os redirects; prova `nojs-retired-programmatic-blog-routes.json` do lote SEO interno de 16/09: seis destinos devolvem 404 direto. As rotas programáticas atuais continuam explicitamente indisponíveis. | Histórico já corrigido. Não inventar destinos ou páginas geográficas. |
| Work: aviso etário no blog em sessão limpa | Conteúdo/links editoriais continuam no HTML do servidor; o aviso segue as condições atuais de maioridade/consentimento, já aprovadas. O campo `AgeGate=True` de um crawler não prova modal visível. | Não demonstrado defeito atual. Preservar consentimento e F02. |
| Work: melhorar dois artigos de segurança/moderação com fontes e revisão humana | Sugestão editorial, sem afirmação concreta incorreta demonstrada nesta revisão funcional. | Conteúdo editorial: preservar os corpos atuais; não produzir texto ou publicar por inferência. |
| Relato antigo de endpoints editoriais ausentes | Controllers/services editoriais existem desde `6f4871af`; listagem administrativa corrigida em `4c45df28`. | Histórico superado; não apresentar ausência antiga como regressão atual. |
| Editor: falha ao abrir post deve impedir edição | Depois de GET 404/503, o formulário antigo era montado com estado vazio. | Defeito confirmado. Erro de carregamento agora retorna sem formulário; retorno ao blog preservado. |
| Editor: rascunho criado/atualizado deve sobreviver à recusa da publicação | O ID/versão retornados por POST/PUT só entravam no estado depois do segundo POST de publicação. Se este falhava, nova tentativa podia recriar o post ou usar versão antiga. | Defeito confirmado. Preservar imediatamente a resposta gravada; publicar continua operação distinta. |
| Erros de gravação não devem ser apresentados como carregamento | Editor, categorias e listagem reutilizavam o erro de carga e sua repetição. | Defeito confirmado. Mensagens locais de ação, sem recarregar/descartar o texto digitado. Componente de erro compartilhado inalterado. |
| Falha na atualização pública não desfaz uma gravação | A revalidação podia falhar depois de CREATE/PUT/status bem-sucedido; categoria perdia ID/versão e a listagem mantinha status antigo. | Defeito confirmado. Preservar resultado, explicitar gravação concluída e permitir repetir somente a revalidação, sem nova mutação. |
| Corpo HTML e links devem manter as entidades válidas | `<p>A &amp; B</p>` virava `&amp;amp;`; `href` com `&amp;` alterava parâmetros. | Defeito confirmado. Preservar referências como texto; normalizar atributo antes da allowlist e escapar ao emitir. Scripts/protocolos perigosos continuam bloqueados. |
| Frequência editorial configurada deve alcançar o sitemap | Editor e `BlogSitemapDto` possuem `changeFrequency`; montagem editorial do sitemap descartava o campo. | Defeito confirmado. Mapear somente valores válidos existentes; sem alterar URLs, critérios de inclusão ou os outros ramos do sitemap. |
| Listagem, categorias, links HTML e integralidade | A lista principal renderiza todos os publicados; limite de três é somente o bloco “Últimos posts”. Não há paginação editorial implementada. Corpo no banco é TEXT, limitado explicitamente a 200.000 caracteres, sem truncamento silencioso. | Correto no contrato examinado. Preservar estrutura e limites. |
| Não publicado/inexistente/slug antigo | Consultas públicas selecionam `PUBLICADO`; rascunho/arquivado ou slug que não existe retornam 404. Não há contrato de histórico/redirect de slug aprovado. | Comportamento existente preservado; não criar redirects novos. |
| Canonical, robots, schema e cache | Canonical usa slug atual; blog/categoria vazios têm noindex; categoria inexistente/inativa é 404; JSON-LD usa serializador central. Cache editorial de 300 s e tag `public-blog`; invalidação da tag cobre consultas, categorias, slug anterior e sitemap. | Sem divergência demonstrada nesses critérios. Não desativar cache ou alterar indexação. |
| Administração restrita | Controllers exigem `ROLE_ADMIN` e `ADMIN_CONFIGURAR`; API privada mantém credenciais e CSRF. | Preservar regras e comprovar negativas com testes específicos, sem dados reais. |

Fontes históricas: auditoria Work anexada em 14/09; relatório e evidências do PR42 em `tops-seo-interno-20260916`; fotografia pública em `topsdojob-live-reaudit-20260920` (20 URLs do blog, todas HTTP 200). Essa fotografia é histórica, não uma nova medição de produção.

O relato original de execução do Work sobre a área programática não foi localizado. Foi encontrado um comando histórico que menciona a lacuna; ele não é tratado como prova de execução. A classificação abaixo se baseia no código e no histórico disponíveis.

## Páginas programáticas: decisão pendente, não integração quebrada

`/admin/blog/programatico` usa `usePendingContractActions`: registra tentativas e informa contrato pendente, sem simular sucesso. `programmatic-blog-api.ts` lança erro explícito, sem transporte. Não existem controller, persistência ou migration programáticos; V037 cria somente categoria, imagem e post editoriais. As diretrizes futuras de `11-plano-execucao-fases.md` não definem geração ou contrato operacional aprovado.

Há vocabulários divergentes entre painel e cliente (por exemplo, massagens/sexo virtual versus garotas de programa/anúncios adultos). Não escolher um automaticamente.

Proposta para decisão posterior, antes de implementar:

1. Definir temas efetivamente oferecidos e fonte editorial autorizada; usar o editor convencional enquanto isso.
2. Definir identidade territorial/URLs, critérios de qualidade e conteúdo mínimo, sem duplicação geográfica para inflar indexação.
3. Confirmar revisão humana, responsável e estados de aprovação; geração não deve equivaler a publicação.
4. Definir contrato de criação/reprocessamento: identificador idempotente, versão/concorrência, limites de lote, cancelamento e resultado parcial.
5. Só então especificar endpoints/persistência e regressões. Nenhum botão será habilitado apenas para esconder “Integração pendente”.

## Provas e limites

- Execução focal backend offline: 15 testes, zero falhas/erros/skips (MockMvc/serviço/validação/imagem); nenhum Maven completo local.
- Linux Node 22.13.1, Playwright 1.62.1 e Chromium 151: 11 cenários do editor aprovados; baseline anterior identificou defeitos em nove cenários e foi registrado como observação, não aprovação. Todos os contextos, browser, servidor e compiler encerrados.
- Renderer/JSON-LD: aprovado, incluindo links e negativas de XSS. Estados públicos: 29 casos aprovados. Acesso/cache público: nove casos aprovados. Os contratos públicos já existentes foram reutilizados, sem criar suíte paralela.
- Uma rodada do auxiliar local falhou por não montar os arquivos Compose consultados pelo contrato; apenas essa montagem TEMP foi corrigida. Outra rodada encontrou a mensagem genérica de carregamento no erro 503 de gravação: a causa foi corrigida no cliente administrativo do blog, preservando classificação/status/requestId. Evidências vermelhas mantidas, sem push de hipóteses.
- Harness existente do blog ampliado para componentes React reais, cliente administrativo, prévia e renderer; transporte HTTP e ação de revalidação substituídos por fronteiras sintéticas explícitas.
- Baseline identificado e resultados anteriores preservados; regressões posteriores falham diante de falso sucesso, perda de ID/versão/texto ou repetição indevida da mutação.
- Desktop e emulação móvel com toque; não é teste em Android físico. Sem SDK/eventos GA4 ou acesso a serviços pagos.
- Negativas backend específicas de autorização e seleção pública; não confundir mocks/serviço com persistência real PostgreSQL.
- Contratos públicos existentes cobrem 404/noindex/canonical/sitemap/cache. Não há nova auditoria/crawl produtivo.
- O validador histórico `validar-rotas-publicas-seo-local.ps1` foi executado antes do ajuste editorial: 24/40 verificações aprovadas; 16 referem-se ao skeleton antigo (caminhos sem route group, `localUrl`, antigos helpers e proibição histórica de SQL fora das migrations). Não foi alterado nem apresentado como verde; os gates atuais do projeto continuam obrigatórios.
- Resultados reais, comandos, capturas, hashes, diff e CI ficam no pacote de revisão e nas evidências desta entrega. Um CI anterior não aprova os bytes novos.
- Nove textos institucionais, FAQ, GA4, P1/P2, mídia, pagamentos e regras do catálogo permanecem fora do delta.
