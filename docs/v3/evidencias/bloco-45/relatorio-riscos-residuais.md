# Relatorio de riscos residuais do Bloco 45

## Riscos residuais

- Flyway real permanece pendente localmente porque CLI/imagem Flyway nao estavam disponiveis.
- A validacao SQL estatica e a validacao PostgreSQL descartavel por caminhos anteriores nao substituem o gate Flyway real para homologacao/producao.
- Homologacao/cutover continuam bloqueados ate execucao real de Flyway em ambiente descartavel/controlado ou ambiente de homologacao autorizado.

## Controles aplicados

- O script `scripts/local/validar-flyway-real-local.ps1` nao aprova por fallback `psql`.
- Pendencia operacional retorna exit `2`.
- Instalacao automatica e `docker pull` continuam proibidos sem autorizacao.
- Recursos Docker do Flyway usam prefixo `topsv3-flyway-local` apenas quando Flyway real estiver disponivel.

## Proibicoes preservadas

- Sem dados reais.
- Sem producao, VPS, restore ou staging.
- Sem Pix/Efi real, webhook, pagamento real ou API externa.
- Sem migration nova.
- Sem alteracao de SQL de schema.
- Sem push e sem remote.
