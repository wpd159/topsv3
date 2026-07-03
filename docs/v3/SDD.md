# SDD Tops do Job V3

Documento central de Specification-Driven Development da V3. Ele consolida o estado local do projeto ate o Bloco 26.2 e aponta para documentos de detalhe, contratos, evidencias e gates.

## 1. Visao geral

A V3 e uma reconstrucao local e controlada do Tops do Job, com foco em preservar o comportamento publico correto da producao atual, reduzir risco operacional e preparar uma evolucao auditavel.

Direcao do produto:

- dominio publico alvo: `topsdojob.com`;
- foco em liquidez e base de anuncios antes de receita imediata;
- preservacao do que ja funciona em producao;
- local antes de staging/homologacao/producao.

Estado atual:

- ambiente 100% local;
- backend Spring Boot com dominio, persistencia JPA, API publica local e admin local;
- frontend Next.js com rotas publicas preservadas, admin local e funil publico `/anunciar`;
- migrations Flyway V001 a V017 criadas e validadas estaticamente;
- PostgreSQL descartavel usado para validacoes locais;
- dados reais, producao, VPS, banco de producao, Efi real e APIs externas fora de uso.

## 2. Escopo e limites

Escopo atual:

- rotas publicas preservadas;
- leitura publica local;
- metricas publicas locais;
- confirmacao local de idade;
- admin local com sessao/RBAC;
- moderacao local minima;
- outbox local e preview sanitizado;
- Premium, creditos, pagamentos e desempenho em leitura local;
- funil local "Anuncie gratis" em wizard progressivo para criar solicitacao nao publica.

Limites permanentes ate aprovacao futura:

- nao usar dados reais;
- nao acessar producao como bancada de teste;
- nao executar deploy;
- nao executar push;
- nao configurar remote sem autorizacao;
- nao usar banco de producao;
- nao usar Efi real;
- nao iniciar importador real;
- nao criar nova migration sem fase expressa.

## 3. Arquitetura local

A arquitetura local esta organizada em:

- `backend/`: Spring Boot, dominio, persistencia JPA, aplicacao e web;
- `frontend/`: Next.js, rotas publicas, admin e cliente API local;
- `infra/local/`: compose e variaveis locais;
- `scripts/local/`: validacoes, e2e descartavel e operacao local;
- `scripts/security/`: scanners de codificacao, arquivos proibidos e secrets;
- `contracts/openapi/`: contrato OpenAPI local;
- `docs/v3/`: SDD, runbooks, decisoes, checklists e evidencias.

## 4. Rotas publicas e SEO

Rotas publicas preservadas:

- `/`;
- `/anuncios/[slug]`;
- `/acompanhantes/[uf]/[cidade]`;
- `/acompanhantes/[uf]/[cidade]/[bairro]`;
- `/anunciar`;
- paginas institucionais locais.

Regras:

- `/anuncios/[slug]` e o contrato publico absoluto da pagina de anuncio;
- rotas alternativas como `/perfil`, `/ads`, `/anuncio` e `/acompanhante` permanecem proibidas;
- ambiente local usa `noindex`;
- canonical de producao nao deve ser emitido em ambiente local;
- textos publicos nao devem expor termos internos como "skeleton" ou "API local";
- SEO final depende de revisao humana antes de homologacao/producao.
- SEO e prioridade central da V3 e possui documentos proprios em `docs/v3/SEO-*.md`.
- O alvo de crescimento organico local e `acompanhante em [cidade]`.

## 5. Classificacao, idade e conteudo bloqueado

A classificacao publica da V3 e binaria:

- `LIVRE`: pode ser exibido publicamente;
- `BLOQUEADO`: nao aparece como anuncio publico normal; detalhe, stories e contato so podem ser avaliados apos confirmacao de idade pelo backend e conforme politica de midia/contato.

Regras:

- frontend nao decide classificacao;
- backend e a fonte da decisao;
- `LIVRE` aparece sem confirmacao de idade;
- `BLOQUEADO` depende de confirmacao de idade pelo backend para qualquer liberacao controlada;
- stories exigem confirmacao de idade;
- nao existe gradacao publica intermediaria;
- nao existe age gate intermediario por categoria;
- nao existe blur por categoria intermediaria;
- confirmacao de idade local usa cookie HttpOnly assinado, sem CPF, documento, conta, localStorage ou sessionStorage.

