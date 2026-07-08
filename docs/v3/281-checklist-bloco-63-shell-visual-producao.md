# Checklist - Bloco 63

## Escopo

- [x] Bloco 62 lido.
- [x] `C:\clone\topsdojob-frontend` usado somente leitura.
- [x] Alteracoes limitadas ao frontend V3 e docs do bloco.
- [x] Shell visual de producao iniciado.
- [x] Sem migrar wizard completo, detalhe, filtros avancados, admin, moderacao, Premium ou age gate.

## Implementacao

- [x] Header publico aproximado da producao.
- [x] Logo local preservada em `/logo.webp`.
- [x] Hero da home com imagem de fundo do clone.
- [x] Busca da home mantida como link seguro interno, sem backend antigo.
- [x] Categorias visuais com assets do clone.
- [x] Footer publico criado.
- [x] Footer ligado a home, cidade, bairro, anuncio e shell institucional.
- [x] CSS global ajustado sem Tailwind/Radix antigos.

## Proibicoes

- [x] Sem backend antigo.
- [x] Sem auth antigo.
- [x] Sem upload real.
- [x] Sem Pix/Efi real.
- [x] Sem pagamento real.
- [x] Sem webhook real.
- [x] Sem API externa real.
- [x] Sem push.
- [x] Sem alteracao em `C:\clone`.

## Validacoes

- [x] `npm install`.
- [x] `npm run lint`.
- [x] `npm run build`.
- [x] `scripts/security/verificar-codificacao.ps1`.
- [x] `scripts/security/verificar-arquivos-proibidos.ps1`.
- [x] `scripts/security/verificar-segredos.ps1`.
- [x] `git diff --check`.
- [x] `git status --short`.
