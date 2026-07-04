# SEO - plano de redirecionamentos 301

## Quando usar 301

Usar 301 somente quando:

- a URL antiga possui equivalente publico claro na V3;
- o conteudo de destino atende a mesma intencao;
- o destino nao e pagina vazia, bloqueada ou fraca;
- canonical, sitemap e robots estao coerentes;
- o redirect foi testado antes do cutover.

## Quando manter URL

Manter URL quando a rota atual ja e o contrato correto:

- `/`;
- `/acompanhantes/[uf]/[cidade]`;
- `/acompanhantes/[uf]/[cidade]/[bairro]`;
- `/anuncios/[slug]`;
- `/anunciar`, se preservada como fluxo publico.

## Quando usar noindex/remover

Usar noindex, remover do sitemap, 404/410 ou decisao equivalente quando:

- nao ha equivalente publico seguro;
- o anuncio foi removido, bloqueado ou nao deve ser indexavel;
- a pagina e fraca/vazia;
- a pagina contem estado interno ou tecnico;
- a rota e admin/API.

## Quando nao redirecionar

Nao redirecionar:

- anuncio removido para home;
- pagina de pessoa/anuncio para listagem generica sem equivalencia;
- URL proibida para destino que confunda crawler/visitante;
- admin/API para pagina publica;
- URL com dado sensivel para URL versionada em documento.

## Rotas proibidas

Rotas antigas ou paralelas continuam proibidas:

- `/anuncio/[id]`;
- `/perfil/[slug]`;
- `/acompanhante/[slug]`;
- `/ads/[slug]`.

Elas so podem ter 301 se houver equivalencia segura para rota final aprovada, sem recriar a rota como pagina publica normal.

## Rollback

Plano minimo de rollback:

- manter snapshot do mapa aprovado;
- medir 404/5xx imediatamente apos cutover;
- reverter regra de redirect que gere erro em massa;
- recolocar no sitemap apenas URLs finais aprovadas;
- revalidar Search Console e logs de acesso;
- documentar queda de trafego e decisao tomada.
