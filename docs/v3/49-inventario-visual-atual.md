# Inventário visual atual

## Escopo

Inventariar, somente com arquivos locais disponíveis no workspace, se há referência visual confiável do site atual do Tops do Job para orientar a V3.

Esta fase não acessa produção, não captura prints, não baixa imagens, não chama API externa e não coleta conteúdo explícito.

## Inventário inicial

Inventário inicial da execução gerado fora do repositório antes das alterações:

```text
C:\Users\WpD\AppData\Local\Temp\topsv3-inventario-inicial-2026-06-26-113346-544\INVENTARIO-INICIAL.csv
```

O arquivo ficou fora do Git e foi confirmado como UTF-8 com BOM.

## Fontes locais verificadas

Foram procurados arquivos locais que poderiam representar fonte visual atual:

- imagens (`png`, `jpg`, `jpeg`, `webp`, `gif`, `avif`, `svg`);
- folhas de estilo (`css`, `scss`);
- HTML estático;
- PDFs;
- arquivos de design (`fig`, `sketch`, `xd`);
- JSONs já versionados;
- documentação existente em `docs/v3`;
- skeleton frontend local.

## Resultado

Não foram encontrados no workspace prints, imagens, HTML legado, CSS legado, arquivo de design ou pacote visual que permita reconstruir com segurança o visual atual.

Arquivos locais encontrados e limites:

| Fonte | Uso permitido | Limite |
| --- | --- | --- |
| `frontend/src/app/globals.css` | CSS do skeleton local | Não é fonte do visual legado nem layout final |
| `docs/v3/conteudo-publico-capturado/*.json` | Metadados e textos públicos sanitizados | Não contém imagem, HTML completo, cards, grid, cores ou tipografia |
| `docs/v3/06-midias-banners-e-stories.md` | Regras de mídia e banners já documentadas | Não descreve visual completo da home/listagens |
| `docs/v3/10-criterios-aceite.md` | Critérios de aceite de banners | Não substitui inventário visual |
| `docs/v3/45-admin-shell-local.md` | Shell admin estrutural local | Não é referência visual final |

## Status

Fonte visual atual disponível no workspace: **não**.

Status formal: `PENDENTE_FONTE_VISUAL_ATUAL`.

## Pendências de fonte visual

Para consolidar componentes finais do frontend, ainda são necessárias fontes confiáveis do visual atual, por exemplo:

- prints da home em desktop e mobile;
- prints de listagens por UF, cidade e bairro;
- print de página pública de anúncio;
- prints de páginas institucionais relevantes;
- exemplo de cards atuais;
- exemplo de badges e botões atuais;
- referências de navegação pública;
- amostras de banners desktop e mobile nas dimensões oficiais;
- CSS ou tokens visuais do legado, se houver autorização para copiar ou consultar;
- decisão formal quando houver divergência entre visual atual e melhoria proposta.

## Diretriz para fases futuras

Até que as pendências sejam resolvidas:

- não criar nova identidade visual;
- não definir nova paleta;
- não definir nova tipografia;
- não redesenhar cards;
- não redesenhar home;
- não criar layout final de listagem ou anúncio;
- não tratar skeleton local como referência visual;
- documentar qualquer melhoria visual como leve, compatível e aprovada.

## Conclusão

A Fase 1C.7 preserva o escopo e bloqueia redesign por ausência de fonte visual confiável no workspace. A V3 fica preparada para receber referências reais depois, sem inventar aparência nem descaracterizar o Tops do Job atual.
