# Dados sinteticos locais

## Objetivo

Definir a base sintetica local do Bloco 30 para permitir continuidade do desenvolvimento da V3 sem backup, dump, restore, sanitizacao, Docker, producao ou banco de quarentena como staging.

## Arquivos

- Fixture: `backend/src/test/resources/fixtures/v3-dados-sinteticos.json`
- Gerador/relatorio: `scripts/local/gerar-dados-sinteticos-v3-local.ps1`
- Validador: `scripts/local/validar-dados-sinteticos-v3-local.ps1`
- Evidencia: `docs/v3/evidencias/bloco-30/relatorio-base-sintetica-local.md`

## Escopo sintetico

- 5 cidades: Goiania/GO, Aparecida de Goiania/GO, Brasilia/DF, Anapolis/GO e cidade local de controle.
- 9 bairros sinteticos, incluindo Setor Bueno, Jardim Goias, Marista, Campinas, Centro, Asa Sul e Asa Norte.
- 12 anuncios sinteticos cobrindo ativo, pausado, pendente, rejeitado e controle negativo.
- Classificacao binaria `LIVRE` e `BLOQUEADO`.
- Planos gratuito, Premium ativo e Premium expirado.
- Casos com bairro, sem bairro, SEO forte, SEO fraco e midia placeholder insuficiente.
- Beneficios Premium sinteticos: destaque, anuncio topo, fotos extra, stories, pacote expirado e gratuito util.
- Metricas agregadas de views e cliques WhatsApp, sem IP bruto e sem user-agent bruto.

## Regras de seguranca

- Nao usar CPF, RG, documento, selfie, e-mail real, telefone real, WhatsApp real ou endereco especifico real.
- Usar apenas dominio reservado `example.test` quando e-mail sintetico for necessario.
- Usar placeholders nao discaveis para WhatsApp e telefone.
- Nao usar imagem real, midia adulta real, storage key real, bucket real ou URL privada.
- Nao usar token, senha, certificado, payload Pix/Efi, pagamento real ou API externa real.
- Frontend nao decide classificacao; backend permanece fonte da decisao.
- `BLOQUEADO` nao libera midia publica nem WhatsApp publico.

## Limites

A base sintetica nao substitui a revisao Pro/humana nem o gate de pre-staging/cutover com backup consistente. Ela serve apenas para desenvolvimento local, validacoes estaticas, rotas publicas locais, SEO local, admin local, moderacao local, Premium read-only, wizard `/anunciar`, age gate e metricas agregadas.
