# Checklist Bloco 21 - paridade visual publica

## Escopo

- [x] Projeto permaneceu 100% local.
- [x] Nao houve redesign.
- [x] Nao houve nova identidade visual.
- [x] Nao houve nova paleta.
- [x] Nao houve nova tipografia.
- [x] Nao houve animacao automatica.
- [x] Nao houve carrossel automatico.
- [x] Nao houve elemento flutuante solto.
- [x] Nao houve barra fixa cobrindo conteudo.

## Rotas

- [x] `/` preservada como home local.
- [x] `/anuncios/[slug]` preservada.
- [x] `/acompanhantes/[uf]/[cidade]` preservada.
- [x] `/acompanhantes/[uf]/[cidade]/[bairro]` preservada.
- [x] Nenhuma rota alternativa proibida foi criada.

## Componentes

- [x] Home local com hierarquia mais clara.
- [x] Card publico local criado.
- [x] Grid publico local criado.
- [x] Cabecalho de localidade criado.
- [x] Detalhe publico local criado.
- [x] Placeholder de midia neutro criado.
- [x] CTA de contato mediado pelo backend.
- [x] Stories protegidos por idade.
- [x] Estado vazio padronizado.
- [x] Bloco SEO local padronizado.

## Privacidade e seguranca publica

- [x] Nenhuma imagem real usada.
- [x] Nenhum video real usado.
- [x] Nenhum dado real usado.
- [x] Nenhum telefone real usado.
- [x] Nenhum WhatsApp bruto renderizado no HTML.
- [x] Nenhuma storage key exposta.
- [x] Nenhum bucket exposto.
- [x] Nenhum hash interno exposto.
- [x] Nenhuma URL privada exposta.
- [x] `PENDENTE_URL_PUBLICA_MIDIA_CDN` preservado.

## Mobile

- [x] Sem scroll horizontal esperado.
- [x] Cards dentro da viewport.
- [x] CTAs dentro do fluxo normal.
- [x] Sem elemento flutuante solto.
- [x] Sem animacao automatica.
- [x] Sem scroll lock.
- [x] Sem `document.body.style.overflow`.
- [x] Sem `position: fixed`.
- [x] Sem `position: absolute`.
- [x] Sem `position: sticky`.
- [x] Sem `100vw`.
- [x] Sem `@keyframes`.
- [x] Sem `animation`.
- [x] Sem `transform`/`translate`.
- [x] Placeholder de midia com dimensao estavel.

## Consulta SSH

- [x] Consulta somente leitura realizada por necessidade documental.
- [x] Nenhum arquivo de producao alterado.
- [x] Nenhum servico reiniciado.
- [x] Nenhum deploy executado.
- [x] Nenhum banco acessado.
- [x] Nenhum segredo exibido.
- [x] Nenhum dump ou dado real copiado.

## Validacoes

- [x] Diagnostico de toolchain local.
- [x] Build local agregado.
- [x] E2E local descartavel.
- [x] API publica local via smoke HTTP do E2E.
- [x] Persistencia JPA estatica.
- [x] UI mobile estatica executada com OK durante a implementacao.
- [x] Scanners de seguranca.
- [x] Rotas publicas e SEO local.
- [x] Migrations SQL estaticas.
- [x] Fonte de importacao local.
- [x] Backend compile/test direto.
- [x] Frontend lint/build direto.

## Pendencias

- [x] Fonte visual completa segue pendente para revisao futura.
- [x] CDN/midia publica real segue pendente.
- [x] Revisao humana visual final segue pendente antes de homologacao/producao.

## Checklist Bloco 21.1

- [x] Prints desktop gerados.
- [x] Prints mobile gerados.
- [x] Print de placeholder/CTA gerado.
- [x] Age gate sem data pre-preenchida.
- [x] Botao de idade desabilitado ate data valida.
- [x] Textos publicos tecnicos suavizados.
- [x] Marca visivel ajustada para `Tops do Job`.
- [x] CSS mobile reforcado sem scroll lock.
- [x] `document.body.style.overflow` nao foi usado.
- [x] Nenhum `position: fixed`, `absolute` ou `sticky` novo.
- [x] Nenhum `100vw`, animacao, transform ou translate novo.
- [x] RESUMO do pacote deve receber metadados de testes executados.
- [x] Sem redesign, nova paleta ou nova tipografia.
