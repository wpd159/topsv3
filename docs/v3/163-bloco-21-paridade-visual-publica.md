# Bloco 21 - paridade visual publica local

## Objetivo

O Bloco 21 aplica uma primeira camada local de paridade visual publica para a V3, preservando rotas e comportamento atual sem transformar o skeleton em layout final.

Foram permitidas somente melhorias pequenas:

- espacamento;
- alinhamento;
- hierarquia visual;
- legibilidade;
- tamanho de toque no mobile;
- estados vazios;
- placeholders de midia com dimensao estavel;
- organizacao de CTA;
- responsividade;
- consistencia de cards;
- reducao de poluicao visual.

Nao houve redesign, nova identidade visual, nova paleta, nova tipografia, animacao automatica, carrossel automatico, componente flutuante, barra fixa ou alteracao estrutural nao aprovada.

## Consulta somente leitura a producao

A consulta SSH foi necessaria porque `docs/v3/48-preservacao-visual-atual.md` ainda registrava a fonte visual como pendente e o bloco exigia preservar o comportamento visual atual quando houvesse duvida.

Consulta realizada via `ssh topsdojob`, somente leitura:

```text
pwd; find /var/www -maxdepth 4 -type f ...
find /etc/nginx/sites-enabled /etc/nginx/conf.d -maxdepth 2 -type f ... grep server_name/root/alias
grep server_name/root/proxy_pass/location/try_files/index em configuracoes nginx de topsdojob
ps -eo pid,comm,args | grep node|next|pm2
readlink -f /proc/1245833/cwd
find no release Next atual para listar arquivos e diretorios publicos/compilados
find em .next/static e .next/server/app para identificar chunks CSS e rotas compiladas
```

Evidencias obtidas:

- a producao atual usa Next server;
- o processo identificado estava em release compilado sob `/var/www/topsdojob-frontend/releases/...`;
- havia assets publicos e rotas compiladas, mas sem fonte de frontend disponivel no release;
- o dominio `.com` estava servido por proxy local para Next;
- a consulta confirmou que a V3 deve seguir uma leitura publica de marketplace e manter rotas publicas conhecidas.

Nada foi alterado:

- nenhum arquivo foi editado em producao;
- nenhum servico foi reiniciado;
- nenhum deploy foi executado;
- nenhum `git pull` foi executado;
- nenhum SQL foi executado;
- nenhum banco foi acessado;
- nenhum `.env`, segredo, certificado, token ou senha foi impresso;
- nenhum dump, dado real ou midia real foi copiado.

## Implementacao frontend local

Componentes publicos criados em `frontend/src/modules/public/components`:

- `PublicHomeHero`;
- `PublicAnuncioCard`;
- `PublicAnuncioGrid`;
- `PublicLocalidadeHeader`;
- `PublicAnuncioDetalhe`;
- `PublicMidiaPlaceholder`;
- `PublicContatoAction`;
- `PublicStoriesGate`;
- `PublicEmptyState`;
- `PublicSeoTextBlock`.

Paginas preservadas e ajustadas:

- `/`;
- `/anuncios/[slug]`;
- `/acompanhantes/[uf]/[cidade]`;
- `/acompanhantes/[uf]/[cidade]/[bairro]`.

Rotas alternativas continuam proibidas:

- `/perfil`;
- `/anuncio/[id]`;
- `/acompanhante/[slug]`;
- `/ads`.

## Midia e placeholders

Nao foi usada imagem real, foto real, video real, storage real, CDN real ou URL publica real.

O placeholder local de midia:

- e neutro;
- fica dentro do fluxo normal da pagina;
- possui dimensao estavel via `aspect-ratio` e `min-height`;
- nao usa imagem externa;
- nao usa `position`;
- nao causa salto visual perceptivel;
- preserva `PENDENTE_URL_PUBLICA_MIDIA_CDN`.

## WhatsApp e contato

WhatsApp continua mediado pelo backend.

O frontend:

