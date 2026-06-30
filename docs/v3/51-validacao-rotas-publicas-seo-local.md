# Validação local de rotas públicas e SEO skeleton

## Objetivo

Criar uma validação local, estática e sem build para proteger as rotas públicas críticas e as regras SEO locais enquanto a Fase 1D aguarda revisão em modo Pro.

Esta validação não acessa produção, não executa crawler real, não consulta banco, não chama API externa, não baixa dependências e não substitui as validações futuras de SEO real.

## Script

Arquivo criado:

```powershell
.\scripts\local\validar-rotas-publicas-seo-local.ps1
```

Execução:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/validar-rotas-publicas-seo-local.ps1
```

O script retorna exit code diferente de zero quando uma regra crítica falha.

## Contratos protegidos

A rota pública absoluta de anúncio permanece:

```text
/anuncios/[slug]
```

Rotas alternativas de anúncio continuam proibidas:

```text
/anuncio/[id]
/perfil/[slug]
/acompanhante/[slug]
/ads/[slug]
```

Rotas públicas locais preservadas:

```text
/acompanhantes/[uf]/[cidade]
/acompanhantes/[uf]/[cidade]/[bairro]
```

## Regras verificadas

O script verifica:

- existência dos arquivos das rotas públicas;
- inexistência de diretórios de rotas alternativas proibidas;
- ausência de links internos para rotas alternativas de anúncio;
- existência de `robots.ts` e `sitemap.ts`;
- `robots.ts` bloqueando indexação local;
- `sitemap.ts` usando `localUrl`;
- ausência de domínio de produção em canonical ativo local;
- uso de `NEXT_PUBLIC_CANONICAL_DOMAIN` com fallback local seguro;
- páginas admin usando metadata `noindex`;
- skeletons marcados como temporários;
- ausência de imagens reais nas rotas skeleton;
- ausência de chamadas externas nas rotas skeleton;
- ausência de chamadas para backend de domínio;
- ausência de SQL e diretórios de migration/domínio/repository/service.

## Limites

Esta validação é local e estática. Ela não garante:

- resposta HTTP real de produção;
- crawler comparativo;
- sitemap definitivo de produção;
- robots definitivo de produção;
- canonical final com dados reais;
- qualidade editorial final;
- dados estruturados finais;
- indexabilidade real no Google.

Esses pontos ficam para fases futuras, especialmente Fase 4, Fase 6 e validações pré-virada.

## Segurança

A validação não usa:

- VPS;
- produção;
- banco;
- Efí real;
- OpenAI;
- API externa;
- admin;
- login;
- dados reais;
- imagens reais;
- conteúdo explícito.

## Relação com a Fase 1D

A Fase 1D continua não iniciada. Esta fase cria apenas proteção local de rotas e SEO skeleton, sem migration, SQL, banco, entidade JPA, repository ou service de negócio.
