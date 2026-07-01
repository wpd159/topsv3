# Fase 1C.4 - rotas públicas skeleton e SEO local seguro

## Objetivo

Preparar o skeleton frontend das rotas públicas críticas da V3 sem implementar busca real, anúncios reais, SEO final, backend de domínio, banco, migrations ou dados reais.

## Rotas criadas

Rotas públicas preservadas como skeleton:

```text
/anuncios/[slug]
/acompanhantes/[uf]/[cidade]
/acompanhantes/[uf]/[cidade]/[bairro]
/robots.txt
/sitemap.xml
```

As páginas renderizam placeholders neutros e deixam explícito que são skeleton local da V3.

Esses placeholders não representam o layout final da V3 nem autorizam redesign do site atual. A consolidação visual futura deve aguardar o inventário de fonte visual real do legado e seguir a diretriz de preservação documentada na Fase 1C.7.

## Validação local complementar

A Fase 1C.8 adiciona o script:

```powershell
.\scripts\local\validar-rotas-publicas-seo-local.ps1
```

Ele valida estaticamente a existência das rotas públicas skeleton, a ausência de rotas alternativas de anúncio, `robots.ts`, `sitemap.ts`, canonical local, admin `noindex`, ausência de imagens reais, ausência de dados reais e ausência de chamadas externas.

Essa validação não substitui crawler real futuro, Search Console, validação HTTP de produção, sitemap definitivo ou SEO real.

## SEO local

Regras implementadas:

- canonical gerado por `NEXT_PUBLIC_CANONICAL_DOMAIN`;
- default local: `http://localhost`;
- se `APP_ENV` público estiver como `local`, domínio de produção configurado é substituído por `http://localhost`;
- páginas skeleton usam `noindex`;
- `robots.txt` local bloqueia indexação integralmente;
- `sitemap.xml` local contém apenas URLs locais mínimas de skeleton.

Não há canonical de produção emitido no local.

## Fora de escopo

Esta fase não cria:

- busca real;
- anúncio real;
- conteúdo adulto real;
- fotos reais;
- dados reais;
- JSON-LD final;
- backend de domínio;
- chamada a API externa;
- migration;
- SQL;
- schema;
- tabela;
- entidade JPA;
- repository;
- service de negócio;
- conexão com banco.

## Fases futuras

- Fase 4: frontend público real.
- Fase 6: SEO consolidado, dados estruturados finais, sitemap/robots definitivos e validações de indexabilidade.
- Fase 1C.5: estratégia GEO/AEO/LLM Visibility, páginas institucionais skeleton, `llms.txt` local e diretrizes de conteúdo neutro para IA.
- Fase 1C.7: preservação do visual atual, inventário de fonte visual e bloqueio de redesign sem aprovação expressa.
- Fase 1C.8: validação local estática das rotas públicas e SEO skeleton.
- Fase 1D: migrations Flyway reais, ainda aguardando revisão reforçada em modo Pro.

## Complemento Bloco 5

O Bloco 5 adiciona endpoints backend publicos de leitura para apoiar futuramente as rotas preservadas:

- `GET /api/public/anuncios/{slug}`;
- `GET /api/public/acompanhantes/{uf}/{cidade}`;
- `GET /api/public/acompanhantes/{uf}/{cidade}/{bairro}`;
- `GET /api/public/seo/rota`.

Esses endpoints nao criam novas rotas publicas de site. Rotas alternativas como `/perfil`, `/ads`, `/anuncio/[id]` e `/acompanhante/[slug]` continuam proibidas.

O frontend visual nao foi alterado neste bloco.

## Complemento Bloco 6

O Bloco 6 conecta as rotas publicas skeleton aos endpoints backend locais de leitura, sem criar rota publica alternativa e sem alterar a diretriz visual.

O endpoint backend `GET /api/public/seo/rota` passa a aceitar tambem:

- `/sitemap.xml`;
- `/robots.txt`.

Continuam proibidas:

- `/perfil/*`;
- `/ads/*`;
- `/anuncio/*`;
- `/acompanhante/*`;
- URL absoluta de producao.

As paginas publicas mantem canonical local, `noindex` e fallback seguro quando a API local nao responde.
