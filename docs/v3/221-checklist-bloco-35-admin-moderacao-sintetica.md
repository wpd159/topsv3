# Checklist - Bloco 35

- [x] Diagnostico inicial executado.
- [x] Remote vazio confirmado.
- [x] Arquivos sensiveis ausentes no repo.
- [x] Copy publica de bastidor removida do wizard `/anunciar`.
- [x] Copy de bastidor da etapa "Fotos e videos" removida do wizard `/anunciar`.
- [x] Validador do wizard endurecido contra textos de bastidor renderizados.
- [x] Wizard sintetico validado apos preliminar.
- [x] Render publico sintetico validado apos preliminar.
- [x] E2E sintetico validado apos preliminar.
- [x] Dados sinteticos validados.
- [x] Checkpoint local do Bloco 34 corrigido criado em `9b677ea`.
- [x] Hash do checkpoint Bloco 34 registrado.
- [x] Rotas e endpoints admin/moderacao auditados.
- [x] Validador admin/moderacao sintetica criado.
- [x] Admin/moderacao sintetica validada.
- [x] Prints admin desktop/mobile gerados.
- [x] UI admin sem `UPPER_SNAKE_CASE` renderizado no smoke do Bloco 35.
- [x] Validacoes finais executadas.
- [x] ZIP limpo do Bloco 35 gerado.

## Proibicoes verificadas

- [x] Sem producao alterada.
- [x] Sem VPS.
- [x] Sem dados reais.
- [x] Sem restore.
- [x] Sem `POST_DATA`.
- [x] Sem sanitizacao real.
- [x] Sem Pix/Efi real.
- [x] Sem pagamento real.
- [x] Sem upload real.
- [x] Sem e-mail real.
- [x] Sem WhatsApp real.
- [x] Sem remote.
- [x] Sem push.

## Resultado especifico

- [x] `scripts/local/validar-admin-moderacao-sintetica-local.ps1`: `OK_ADMIN_MODERACAO_SINTETICA_LOCAL`.
- [x] E2E descartavel: `OK_E2E_LOCAL_DESCARTAVEL`.
- [x] Docker usado apenas com prefixo `topsv3-admin-sintetico-*`.
- [x] Container e rede descartaveis removidos ao fim.
- [x] `cripto-*`/TopsWI detectado apenas para preservacao; nenhum stop/remove/prune/compose down executado.
- [x] Bloco 29 continua adiado e nao foi usado como staging.
