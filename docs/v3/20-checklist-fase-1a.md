# Checklist da Fase 1A

## Inventário Inicial

- [x] raiz Git confirmada como `C:/topsv3`.
- [x] inventário inicial criado fora do repositório.
- [x] inventário salvo em CSV UTF-8 com BOM.
- [x] inventário não adicionado ao Git.

## Errata Não Bloqueante

- [x] ocorrências de verbo ser sem acento corrigidas quando aplicável.
- [x] obrigatoriedade escrita sem acento corrigida quando aplicável.
- [x] frase sobre integração Pix ativa corrigida quando aplicável.
- [x] palavras simples sem acento corrigidas em texto funcional.
- [x] identificadores técnicos preservados.

## Estrutura Local

- [x] `backend/` criado sem aplicação.
- [x] `frontend/` criado sem aplicação.
- [x] `infra/local/` criado.
- [x] `infra/staging/` criado como reserva documental.
- [x] `infra/producao/` criado como reserva documental.
- [x] `scripts/local/` criado.

## Infraestrutura Local

- [x] Docker Compose local criado.
- [x] PostgreSQL local preparado sem schema de domínio.
- [x] MinIO local S3-compatible preparado.
- [x] Mailpit preparado para captura local de e-mail.
- [x] volumes de runtime direcionados para `storage-local/`.
- [x] `.env.local.example` criado apenas com placeholders.

## Scripts Locais

- [x] script de validação de configuração local criado.
- [x] script de iniciar infraestrutura local criado.
- [x] script de parar infraestrutura local criado.
- [x] script de status local criado.
- [x] script de logs locais criado.
- [x] nenhum script acessa produção, VPS, banco externo ou Efí real.

## Validações

- [x] scanners de segurança executados.
- [x] testes dos hooks executados.
- [x] testes do pacote executados.
- [x] `git diff --check` executado.
- [x] `git diff --cached --check` executado.
- [x] ausência de remote confirmada.

## Saída da Fase

- [x] nenhum backend funcional criado.
- [x] nenhum frontend funcional criado.
- [x] nenhuma migration criada.
- [x] nenhum SQL de schema criado.
- [x] nenhum dado real criado.
- [x] nenhuma conexão externa criada.
- [x] nenhuma integração Efí real criada.
- [x] nenhum deploy executado.
- [x] nenhum commit executado.
- [x] nenhum push executado.
- [x] ZIP de revisão final gerado na Área de Trabalho.