- nao decide liberacao de contato;
- nao imprime URL bruta de WhatsApp no HTML;
- nao monta link de WhatsApp por conta propria;
- nao usa storage, segredo, bucket, chave, hash ou URL privada;
- mostra apenas estado publico seguro quando o backend local autoriza contato.

## Conteudo BLOQUEADO, idade e stories

O frontend continua sem decidir classificacao.

O fluxo preserva:

- `LIVRE` e `BLOQUEADO` como modelo binario;
- confirmacao local de idade para conteudo bloqueado;
- reconsulta do backend apos confirmacao;
- stories protegidos por idade;
- ausencia de localStorage/sessionStorage;
- ausencia de age gate intermediario por categoria.

## Estabilidade mobile

Foram evitados:

- `document.body.style.overflow`;
- scroll lock;
- `position: fixed`;
- `position: absolute`;
- `position: sticky`;
- `100vw`;
- `@keyframes`;
- `animation`;
- `transform`;
- `translate`;
- carrossel automatico;
- elemento flutuante;
- CTA fora do fluxo.

Cards, CTAs, placeholders e textos longos usam:

- `max-width: 100%`;
- `overflow-wrap`;
- `grid` responsivo;
- `minmax(min(100%, ...), 1fr)`;
- dimensoes estaveis;
- botoes com area minima de toque.

## Validacoes do bloco

Resultado final da validacao mobile estatica:

```text
VALIDATION_RESULT=OK_UI_MOBILE_ESTATICA
```

Resultado final das demais validacoes:

- `diagnosticar-toolchain-local.ps1`: `OK_DIAGNOSTICO_TOOLCHAIN_LOCAL`;
- `validar-build-local.ps1`: `OK_BUILD_LOCAL`;
- `validar-e2e-local-descartavel.ps1`: `OK_E2E_LOCAL_DESCARTAVEL`;
- smoke HTTP da API publica local via E2E: `SMOKE_HTTP_OK=True`;
- `validar-persistencia-jpa-estatica.ps1`: `OK_PERSISTENCIA_JPA_ESTATICA`;
- `validar-rotas-publicas-seo-local.ps1`: 51/51 verificacoes OK;
- `validar-migrations-sql-estatico.ps1`: 27/27 verificacoes OK;
- `validar-fonte-importacao-local.ps1`: nenhuma fonte real informada;
- `verificar-codificacao.ps1`: OK apos normalizacao de BOM do script mobile;
- `verificar-arquivos-proibidos.ps1`: OK;
- `verificar-segredos.ps1`: fallback local OK, Gitleaks pendente por binario ausente;
- backend direto: compile/test OK com Maven local;
- frontend direto: `npm run lint` e `npm run build` OK;
- `git diff --check` e `git diff --cached --check`: OK;
- `git remote -v`: vazio.

## Fora de escopo preservado

Nao houve:

- migration;
- SQL de schema;
- banco persistente;
- dado real;
- midia real;
- upload;
- envio externo;
- pagamento;
- credito;
- Pix/Efi;
- OpenAI;
- API externa;
- importador real;
- nova acao admin;
- producao alterada;
- VPS alterada;
- remote;
- push;
- commit;
- inicio de fase posterior.

## Riscos residuais

- O release consultado em producao estava compilado; fonte visual completa ainda depende de material aprovado ou inventario visual mais detalhado.
- A CDN/midia publica real continua pendente de fase futura.
- A revisao visual final ainda depende de validacao humana antes de homologacao/producao.

## Complemento Bloco 21.1

A correcao pos-auditoria do Bloco 21 gerou prints locais obrigatorios em `docs/v3/evidencias/bloco-21/`, usando apenas backend/frontend locais, PostgreSQL descartavel, dados sinteticos e Edge headless local.

Tambem foram corrigidos:

- age gate sem data pre-preenchida;
- botao de idade desabilitado ate data valida;
- textos visiveis menos tecnicos;
- metadata de pacote para evitar `nao informado` quando testes foram executados;
- contencao mobile de grids, cards, paineis e textos longos.

Nao houve redesign, nova paleta, nova tipografia, producao, banco de producao, dado real, midia real, migration, SQL de schema, remote, push ou commit.