## 6. WhatsApp, contato e metricas

WhatsApp publico e sempre mediado pelo backend.

Regras:

- frontend nao renderiza WhatsApp bruto por conta propria;
- clique de WhatsApp passa pelo endpoint publico local;
- metricas minimizam IP, User-Agent e referer por hash;
- gratuito continua util e nao recebe limite comercial artificial de clique, contato ou WhatsApp;
- conteudo `BLOQUEADO` nao pode liberar WhatsApp publico como anuncio normal.

## 7. Premium e beneficios

Premium e aditivo e preserva o gratuito util.

Regras:

- Premium nao limita o gratuito artificialmente;
- beneficios sao leitura/calculo local nesta etapa;
- `/admin/premium` pode exibir preview local de combinacao de beneficio/periodo sem efeito real;
- nao ha compra, checkout, cobranca, Pix/Efi funcional ou ativacao real por dinheiro;
- dados Premium locais ficam sinteticos e fora de migrations.

## 8. Creditos, pagamentos e Efi

Creditos, ledger, pagamentos e Pix/Efi estao em leitura local ou mock seguro.

Regras:

- nenhum Pix real;
- nenhuma conciliacao real;
- nenhum webhook real;
- nenhum QR Code ou copia e cola real;
- nenhum valor sensivel bruto em DTO admin;
- creditos sao inteiros;
- dinheiro usa tipo numerico/BigDecimal;
- Efi e o provedor ativo futuro;
- Mercado Pago e legado;
- tabelas legadas de Mercado Pago podem conter evidencias Efi e o provedor deve ser decidido por evidencia;
- Efi real permanece proibida ate fase expressa de homologacao com credenciais e revisao Pro.

## 9. Importador e fonte real

O importador esta preparado estruturalmente, sem uso real.

Regras:

- sem dump;
- sem arquivo real de entrada;
- sem importacao real;
- sem ETL real;
- sem acesso a banco;
- fonte real futura exige gate, autorizacao formal e revisao Pro.

## 10. Admin e RBAC

Admin local usa sessao/cookie e RBAC minimo.

Papeis:

- `ADMIN`;
- `MODERADOR`;
- `COMERCIAL`;
- `USUARIO`.

Regras:

- `/api/admin/**` exige sessao, exceto login;
- frontend admin usa `credentials: include`;
- sem localStorage/sessionStorage;
- sem credencial pre-preenchida;
- acoes criticas exigem permissao, auditoria e fase expressa.

## 11. Moderacao

Moderacao local minima cobre decisoes de anuncio e midia conforme blocos autorizados.

Regras:

- `REPROVAR` exige motivo;
- motivo e sanitizado e limitado;
- `SOLICITAR_AJUSTE` e intermediaria e nao finaliza revisao;
- auditoria mascara e-mail, contato e documento;
- auditoria JSON completa permanece pendencia Pro antes de homologacao/producao.

## 12. Outbox e comunicacoes

Outbox e local e seguro.

Estado atual:

- leitura admin de outbox;
- preview sanitizado;
- simulacao local de processamento apenas em `APP_ENV=local`;
- sem envio externo;
- sem worker;
- sem scheduler;
- sem SMTP externo;
- sem WhatsApp real.

## 13. Midia, storage e CDN

Midia publica continua restrita.

Regras:

- `urlPublica` permanece nula enquanto CDN/storage publico nao estiver aprovado;
- DTO publico nao expoe bucket, storage key, provider, hash, etag ou URL privada;
- documento privado nunca e midia publica;
- upload real esta fora do escopo atual;
- placeholders publicos devem ser neutros e nao podem simular midia real.

## 14. Visual, UI e mobile

A V3 preserva o visual atual do Tops do Job.

Melhorias leves permitidas:

- espacamento;
- alinhamento;
- hierarquia visual;
- contraste;
- legibilidade;
- responsividade;
- estados vazios e loading estavel;
- organizacao de CTA.

