# Relatorio - wizard producao somente leitura

## Consulta

- URL consultada: `https://topsdojob.com/anunciar`.
- Tipo: navegacao publica somente leitura.
- Resultado: redirecionamento para `https://topsdojob.com/?next=%2Fanunciar`.
- Motivo observado: aviso de conteudo adulto/age gate antes de liberar o destino.

## Limite de seguranca

Nao foi clicado em `Aceitar` no aviso adulto, pois isso completaria verificacao etaria em producao. O Bloco 34 nao recebeu autorizacao explicita para completar age gate em nome do usuario.

## Evidencia obtida

- Marca e cabecalho publico.
- CTA `PUBLICAR SEU ANUNCIO`.
- Busca publica.
- Categorias em destaque.
- Secao de confianca/seguranca.
- Aviso de conteudo adulto.
- Redirecionamento com `next=/anunciar`.

## Prints

- `docs/v3/evidencias/bloco-34/prints/producao/desktop-anunciar-producao.png`.
- `docs/v3/evidencias/bloco-34/prints/producao/mobile-anunciar-producao.png`.

Os prints contem apenas pagina publica observavel e nao incluem dados reais sensiveis.

## Confirmacoes

- Producao alterada: nao.
- Login executado: nao.
- Formulario enviado: nao.
- Upload real: nao.
- Pagamento/Pix/Efi: nao.
- E-mail/WhatsApp real: nao.
- Banco/VPS/SQL: nao.
- API externa autenticada: nao.
