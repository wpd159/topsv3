# Relatorio - Shell visual de producao

## Resumo

O Bloco 63 implementou a primeira fase da Opção B: substituir a casca publica de skeleton por um shell visual inspirado diretamente no frontend de producao em `C:\clone\topsdojob-frontend`.

## O que veio do clone

Referencia visual:

- header branco com logo, links e CTA forte;
- hero com imagem de fundo escurecida;
- busca visual em destaque;
- cards de categoria com imagem;
- footer publico com colunas e CTA;
- uso da paleta rosa principal da marca;
- containers largos e fundo branco.

Assets copiados:

- `2151117281.jpg`;
- imagens em `public/cards/*.jpg`.

## O que foi adaptado para a V3

- Nenhuma chamada ao backend antigo foi copiada.
- A busca visual usa link interno seguro.
- Categorias usam links internos V3 e dados estaticos temporarios.
- Header usa `/logo.webp` local.
- Login/registro foram ligados a rotas V3 existentes.
- O footer usa links internos e nao abre modal antigo.
- Adapters V3 foram preservados.

## Telas impactadas

- `/`
- `/acompanhantes/go/goiania`
- `/acompanhantes/go/goiania/setor-bueno`
- `/anuncios/[slug]`
- `/anunciar`
- paginas institucionais que usam `PublicRouteShell`

## Pendencias

- Migrar filtros reais.
- Migrar detalhe do anuncio completo.
- Migrar wizard completo com adapters V3.
- Decidir dependencia visual final, se Tailwind/Radix do clone forem necessarios.
- Validar visual humano em HML.
- Substituir stubs de busca/categorias por adapters V3 quando houver fase propria.

## Confirmacoes

- `C:\clone` nao foi alterado.
- Nao houve push.
- Nao houve backend, banco, migrations, dados reais, Pix/Efi, upload real, webhook real ou API externa real.
