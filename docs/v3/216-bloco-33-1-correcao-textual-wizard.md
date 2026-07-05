# Bloco 33.1 - correcao textual do wizard Anuncie gratis

## Objetivo

Corrigir apenas textos publicos sem acentuacao no wizard `/anunciar`, mantendo intactos contratos, IDs internos, validacoes, DTOs, rotas, regras de negocio e seguranca.

Nao precisa Pro para este bloco porque o escopo e local, sintetico e textual/UI.

## Contexto

- Bloco 33 auditado: OK.
- ZIP auditado do Bloco 33: `C:\Users\WpD\Desktop\topsv3-fase-BLOCO-33-2026-07-05-144142-485.zip`.
- SHA-256 auditado: `3fdbf0e51ae312f0fcbaedb6721c397caa93cfe150c74d91919594bf7d1cad63`.
- HEAD local: `f6189f0`.
- Delta do Bloco 33 permanece staged.
- Checkpoint do Bloco 33 continua pendente para bloco posterior.

## Textos corrigidos

No wizard publico foram corrigidos textos de apresentacao, incluindo:

- `Revisao final` para `Revisão final`;
- `Solicitacao recebida` para `Solicitação recebida`;
- `Confirmacoes` para `Confirmações`;
- `Etapa concluida.` para `Etapa concluída.`;
- `botao final` para `botão final`;
- outros textos publicos do wizard com acentuacao simples, como `Início`, `Anúncio`, `Dados básicos`, `Nome para exibição`, `Localização`, `Fotos e vídeos`, `Premium obrigatório`, `moderação`, `política`, `Formulário` e `WhatsApp válido`.

IDs internos como `basicos`, `localizacao`, `midia`, `revisao` e `sucesso` permanecem sem acento por serem identificadores tecnicos.

## Validador

O script `scripts/local/validar-wizard-anunciar-sintetico-local.ps1` foi reforcado para reprovar textos publicos criticos sem acento quando eles aparecem no `bodyText` renderizado:

- `Revisao final`;
- `Solicitacao recebida`;
- `Confirmacoes`;
- `Etapa concluida`;
- `botao final`.

O validador verifica apenas texto renderizado ao visitante; ele nao reprova IDs internos, rotas, nomes de arquivo ou conteudo tecnico nao renderizado.

## Garantias

- Nenhuma regra de negocio foi alterada.
- Nenhum fluxo real foi usado.
- Nenhum dado real foi usado.
- Nenhuma producao, VPS, banco de producao, restore, sanitizacao real, Pix/Efi real, pagamento real, upload real, e-mail real, WhatsApp real ou API externa foi acessada.
- Nenhuma migration, SQL de schema, remote, push ou commit foi executado.
- Bloco 29 segue adiado.
- Quarentena sem `POST_DATA` segue proibida para staging final.
