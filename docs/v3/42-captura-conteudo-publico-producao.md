# Captura pública somente leitura da produção

## Objetivo

Registrar um snapshot local, sanitizado e versionável dos textos SEO públicos atualmente disponíveis em produção para análise futura da V3.

Esta etapa é somente leitura. Ela não altera produção, não faz login, não usa credenciais, não acessa admin, banco, VPS, Efí, IA externa ou endpoints privados.

## Escopo permitido

Domínio permitido:

```text
https://topsdojob.com
```

Método permitido:

```text
GET
```

Rotas públicas permitidas:

- `/`;
- `/sobre`;
- `/como-funciona`;
- `/seguranca`;
- `/anunciar`;
- `/perguntas-frequentes`;
- `/acompanhantes/[uf]/[cidade]`;
- `/acompanhantes/[uf]/[cidade]/[bairro]`;
- `/anuncios/[slug]`;
- `/sitemap.xml`;
- `/robots.txt`.

## Escopo proibido

Não acessar:

- `/admin`;
- `/login`;
- `/api`;
- `/dashboard`;
- `/checkout`;
- `/pagamento`;
- `/webhook`;
- `/moderacao`;
- rotas autenticadas;
- painéis;
- endpoints internos;
- URLs com token;
- documentos privados;
- imagens e vídeos privados;
- dados de usuário não públicos.

## Limites de captura

Limites aplicados pelo script local:

- máximo inicial de 30 URLs;
- intervalo mínimo padrão de 2 segundos;
- timeout curto;
- user-agent identificável como auditoria local do próprio projeto;
- bloqueio de caminhos privados antes da requisição;
- interrupção em caso de erros 403, 429 ou 5xx repetidos;
- captura sem baixar imagens, vídeos ou sub-recursos.

## Sanitização

O script grava apenas artefatos sanitizados em:

```text
docs/v3/conteudo-publico-capturado/
```

Campos permitidos:

- URL pública;
- status HTTP;
- content type;
- tipo de página;
- title;
- meta description;
- H1;
- headings;
- texto principal sanitizado;
- observações SEO;
- decisão preliminar.

Campos e conteúdos proibidos:

- telefone;
- WhatsApp;
- CPF;
- e-mail pessoal;
- token;
- imagem;
- vídeo;
- documento;
- dado privado;
- conteúdo explícito;
- HTML bruto completo;
- payload bruto.

Quando há risco de conteúdo sensível, o texto principal é substituído por:

```text
CONTEUDO_SENSIVEL_NAO_CAPTURADO
```

## Como repetir

Simular plano sem capturar:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/capturar-conteudo-publico-producao.ps1
```

Executar captura pública limitada:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/capturar-conteudo-publico-producao.ps1 -ExecutarCaptura
```

## Revisão antes de reaproveitar

Nenhum texto capturado deve ser promovido automaticamente para a V3.

Antes de reaproveitar:

- revisar conteúdo por humano;
- confirmar se há dado sensível removido;
- adaptar linguagem para SEO/GEO/AEO;
- preservar URLs canônicas;
- verificar se a página pode ser indexável;
- descartar conteúdo fraco, duplicado ou sensível.

## Riscos

- páginas públicas podem conter contato, texto sensível ou conteúdo inadequado para captura;
- sitemap pode listar volume grande de URLs;
- páginas locais e anúncios podem misturar SEO com conteúdo que exige revisão;
- produção pode bloquear captura por rate limit;
- texto público atual pode precisar de reescrita para V3.

## Resultado desta execução

A captura foi executada com `-ExecutarCaptura` de forma limitada.

Foram capturadas 4 URLs públicas:

- `https://topsdojob.com/`;
- `https://topsdojob.com/sobre`;
- `https://topsdojob.com/sitemap.xml`;
- `https://topsdojob.com/robots.txt`.

Foram ignoradas URLs institucionais ausentes, uma rota com redirecionamento fora do escopo permitido e amostras de sitemap por risco de telefone, conteúdo sensível ou volume.
