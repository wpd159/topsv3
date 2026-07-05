# Relatorio - riscos residuais Bloco 31

## Riscos residuais

| Risco | Estado | Mitigacao |
| --- | --- | --- |
| Producao/cutover ainda sem restore consistente | pendente | Bloco 29 segue gate adiado; Opcao A obrigatoria |
| `gitleaks` ausente no PATH | pendente | fallback local executado; gitleaks real permanece gate antes de producao |
| Fixture sintetica nao cobre todos os casos comerciais futuros | baixo | ampliar fixture em blocos locais futuros sem dados reais |
| E2E depende de Docker local | controlado | prefixo exclusivo, sem volume persistente, sem prune, sem compose down |
| Recursos TopsWI/cripto no Docker local | controlado | somente leitura; nenhum stop/rm/prune/compose down executado |

## Recursos Docker detectados e preservados

- `topsv3-bloco29-pg17-quarentena`;
- `topsv3-bloco29-pg17-sanitizado`;
- `topsv3-bloco29-pg17-bruto`;
- `cripto-postgres`;
- `cripto-redis`;
- network `topsv3-bloco29-net`;
- network `cripto_default`;
- volumes `topsv3-bloco29-pgdata-bruto`, `topsv3-bloco29-pgdata-quarentena`, `topsv3-bloco29-pgdata-sanitizado` e `cripto_postgres_dados`.

## Recursos Docker do Bloco 31

- Container temporario: prefixo `topsv3-e2e-sintetico-pg-*`.
- Network temporaria: prefixo `topsv3-e2e-sintetico-net-*`.
- Imagem: `postgres:17` ja local.
- Volume persistente criado: nao.
- Container temporario removido: sim.
- Network temporaria removida: sim.

## Confirmacoes

- Sem producao, VPS, banco de producao ou SQL em producao.
- Sem restore real, `POST_DATA`, sanitizacao real ou correcao de orfaos.
- Sem dump novo, SQL bruto/log bruto versionado, midia real ou documento real.
- Sem Pix/Efi real, pagamento, upload, e-mail real, WhatsApp real ou API externa real.
- Sem remote, push ou fase posterior.
