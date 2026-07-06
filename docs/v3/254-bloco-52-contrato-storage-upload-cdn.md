# Bloco 52 - Contrato storage/upload/CDN

## Objetivo

O Bloco 52 fecha o checkpoint local do Bloco 51 e cria o contrato tecnico de storage, upload e CDN para futura homologacao. O bloco nao executa upload, nao cria bucket real, nao acessa R2/S3/CDN real, nao usa dado real e nao chama API externa.

## Checkpoint

- Commit local do Bloco 51: `eacecaa2 docs: define contrato homologacao ate bloco 51`.
- Remote: vazio.
- Push: nao executado.
- Inventario inicial do Bloco 52 salvo fora do repositorio.

## Contrato criado

- `docs/v3/HOMOLOGACAO-storage-upload-cdn.md`

## Escopo do contrato

- Midia publica de anuncio.
- Stories.
- Midia pendente.
- Midia rejeitada.
- Documento privado.
- Separacao de buckets/containers.
- URL publica apenas para midia aprovada.
- URL privada e storage key nunca expostas em DTO publico.
- Placeholder seguro para midia sem URL.
- Limite gratuito de 2 fotos e Premium/fotos extras.
- Expiracao conjunta de beneficios.
- Remocao logica versus remocao fisica futura.
- Retencao, antivirus, moderacao, CDN/cache e rollback.
- Variaveis obrigatorias sem valores reais.

## Decisao

Storage/upload/CDN real continua pendente para homologacao futura. Documento privado nunca vira midia publica. Antes de qualquer upload real, o contrato deve ser revisado com Pro e validado em ambiente isolado.

## Limites preservados

Sem producao, VPS, dados reais, storage real, CDN real, R2/S3 real, API externa, Pix/Efi, webhook, remote, push ou fase posterior.
