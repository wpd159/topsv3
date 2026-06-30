# Critérios de aceite da V3

## Objetivo

Definir critérios objetivos para considerar a V3 pronta para produção. A V3 só deve virar quando todos os gates críticos estiverem aprovados ou quando uma exceção formal, documentada e aceita existir.

## Dados

Aceite:

- usuários importados com mapeamento legado -> V3;
- anúncios importados com status coerente;
- slugs preservados sempre que possível;
- duplicidades suspeitas registradas;
- pendências de importação classificadas;
- nenhuma correção silenciosa;
- dados sensíveis fora do repositório e dos artefatos;
- contagens reconciliadas.

Bloqueia:

- perda de entidade crítica sem decisão;
- usuário/admin importado com credencial insegura;
- dados pessoais em artefato;
- relatório de importação ausente.

## URLs antigas funcionando

Aceite:

- `/anuncios/[slug]` responde conforme esperado;
- `/acompanhantes/[uf]/[cidade]` responde conforme esperado;
- `/acompanhantes/[uf]/[cidade]/[bairro]` responde conforme esperado;
- `/sitemap.xml` responde;
- `/robots.txt` responde;
- URLs prioritárias antigas mantem 200 ou 301 correto;
- nenhum redirect prioritário tem cadeia/loop.

## Anúncios ativos com mídia válida

Aceite:

- anúncio ativo/publicável tem mídia real quando a regra exigir;
- capa válida;
- galeria ordenada;
- story usa `story_anuncio.anuncio_midia_id` único e canônico;
- `anuncio_midia.tipo` e `anuncio_midia.finalidade` são `STORY` para stories;
- mídia quebrada relatada;
- placeholder não tratado como foto real;
- documentos privados não expostos.

## Premium auditável

Aceite:

- catálogo de benefícios ativo;
- durações por benefício configuradas;
- ativações com origem, ator, início, fim e snapshot;
- revogações auditadas;
- `ANUNCIO_TOPO` testado;
- `POSICAO_GARANTIDA_TOP20` explícito e com limite de inventário;
- nenhum benefício visual cria prioridade escondida.

## Créditos conciliados

Aceite:

- razão de créditos criada;
- saldo projetado bate com movimentos;
- pagamentos aprovados conciliam com entradas;
- ajustes manuais auditados;
- idempotência testada;
- divergências financeiras zeradas; pendências não financeiras somente poderão ser aceitas mediante decisão formal e sem impacto nos gates críticos.

Bloqueia:

- crédito duplicado por webhook;
- saldo negativo indevido;
- pagamento aprovado sem movimento;
- movimento sem origem.
- qualquer divergência financeira em créditos, pagamentos, conciliação Efí, movimentos de crédito, saldos ou ativações premium com custo financeiro.

## Pix Efí pronto

Aceite:

- Efí Bank/Efí Pay é o provedor Pix ativo inicial;
- Mercado Pago não aparece como dependência nova ou fluxo ativo;
- cobrança Pix e criada em homologação;
- QR Code e Pix cópia e cola são válidos;
- consulta de cobrança funciona;
- webhook Efí e validado conforme mecanismo oficial;
- webhook duplicado não duplica crédito;
- consulta duplicada não duplica crédito;
- pagamento aprovado gera um único movimento de crédito;
- falha na Efí não corrompe saldo;
- credenciais e certificado não estão no repositório, frontend, JAR, imagem Docker ou ZIP de entrega;
- CPF, QR Code, cópia e cola, token, client secret e certificado não aparecem nos logs;
- conciliação produz relatório;
- modo local funciona sem credenciais reais.

Bloqueia:

- crédito concedido apenas por payload público não confirmado;
- `txid`, idempotency key ou evento de provedor sem controle de unicidade;
- modo mock com risco de acessar produção;
- uso de nomenclatura central herdada do Mercado Pago.

## Sitemap limpo

Aceite:

- contém apenas URLs canônicas sem `www`;
- contém apenas páginas 200 e indexáveis;
- exclui admin, staging, checkout e APIs;
- foi validado por crawler;
- está referenciado no robots de produção.

