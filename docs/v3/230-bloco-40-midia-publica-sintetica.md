# Bloco 40 - Mídia pública sintética

O Bloco 40 cria checkpoint local do Bloco 39 e valida, em ambiente local descartável, a exposição pública de mídia sintética. O escopo é somente local, sem dados reais, sem produção, sem VPS, sem restore, sem upload real, sem CDN/storage real, sem Pix/Efí real, sem pagamento e sem API externa.

## Checkpoint do Bloco 39

- Commit local criado: `a3e92c0`.
- Mensagem: `test: valida agegate whatsapp sintetico ate bloco 39`.
- `git remote -v`: vazio.
- Push executado: não.

## Escopo validado

- Anúncio gratuito permanece útil e pode ter até 2 fotos públicas sintéticas.
- Premium é aditivo e pode indicar benefício de mídia extra, sem compra real ou promessa de contratação.
- Mídia pendente fica representada por placeholder público seguro.
- `BLOQUEADO` não expõe mídia sensível antes da confirmação de idade.
- Stories exigem idade; quando liberados localmente, retornam apenas pendência segura de CDN, sem URL real.
- Documento privado nunca vira mídia pública.
- Admin lê mídia sanitizada, sem bucket, chave de storage, provider, hash, URL privada ou mídia real.

## Ajustes de apresentação

- O rótulo público de benefício de mídia extra foi padronizado para `Mídia extra`.
- A descrição SEO pública deixou de usar copy técnica como `Metadados publicos locais para ANUNCIO`.
- O campo técnico `tipoRota` permanece preservado no DTO; apenas `title` e `description` públicos foram naturalizados.

## Limites mantidos

- Nenhum upload real.
- Nenhum bucket, provider, storage key, hash, URL privada ou CDN real.
- Nenhum documento real.
- Nenhum uso de dados reais, produção, VPS, restore ou banco de produção.
- Nenhuma alteração de migration, banco, pagamento, Pix/Efí, checkout ou arquitetura.

## Validador

O script `scripts/local/validar-midia-publica-sintetica-local.ps1` roda em dois modos:

- sem `BaseUrl`: inicia E2E descartável com prefixo próprio `topsv3-midia-publica-sintetica`;
- com `BaseUrl`: executa somente os checks HTTP locais contra backend já disponível.

O resultado esperado é `OK_MIDIA_PUBLICA_SINTETICA_LOCAL`.
