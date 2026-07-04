# Plano SEO cidade e bairro

## Objetivo

Aumentar trafego de intencao local sem sacrificar marca, qualidade ou seguranca.

Consulta-alvo central:

```text
acompanhante em [cidade]
```

Extensoes futuras:

- acompanhante em [bairro];
- acompanhantes [cidade];
- acompanhantes [bairro] [cidade];
- acompanhante perto de mim, quando houver estrategia local aprovada.

## Estrutura

Rotas base:

- `/acompanhantes/[uf]/[cidade]`;
- `/acompanhantes/[uf]/[cidade]/[bairro]`.

Cada pagina futura deve ter:

- title unico;
- description unica;
- canonical correto;
- texto util e humano;
- listagem real apenas quando houver dados aprovados;
- estado vazio nao indexavel ou tratado com cuidado;
- links internos para bairros/cidades relacionados;
- ausencia de API routes no sitemap.

## Qualidade minima

Nao publicar pagina local se ela for:

- duplicada;
- vazia sem valor;
- gerada em massa sem revisao;
- sem canonical coerente;
- com conteudo tecnico de ambiente;
- dependente de dados reais nao autorizados;
- com midia/contato bloqueado pelo backend.

## Conteudo

O texto deve preservar o tom atual do Tops do Job e evitar promessa falsa.

Prioridades:

- clareza para visitante;
- correspondencia com cidade/bairro;
- seguranca;
- chamada para listagem ou cadastro;
- ausencia de dados sensiveis.

## Cutover

Durante cutover, remover `noindex` apenas quando:

- rotas finais estiverem validadas;
- sitemap final estiver limpo;
- canonical estiver correto;
- Search Console baseline estiver registrado;
- redirects estiverem testados;
- mobile estiver estavel.

## Bloco 27

Padroes implementados localmente:

- cidade: title/H1 `Acompanhantes em [Cidade] - [UF]`;
- bairro: title/H1 `Acompanhantes em [Bairro], [Cidade] - [UF]`;
- texto introdutor humano e curto;
- breadcrumbs;
- cidade linka bairros e anuncios quando houver dados;
- bairro linka cidade e anuncios;
- paginas vazias continuam com mensagem util, sem indexacao local.

## Bloco 28

Inventario publico sanitizado observou:

- 29 URLs de cidade;
- 21 URLs de bairro;
- 62 URLs de anuncio;
- 0 URLs admin/API no sitemap.

As cidades e bairros devem ser priorizados por Search Console e conteudo real aprovado. Nao criar ranking definitivo apenas pela ordem do sitemap.
