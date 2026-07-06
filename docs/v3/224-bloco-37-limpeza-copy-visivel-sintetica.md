# Bloco 37 - limpeza de copy visivel sintetica

## Objetivo

O Bloco 37 cria checkpoint local do Bloco 36 e remove de telas publicas/admin textos visiveis que pareciam bastidor tecnico, ambiente local ou fixture sintetica.

## Checkpoint

- Commit local do Bloco 36: `e031ea3`
- Mensagem: `test: valida premium beneficios sintetico ate bloco 36`
- Remote: vazio
- Push: nao executado

## Escopo executado

- Copy publica/admin ajustada para linguagem neutra e acentuada.
- Fixtures e seeds de demonstracao passaram a usar nomes como `Anúncio de demonstração`, `Perfil de demonstração para validação`, `Cidade de demonstração`, `Bairro de demonstração` e `Endereço de demonstração`.
- Validadores renderizados passaram a detectar texto visivel de bastidor como `local`, `sintético`, `mock`, `fixture`, `smoke test`, `descartável` e `API local`.
- O placeholder SEO publico deixou de renderizar URL com `localhost`, mantendo apenas caminho relativo.
- A descrição SEO de anúncio descarta metadado técnico/local quando vier da API e usa a descrição pública como fallback.
- Labels publicos/admin foram acentuados onde ainda havia texto sem acento.

## Correcao final antes de checkpoint

Auditoria do ZIP inicial do Bloco 37 encontrou prints ainda com copy tecnica ou sem acento. A origem foi corrigida somente na camada de apresentacao:

- descricao SEO tecnica da API local passa por filtro robusto no frontend publico antes de virar copy renderizada;
- age gate publico passou a exibir `Autorização`;
- permissoes e descricoes administrativas passaram a ser formatadas por helper de exibicao antes de aparecerem na tela;
- validadores renderizados passaram a reprovar explicitamente as strings tecnicas auditadas.

Nao houve alteracao de regra de negocio, backend funcional, banco, rotas, DTOs, contratos, Premium, Pix, pagamento ou arquitetura.

## Textos removidos da apresentacao

- `Anuncio sintetico local`
- `Registro sintetico neutro para smoke test local descartavel`
- `Perfil Sintetico Wizard`
- `Perfil sintetico wizard`
- `Texto sintetico`
- `Area administrativa local`
- `Sessao local`
- `Premium local`
- `API local`
- `Smoke test local descartavel`
- enums visiveis de preview como `PENDENTE_REVISAO` e `PUBLICADO_LOCAL`
- placeholder de login `admin.local@example.invalid`
- metadado SEO visivel `Metadados publicos locais para ANUNCIO`
- `Autorizacao autorizada`
- `admin configurar`
- `anuncio ler`
- `Preparar autorizacao futura comercial.`

## Textos substitutos

- `Anúncio de demonstração`
- `Perfil de demonstração para validação`
- `Perfil de Demonstração Wizard`
- `Texto de demonstração`
- `Área administrativa`
- `Sessão`
- `Premium`
- `Serviço`
- `Validação controlada`
- `Pendente de revisão`
- `Publicado`
- `Autorização autorizada`
- `configurar administração`
- `ler anúncios`
- `Preparar autorização comercial futura.`

## Limites preservados

Nao houve alteracao de regra de negocio, backend funcional, banco, DTO publico, rota, Premium, pagamento, Pix/Efi, upload, WhatsApp, e-mail, webhook, importador real, producao, VPS, remote ou push.

Identificadores tecnicos, slugs, nomes de scripts, nomes de containers, nomes de fixtures e parametros internos que usam `local` ou `sintetico` foram preservados quando nao sao copy renderizada ao usuario.
