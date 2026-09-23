# Capas editoriais: compressão focal e proposta para as dez existentes

Esta proposta acompanha somente a alteração de JPEG opaco no `BlogImagemProcessor`.
Não autoriza atualização das imagens publicadas, backfill ou alteração de fotos de anunciantes.
O crawl Screaming Frog de 22/09/2026 tinha limite de 500 recursos; as dez capas
abaixo não representam inventário completo do blog.

## Medição e escolha

As dez URLs do alerta foram obtidas por GET público em 23/09/2026. Os bytes e os
SHA-256 coincidiram com a exportação original. Todas são JPEG opaco de 1200×630.
O [manifesto por objeto](BLOG-capas-editoriais-medicao-20260923.csv) contém URL,
ID da imagem, artigo, hash original, bytes e hash das alternativas. Não contém
chaves privadas nem arquivos de imagem.

| Alternativa aplicada às dez cópias | Total | Diferença vs. atual | Decisão |
| --- | ---: | ---: | --- |
| JPEG publicado | 1.430.461 B | — | Referência |
| JPEG ImageIO 17, qualidade 0,80, 1200×630 | 1.014.020 B | −29,1% | Escolhida para novas CAPAs opacas |
| JPEG ImageIO 17, qualidade 0,75, 1200×630 | 829.847 B | −42,0% | Texto fino mais suave sob ampliação |
| WebP q0,90, 1200×630, Sharp/libvips | 871.486 B | −39,1% | Formato/encoder diferentes; compatibilidade OG não validada |
| JPEG q0,90, 1000×525 | 1.058.731 B | −26,0% | Reduz resolução do artigo; não escolhida |
| JPEG q0,90, 800×420 | 774.414 B | −45,9% | Suavização visível no artigo; não escolhida |
| PNG opaco | 8.348.997 B | +483,6% | Não apropriado para esta amostra |

A compressão foi medida separadamente de formato e resolução. A inspeção das dez
capas em artigo 976×549, card 500×224 e miniatura 48×48 encontrou texto e
recorte preservados em JPEG 0,80. A miniatura já torna texto pequeno ilegível no
original; a compressão não resolve esse limite de layout. Seis das dez ainda
superariam 100 KB em JPEG 0,80: o objetivo é reduzir bytes sem sacrificar a
imagem, não atingir o limiar da ferramenta a qualquer custo.

As candidatas foram **recodificadas dos JPEGs já publicados**. Os bytes e hashes
JPEG 0,80 do manifesto foram conferidos nas dez cópias pelo `BlogImagemProcessor`
completo (incluindo `redimensionar`), com entrada validada sinteticamente; não
resultam de upload pelo editor. Os números não preveem o tamanho exato de futuros
uploads de fontes distintas.
O caminho PNG com transparência e o JPEG do tipo OG (qualidade 0,90) não mudam.
A geração ocorre antes do armazenamento e continua produzindo um único objeto
privado por upload; a publicação usa uma
cópia pública dos mesmos bytes. Esta mudança não adiciona otimizador HTTP ou
camada de cache.

## Dez capas existentes: operação proposta, ainda não executada

O manifesto identifica exatamente os dez objetos e respectivos artigos. Uma
consulta pública focal em 23/09 confirmou esses vínculos e encontrou mais cinco
artigos publicados fora desta amostra. Antes de qualquer troca, é obrigatório
reconciliar novamente, pelo painel/consulta administrativa autorizada, `post.id`,
`versao`, estado, `imagemCapaId`, `imagemOgId`, URL, `blog_imagem.id`, chave pública
e SHA-256. A amostra pública não substitui essa conferência transacional.

1. Guardar uma cópia controlada dos bytes atuais de cada capa, com hash conferido,
   identificação do post, versão, IDs de capa/OG e recibo de recuperação. Antes de
   começar, comprovar que o objeto **privado antigo** ainda é legível e tem o hash
   do registro, e que o operador autorizado é o criador da imagem antiga. Sem
   essas condições, parar: o reupload sob o processador novo não garante
   recuperação bit a bit. Não anexar binários ou chaves privadas ao PR.
2. Submeter as dez saídas JPEG 0,80 à revisão visual humana, incluindo texto
   fino, recorte e prévia social. Elas são apenas prévias: enviar ao editor as
   **cópias originais preservadas**, pois enviar a saída 0,80 produziria uma
   segunda compressão. A alteração proposta não troca o objeto OG.
3. Em fase produtiva autorizada separadamente, usar o editor administrativo
   existente para upload de **nova CAPA a partir do original** e atualização
   versionada de um post por vez, mantendo os demais campos e o OG. Conferir o
   hash/bytes obtidos; o manifesto não substitui essa prova. O editor também
   aciona a revalidação pública necessária; um PUT backend isolado não faz essa
   invalidação.
4. Após cada sucesso, conferir nova URL/bytes e renderização no artigo, categoria
   e índice, além do resultado da remoção da antiga chave pública na origem.
   Registrar pares antigo→novo e não repetir cegamente uma operação ambígua.
5. Recuperação exata, se necessária: após reconciliar qualquer erro ambíguo,
   revalidar versão/estado/vínculo do post e o objeto privado antigo. O operador
   que criou a imagem pode voltar a vincular o **ID antigo** pelo endpoint
   administrativo versionado já existente, que copia os mesmos bytes privados
   para a chave pública; conferir o hash na origem e revalidar as páginas pelo
   mecanismo administrativo existente. A interface atual não oferece seletor de
   imagens antigas e um PUT isolado não revalida o frontend. Outro operador não
   pode reanexar um ID desvinculado pelo guard atual. Se o ID privado não puder
   ser reanexado com segurança, interromper e revisar a recuperação; reupload
   sob a nova qualidade não restaura os bytes antigos.

O código atual remove objetos recém-criados em rollback e apaga a antiga chave
pública após commit de retirada/substituição. Falha no delete pós-commit exige
reconciliação de banco e storage; não equivale a rollback do banco. Essa exclusão
é comprovável na origem, **não** demonstra revogação imediata de caches de
navegador, CDN ou outros intermediários. Nenhum objeto real foi alterado aqui.