Proibido:

- redesign;
- nova identidade visual;
- nova paleta;
- nova tipografia;
- animacao automatica;
- floating button solto;
- scroll lock;
- `document.body.style.overflow`;
- elemento mobile solto, sobreposto ou fora do fluxo.

## 15. Seguranca

Seguranca do repositorio:

- scanners de codificacao, arquivos proibidos e secrets;
- bloqueio de dumps, backups, uploads, credenciais, certificados e archives;
- gitleaks opcional quando instalado, fallback local obrigatorio;
- ZIPs de revisao fora do Git;
- sem remote/push nas fases locais.

Seguranca de aplicacao:

- DTO publico sem entidade JPA exposta;
- logs sem dados sensiveis;
- request id transversal;
- admin por sessao local;
- deny-all para `/api/**` desconhecida.

## 16. Banco e migrations

Migrations V001 a V017 existem para auditoria e foram validadas localmente.

Estado:

- validacao SQL estatica OK;
- validacao PostgreSQL descartavel OK por SQL ordenado;
- Flyway CLI/imagem real ainda pendente quando nao disponivel localmente;
- schema segue dependente de revisao Pro antes de fases de homologacao/producao.

Nova migration ou alteracao de SQL de schema so pode ocorrer em fase expressa.

## 17. Ambientes

Ambientes previstos:

- local: permitido para desenvolvimento e validacao;
- staging/homologacao: futuro, depende de gates;
- producao: intocada nesta etapa.

Regras:

- `APP_ENV=local` somente em profile local;
- base default deve falhar fechada;
- producao nao e bancada de teste;
- consulta a producao, quando estritamente necessaria, deve ser somente leitura, documentada e sem exposicao de segredo.

Caminho correto:

1. local;
2. staging;
3. dry-run real autorizado;
4. homologacao validada;
5. producao com backup e rollback.

## 18. Criterios de go-live futuro

Antes de homologacao/producao:

- revisao Pro de schema;
- validacao Flyway real em ambiente descartavel;
- staging validado;
- backup e rollback documentados;
- importacao final validada quando houver fonte real;
- politica final de midia/CDN;
- politica final de retencao documental;
- auditoria JSON revisada;
- CSRF/admin revisado;
- secrets reais fora do Git;
- Efi homologada com credenciais seguras;
- testes e2e aprovados;
- plano de rollback;
- revisao SEO/visual final preservando producao atual.

## 19. Pendencias criticas

Pendencias principais:

- revisao Pro de schema;
- fonte real autorizada;
- Flyway real quando toolchain/imagem estiver disponivel;
- gitleaks instalado ou decisao formal de fallback;
- CDN/storage publico aprovado;
- politica juridica final de retencao;
- auditoria JSON Pro;
- CSRF admin para ambientes nao locais;
- fonte real autorizada para importador futuro;
- homologacao Efi futura;
- importacao real;
- SEO real;
- deploy/cutover;
- backup e rollback;
- revisao visual final com fonte atual da producao.

## 20. Historico de blocos

Historico resumido:

- Fases 0.x: SDD, protecoes Git, scanners e pacote;
- Fase 1A-1C: infraestrutura local, skeleton e contratos;
- Fase 1D: migrations V001 a V017;
- Fase 2A-2G: importador estrutural e gates de fonte real;
- Blocos 3-4: dominio e persistencia JPA;
- Blocos 5-10: API publica, frontend publico, e2e, metricas, idade e UX de conteudo bloqueado;
- Blocos 11-15: midia segura, auth/admin e read-only detalhado;
- Blocos 16-20: moderacao, outbox e templates;
- Bloco 21: paridade visual e mobile estavel;
- Blocos 22-25: Premium, creditos, pagamentos e prova de resultado read-only;
- Bloco 26: funil local Anuncie gratis;
- Bloco 26.1: correcao visual minima de `/anunciar` e consolidacao do SDD central;
- Bloco 26.2: wizard progressivo de `/anunciar`, Premium preview local e SEO central.

Detalhes e rastreabilidade ficam em `docs/v3/SDD-indice-rastreabilidade.md`.
