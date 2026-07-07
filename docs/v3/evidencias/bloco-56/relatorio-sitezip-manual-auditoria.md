# Relatorio site.zip manual - Bloco 56

## Decisao

O `site.zip` manual usado em auditoria anterior deve ser tratado como artefato excepcional e confidencial.

## Motivo

O artefato manual continha backup/logs e, por isso, nao representa pacote oficial de revisao gerado pelo empacotador higienizado do projeto.

## Regras

- Nao tratar o `site.zip` manual como falha do empacotador oficial.
- Nao copiar backup, dump, log bruto, midia real, documento real, `.env`, certificado, chave, token ou banco local para docs, Git ou novo ZIP.
- Manter pacotes oficiais gerados apenas pelo fluxo higienizado de entrega.
