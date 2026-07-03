# Checklist SEO de cutover

## Antes de homologacao

- [ ] Validar rotas publicas preservadas.
- [ ] Confirmar ausencia de `/perfil`, `/ads`, `/anuncio` e `/acompanhante` como rotas paralelas.
- [ ] Confirmar que admin permanece `noindex`.
- [ ] Confirmar que sitemap nao inclui `/api`.
- [ ] Confirmar que sitemap nao inclui admin.
- [ ] Confirmar que canonical nao aponta para ambiente errado.
- [ ] Confirmar que robots de staging/homologacao segue bloqueado enquanto necessario.
- [ ] Confirmar que paginas publicas nao exibem "skeleton" ou "API local".
- [ ] Confirmar que mobile nao tem scroll horizontal, scroll lock ou elemento flutuante indevido.

## Antes de producao

- [ ] Registrar baseline do Search Console.
- [ ] Congelar mapa de URLs.
- [ ] Testar redirects necessarios.
- [ ] Validar sitemap final sem URLs fracas.
- [ ] Validar robots final.
- [ ] Validar canonical final sem duplicidade.
- [ ] Validar title/description das paginas prioritarias.
- [ ] Revisar paginas de cidade e bairro com foco em `acompanhante em [cidade]`.
- [ ] Validar que conteudo bloqueado nao aparece como anuncio publico normal.
- [ ] Validar que WhatsApp publico continua mediado pelo backend.
- [ ] remover noindex somente no cutover aprovado.

## Depois do cutover

- [ ] Monitorar 404.
- [ ] Monitorar 5xx.
- [ ] Monitorar cobertura e sitemap no Search Console.
- [ ] Monitorar consultas de marca.
- [ ] Monitorar consultas locais.
- [ ] Comparar cliques, impressoes, CTR e posicao media contra baseline.
- [ ] Registrar queda relevante e plano de rollback quando aplicavel.

## Bloqueios

Cutover SEO nao pode prosseguir se:

- sitemap contem URL de API;
- sitemap contem admin;
- paginas publicas finais continuam `noindex` sem justificativa;
- canonical aponta para ambiente errado;
- rotas principais retornam erro;
- conteudo de ambiente aparece para visitante;
- dados reais foram copiados indevidamente para o repo;
- producao foi alterada sem plano de rollback.