## Canonical correto

Aceite:

- canonical sempre usa `https://topsdojob.com`;
- canonical não aponta para redirect;
- canonical não aponta para noindex;
- `www` redireciona para sem `www`;
- Open Graph/JSON-LD/IndexNow usam domínio correto.

## Home funcionando

Aceite:

- home carrega sem erro crítico;
- banner desktop 1452 x 500 e mobile 1080 x 900 disponíveis;
- banner editável pelo admin;
- links principais funcionam;
- metadados básicos corretos;
- performance medida conforme `DECISAO_PENDENTE: ADR-009`, a fechar antes da Fase 7.

## Busca funcionando

Aceite:

- busca por texto;
- filtros por UF/cidade/bairro;
- paginação;
- ranking explicável;
- sem fallback de carregar tudo em memória;
- resultados respeitam status do anúncio;
- resultados respeitam premium sem prioridade escondida.

## Moderação funcionando

Aceite:

- fila de pendentes;
- detalhe do anúncio;
- revisão de mídia;
- aprovar/rejeitar/solicitar ajuste;
- motivo de decisão;
- auditoria;
- permissões de MODERADOR testadas.

## Dashboard funcionando

Aceite:

- visão administrativa carrega;
- usuários/anúncios/premium/créditos/financeiro acessíveis conforme permissão;
- métricas administrativas definidas por `DECISAO_PENDENTE: ADR-009`, a fechar antes da Fase 7, aparecem conforme permissão;
- erros são tratados;
- ações críticas são auditadas.

## Login/admin seguro

Aceite:

- login testado;
- autenticação web por sessão server-side testada;
- sessão opaca gerenciada pelo backend;
- sessão revogável por usuário, dispositivo e administrador;
- cookie `HttpOnly`, `Secure` fora do ambiente local e `SameSite=Lax` por padrão;
- nenhum token principal de autenticação do navegador no `localStorage`;
- JWT não é mecanismo principal da autenticação web;
- rotação de sessão após login e elevação de privilégio;
- expiração absoluta e por inatividade;
- ADMIN, MODERADOR e COMERCIAL usam senha mais token por e-mail na versão inicial;
- token por e-mail com hash, expiração, tentativas e consumo único;
- rate limit;
- CSRF/CORS definidos;
- roles ADMIN/MODERADOR/COMERCIAL testadas;
- senha/hash/token nunca expostos.

## Importação com relatório confiável

Aceite:

- execução registrada;
- relatório gerado;
- códigos obrigatórios presentes;
- mapa legado -> V3;
- mapa URL atual -> V3;
- manifesto de mídia;
- reconciliação de créditos/pagamentos/premium;
- duas execuções reproduzíveis antes da virada.

## Backup testado

Aceite:

- backup PostgreSQL;
- backup de mídia;
- backup de configurações;
- backup de mapa SEO;
- backup de releases;
- logs essenciais preservados;
- checksum validado;
- criptografia aplicada aos dados sensíveis;
- restauração testada em ambiente isolado.

## Rollback disponível

Aceite:

- runbook aprovado;
- release anterior disponível;
- banco legado preservado;
- backup final verificado;
- reversao de proxy/symlink/DNS documentada;
- smoke tests definidos;
- critérios de rollback claros;
- responsáveis nomeados.

## Segurança e operação

Aceite:

- nenhum risco crítico aberto;
- health/readiness/liveness disponíveis;
- logs estruturados;
- alertas ativos;
- webhook financeiro protegido;
- upload validado;
- secrets fora do repositório;
- deploy não pula testes obrigatórios.

## Go/no-go

A decisão de go deve responder:

- Dados reconciliados?
- SEO preservado?
- URLs prioritárias funcionando?
- Admin seguro?
- Pagamento/créditos confiáveis?
- Mídia válida?
- Backup restaurável?
- Rollback ensaiado?
- Operação comercial preparada?

Se qualquer resposta crítica for "não", a V3 não deve virar.
