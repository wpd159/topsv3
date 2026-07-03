# Bloco 26.2 - wizard, SEO central e Premium preview

## Objetivo

Completar o Bloco 26.1 convertendo `/anunciar` de formulario unico para wizard progressivo, adicionar preview local de Premium e centralizar documentacao/validacao SEO publica.

## Consulta obrigatoria a producao

Consulta realizada porque o bloco altera fluxo visual de `/anunciar` e Premium, e a diretriz permanente exige preservar comportamento publico atual quando houver duvida.

Escopo:

- somente leitura;
- sem login;
- sem editar arquivo;
- sem reiniciar servico;
- sem deploy;
- sem git pull;
- sem migration;
- sem SQL de escrita;
- sem imprimir `.env`, certificados, tokens ou senhas;
- sem baixar dump;
- sem alterar banco, midia ou producao.

Comandos somente leitura executados:

```text
ssh topsdojob "pwd; ls -la; find /var/www /opt /srv /home -maxdepth 4 -type d ..."
ssh topsdojob "cd /var/www/topsdojob-frontend && ls -la; find . -maxdepth 2 -type l -ls ..."
ssh topsdojob "cd /var/www/topsdojob-frontend/current && find . -maxdepth 3 -type f ..."
ssh topsdojob "cd /var/www/topsdojob-frontend/current && cat package.json"
ssh topsdojob "cd /var/www/topsdojob-frontend/current && find .next/server/app ..."
ssh topsdojob "cd /var/www/topsdojob-frontend/current && python3 ... source maps ..."
```

Arquivos/paths consultados:

- `/var/www/topsdojob-frontend/current`;
- `/var/www/topsdojob-frontend/current/package.json`;
- `.next/server/app/(private-routes)/anunciar/page.js`;
- `.next/server/app/(private-routes)/anunciar/wizard/page.js`;
- `.next/server/chunks/ssr/src_features_anuncio-wizard_anuncio-wizard_tsx_6d248d44._.js.map`;
- `.next/server/chunks/ssr/_2856fc4b._.js.map`;
- `.next/server/chunks/ssr/src_app_(private-routes)_meus-anuncios_[slug]_monetizar_page_tsx_5894700c._.js.map`;
- `.next/server/chunks/ssr/src_app_(painel-admin)_admin_beneficios-premium_page_tsx_35109e78._.js.map`.

Observacao publica via navegador:

- `/anunciar` redirecionou para `https://topsdojob.com/?next=%2Fanunciar`;
- `/anunciar/wizard` redirecionou para `https://topsdojob.com/?next=%2Fanunciar%2Fwizard`;
- `/admin/beneficios-premium` redirecionou para `https://topsdojob.com/?next=%2Fadmin%2Fbeneficios-premium`;
- no mobile, `/anunciar` tambem redirecionou para home e nao apresentou overflow horizontal na observacao publica;
- nenhuma autenticacao foi feita;
- nenhum formulario foi enviado.

Confirmacao:

- nada foi alterado em producao;
- nada foi publicado;
- nenhum dado real foi copiado;
- nenhum segredo foi exibido.

## Fluxo antigo identificado

O wizard de producao atual encontrado no build possui etapas:

1. `perfil`: nome do anuncio, categoria e descricao de perfil;
2. `localizacao`: estado, cidade, bairro e referencia conhecida;
3. `servicos`: preco, horario, local de atendimento, servicos, descricao e link de conteudo;
4. `fotos`: fotos, limite atual e indicacao de upgrade para mais midia;
5. `revisao`: revisao antes de avancar;
6. `premium`: escolha entre publicar gratis ou destacar anuncio.

Validacoes observadas:

- perfil exige nome/categoria;
- localizacao exige estado/cidade/bairro;
- servicos exige preco, horario, local de atendimento e ao menos um servico;
- fotos exigem ao menos uma foto no fluxo de producao;
- KYC pode solicitar nome real, nascimento, CPF e documento quando necessario.

CTAs/decisoes observadas:

- fluxo progressivo com voltar/continuar;
- "Publicar gratis";
- "Destacar meu anuncio";
- Premium e opcional;
- ativacao Premium continua em fluxo separado/existente.

## O que foi preservado

- `/anunciar` passa a ser progressivo, uma etapa por vez.
- O gratuito continua util.
- Premium nao e obrigatorio para anunciar.
- O envio ocorre somente no fim, apos revisao.
- A equipe revisa antes de publicar.
- Frontend nao decide publicacao, contato publico ou classificacao.
- Midia e documentos ficam fora do envio local.

## O que foi descartado nesta fase

Pontos existentes em producao foram deliberadamente nao implementados no local por regra de escopo:

- upload de fotos;
- upload de documentos;
- KYC real;
- persistencia de rascunho em navegador;
- pagamento;
- checkout;
- Pix/Efi;
- ativacao Premium real;
- publicacao automatica.

## `/anunciar` V3 local

Etapas implementadas:

1. inicio;
2. dados basicos;
3. localizacao;
4. contato/WhatsApp;
5. detalhes do anuncio;
6. midia futura sem upload;
7. termos e revisao final;
8. sucesso.

Regras:

