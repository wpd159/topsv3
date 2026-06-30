# Catalogo de transformacao legado para V3

Status da entrega: `CATALOGO DOCUMENTAL`.

## Usuarios

- Normalizar telefone/WhatsApp para formato E164 quando houver evidencia.
- Normalizar e-mail como dado pessoal.
- Detectar duplicidade suspeita sem mesclar automaticamente.
- Nunca usar documento privado como dado publico.

## Anuncios

- Preservar slug publico sempre que possivel.
- Validar slug e duplicidade antes de qualquer promocao.
- Mapear status legado por evidencia.
- Normalizar preco como decimal/numeric conceitual, nunca float.
- Sinalizar anuncio sem foto.
- Sinalizar anuncio sem cidade.
- Preservar contato pelo WhatsApp em anuncio aprovado/ativo conforme regra futura.

## Localidades

- Normalizar UF, cidade e bairro.
- Validar hierarquia UF -> cidade -> bairro.
- Sinalizar localidade sem evidencia suficiente.

## Midia e documentos

- Classificar foto, video e story por manifesto futuro.
- Validar checksum quando houver evidencia declarada.
- Sinalizar midia sem manifesto.
- Manter documento privado fora de midia publica.
- Aplicar politica documental `ENQUANTO_HOUVER_ANUNCIO`.
- Manter `retencao_ate` nullable.
- Exigir acesso auditavel.
- Nao criar expurgo automatico nesta fase.
- Nunca publicar documento privado.

## Pagamentos

- Classificar provedor por evidencia real.
- Registrar que tabela legada com nome Mercado Pago pode conter Efi.
- Nao converter Efi em Mercado Pago pelo nome da tabela.
- Nao converter Mercado Pago em Efi sem evidencia.
- Sinalizar `PAGAMENTO_SEM_PROVEDOR`.
- Sinalizar `PAGAMENTO_EFI_NAO_CONFIRMADO`.
- Sinalizar `STATUS_PAGAMENTO_INCONSISTENTE`.

## Creditos

- Usar inteiro/bigint conceitual.
- Nunca usar float.
- Sinalizar credito sem pagamento.
- Sinalizar pagamento aprovado sem credito.
- Sinalizar credito duplicado.

## Premium e beneficios

- Preservar Premium atual.
- Preservar beneficios atuais.
- Novos recursos devem ser aditivos.
- Nao criar limite diario de cliques, contatos ou WhatsApp no gratuito.
- Sinalizar inconsistencia de expiracao ou pacote.

## Metricas

- Tratar metricas de producao como existentes.
- Preservar, migrar ou reimplementar com equivalencia funcional.
- Manter visualizacoes e cliques WhatsApp quando houver evidencia.
- Nao tratar metricas como funcionalidade inexistente do zero.

## SEO e URLs

- Preservar `/anuncios/[slug]`.
- Preservar `/acompanhantes/[uf]/[cidade]`.
- Preservar `/acompanhantes/[uf]/[cidade]/[bairro]`.
- Preservar `/sitemap.xml` e `/robots.txt`.
- Proibir rotas alternativas de anuncio.
- Decidir manter, redirecionar, noindex ou remover por evidencia.
- Evitar derrubar pagina indexada util sem auditoria.

## Banners

- Preservar dimensoes desktop 1452x500 e mobile 1080x900.
- Validar midia pelo manifesto futuro.
- Permitir rascunho sem midia obrigatoria.

## Comercial

- Preservar foco em liquidez e base de anuncios.
- Campanhas/cortesias precisam de validade e auditoria futuras.
- Nao transformar gratuito em produto artificialmente limitado.

## Plano de execucao futuro

- Validar pacote, dicionario e regras antes de preparar entidades.
- Preparar anuncios somente depois de usuarios e localidades.
- Preparar midias somente depois de anuncios.
- Preparar creditos somente depois de pagamentos ou evidencia auditavel equivalente.
- Preparar SEO/URLs somente depois de anuncios e localidades.
- Gerar relatorio de dry-run antes de qualquer importacao real.
- Bloquear importacao real enquanto houver pendencia critica.

## Fora do escopo

- leitura de dump;
- abertura de arquivo real de entrada;
- transformacao real;
- importacao real;
- acesso a banco;
- migration;
- SQL;
- API externa;
- storage real;
- deploy.
