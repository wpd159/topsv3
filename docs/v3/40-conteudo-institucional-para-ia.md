# Conteúdo institucional para IA

## Objetivo

Definir diretrizes para páginas institucionais que ajudem buscadores e IAs a entenderem o Tops do Job sem expor dados reais, conteúdo explícito, áreas privadas ou funcionalidades ainda não implementadas.

## Páginas institucionais skeleton

Criadas como skeleton local:

```text
/sobre
/como-funciona
/seguranca
/anunciar
/perguntas-frequentes
```

Essas páginas são neutras, locais, `noindex` e indicam que o conteúdo final precisa de revisão humana antes de produção.

## Mensagem central

O conteúdo público futuro deve deixar claro que o Tops do Job é uma plataforma brasileira de anúncios classificados de acompanhantes, organizada por localidade e por páginas de anúncio, com atenção a segurança, privacidade, moderação e preservação de URLs.

O texto não deve sugerir que todas as verificações, moderações, rankings, recursos de pagamento ou proteções já existem se ainda não estiverem implementados.

## FAQ neutro

O FAQ futuro deve responder perguntas como:

- o que é o Tops do Job;
- como as páginas por cidade e bairro são organizadas;
- como anúncios devem ser revisados antes de publicação;
- quais áreas são públicas e quais são privadas;
- como segurança e privacidade serão tratadas;
- por que algumas páginas ficam noindex;
- como sitemap, robots e canonical serão usados.

Perguntas sobre conteúdo sensível devem ser tratadas com linguagem institucional, sem descrição explícita.

## Conteúdo local

Páginas por cidade e bairro só devem ser indexáveis quando tiverem conteúdo útil, revisão humana, anúncios publicáveis e qualidade suficiente. Páginas locais vazias, duplicadas, fracas ou geradas automaticamente devem ficar fora do sitemap ou em `noindex`.

## Diretrizes editoriais

- preservar `/anuncios/[slug]`;
- preservar `/acompanhantes/[uf]/[cidade]`;
- preservar `/acompanhantes/[uf]/[cidade]/[bairro]`;
- usar cidade e bairro apenas quando houver fonte aprovada;
- evitar repetição artificial de palavras-chave;
- revisar textos sensíveis antes de produção;
- não publicar conteúdo gerado por IA sem aprovação humana;
- não usar dados privados, documentos, telefone, CPF, pagamento ou token;
- não criar conteúdo explícito.

## llms.txt

`frontend/public/llms.txt` é um guia público local para explicar o projeto, suas rotas e limites. Ele não substitui sitemap, robots, schema.org, conteúdo real nem revisão humana.

O arquivo não deve conter dado real, anúncio real, conteúdo explícito, telefone, CPF, pagamento, token, segredo, chave de IA ou promessa não implementada.

## Schema futuro

Helpers e documentação podem preparar os tipos aceitos, mas páginas dinâmicas não devem emitir JSON-LD final nesta fase. Schema futuro deve ser criado junto do conteúdo real, validado por humano e coerente com o OpenAPI, sitemap, robots e canonical do ambiente.

## Bloqueios

Continuam bloqueados:

- acesso a IA externa;
- integração OpenAI ou semelhante;
- publicação automática;
- scraping;
- spam;
- backend de domínio;
- busca real;
- dados reais;
- banco, SQL e migrations.