- uma etapa por vez;
- botao Voltar;
- botao Continuar;
- validacao por etapa;
- nenhum envio antes da revisao final;
- nenhum `localStorage`;
- nenhum `sessionStorage`;
- nenhum `document.body.style.overflow`;
- nenhum scroll lock;
- nenhum upload;
- nenhum pagamento;
- nenhum Premium obrigatorio;
- nenhuma publicacao automatica.

## Premium local

Foi criado preview administrativo em `/admin/premium` com:

1. inicio;
2. escolha de anuncio de exemplo;
3. escolha de beneficios;
4. periodo;
5. revisao;
6. resultado local.

O preview:

- nao chama endpoint de mutacao;
- nao compra;
- nao cobra;
- nao gera Pix;
- nao chama Efi;
- nao cria credito;
- nao ativa beneficio;
- nao promete resultado;
- nao limita gratuito.

## SEO central

Documentos criados:

- `docs/v3/SEO-prioridade-central-v3.md`;
- `docs/v3/SEO-mapa-preservacao-urls.md`;
- `docs/v3/SEO-plano-cidade-bairro.md`;
- `docs/v3/SEO-checklist-cutover.md`;
- `docs/v3/SEO-baseline-search-console.md`.

Script criado:

- `scripts/local/validar-seo-publico-local.ps1`.

O script valida:

- rotas publicas preservadas;
- ausencia de rotas alternativas;
- canonical local seguro;
- sitemap sem dominio de producao;
- sitemap sem API;
- robots local seguro;
- admin noindex;
- ausencia de "skeleton/API local" em textos publicos analisados;
- links de anuncio, cidade e bairro;
- existencia dos documentos SEO centrais;
- baseline Search Console informado.

## Baseline SEO

Registro de 24h:

- 92 cliques;
- 432 impressoes;
- CTR 21.3%;
- posicao media 8.7.

Diagnostico:

- trafego atual mais forte em marca/relacionados;
- meta de crescimento: `acompanhante em [cidade]`;
- Search Console completo fica para fase futura autorizada.

## Evidencias visuais geradas

As evidencias do bloco ficam em:

```text
docs/v3/evidencias/bloco-26-2/
```

Capturas de `/anunciar`:

- desktop inicial;
- desktop dados basicos;
- desktop localizacao;
- desktop contato;
- desktop detalhes;
- desktop midia futura;
- desktop revisao final;
- desktop sucesso;
- mobile inicial;
- mobile dados basicos;
- mobile localizacao;
- mobile contato;
- mobile detalhes;
- mobile midia futura;
- mobile revisao final;
- mobile sucesso.

Capturas do Premium:

- desktop inicial;
- desktop escolha de anuncio;
- desktop escolha de beneficios;
- desktop periodo;
- desktop revisao;
- desktop resultado;
- mobile inicial;
- mobile escolha de anuncio;
- mobile escolha de beneficios;
- mobile periodo;
- mobile revisao;
- mobile resultado.

Foram gerados 28 prints no total:

- 16 prints de `/anunciar` em desktop/mobile;
- 12 prints do preview Premium em desktop/mobile.

Observacao: a captura local pode exibir artefato externo do navegador de testes sobre a tela. Esse artefato nao e criado pelo codigo da V3.

## Validacoes executadas

Resultados registrados nesta execucao:

- codificacao: OK;
- arquivos proibidos: OK;
- segredos: OK, com `gitleaks` indisponivel e fallback local sem achados;
- UI mobile estatica: `OK_UI_MOBILE_ESTATICA`;
- rotas publicas e SEO local: 51/51 OK;
- SEO publico central: `OK_SEO_PUBLICO_LOCAL`, 37/37 OK;
- API publica local: `OK_API_PUBLICA_LOCAL`, 1089/1089 OK;
- persistencia JPA estatica: `OK_PERSISTENCIA_JPA_ESTATICA`, 20/20 OK;
- migrations SQL estaticas: 27/27 OK;
- fonte de importacao local: OK, sem fonte real informada;
- backend compile: OK;
- backend tests: OK;
- frontend lint: OK;
- frontend build: OK;
- E2E local descartavel: `OK_E2E_LOCAL_DESCARTAVEL`, executado em backend local na porta `18081`;
- smoke HTTP: OK;
- `git diff --check`: OK;
- `git diff --cached --check`: OK.

Uma tentativa anterior de E2E usando a porta `18080` falhou porque havia backend local antigo ocupando a porta. O teste foi descartado, o container/rede foram removidos pelo script e a validacao limpa foi reexecutada em `18081`.

## Pacote

O pacote final deve usar metadados de execucao com todas as validacoes executadas, para que `RESUMO-ENTREGA.md` e `RELATORIO-VALIDACOES.md` nao parecam registrar somente os tres scanners internos do empacotador.

## Proibicoes preservadas

Nao houve:

- alteracao de schema;
- migration;
- SQL;
- banco de producao;
- dado real;
- upload real;
- email real;
- WhatsApp real;
- pagamento;
- credito;
- Pix/Efi real;
- checkout;
- webhook;
- publicacao automatica;
- importador real;
- API externa;
- OpenAI;
- remote;
- push;
- commit.

## Riscos residuais

- Paridade final de campos do wizard depende de revisao Pro e fonte visual operacional completa.
- Upload/KYC real continuam pendentes.
- Premium real continua pendente de fase financeira aprovada.
- SEO final depende de staging, Search Console completo, sitemap real e cutover aprovado.
- Remocao de `noindex` publico so pode ocorrer no cutover aprovado.
