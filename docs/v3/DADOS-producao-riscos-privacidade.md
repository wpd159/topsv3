# Dados de producao - riscos de privacidade

## Riscos principais

| Risco | Severidade | Mitigacao | Estado |
| --- | --- | --- | --- |
| Backup bruto no repo | critica | bloquear caminhos dentro de `C:\topsv3` | controlado |
| Backup bruto no ZIP | critica | pacote usa inventario e staged files | controlado |
| Slug real em relatorio | alta | placeholders sanitizados | controlado |
| Telefone/e-mail/documento em doc | critica | scanners e relatorios agregados | controlado |
| Midia real copiada | critica | proibicao de copiar fotos/videos/docs | controlado |
| Restore em banco de producao | critica | somente Docker/local | controlado |
| Sanitizacao incompleta | alta | gate `validar-dados-producao-sanitizados-local.ps1` | pendente |
| SEO com paginas reais sensiveis | alta | relatorio sanitizado sem slugs reais | pendente |
| Cliente PostgreSQL incompatível | media | disponibilizar toolchain local compativel | pendente |

## Risco residual Bloco 29

O principal risco residual e operacional: o restore nao foi executado por falta de cliente PostgreSQL compativel com o dump. Nao houve exposicao adicional de dados porque o conteudo do backup nao foi aberto nem versionado.

## Risco residual Bloco 29.1

O SHA-256 do backup foi validado, mas o risco operacional permanece bloqueante: sem cliente PostgreSQL 17.x local nao ha restore, sanitizacao real ou validacao SEO agregada com dados sanitizados. Qualquer download de imagem deve ter autorizacao consciente do usuario e nunca pode envolver producao como bancada.

## Risco residual Bloco 29.2

O download autorizado `docker pull postgres:17` falhou por Docker daemon indisponivel. O risco principal segue operacional, nao de exposicao: o backup nao foi aberto, nao foi convertido para SQL bruto e nao foi versionado. A proxima tentativa exige Docker daemon local disponivel e manutencao de `--pull=never` nos `docker run` posteriores.

## Risco residual Bloco 29.3

O restore bruto falhou depois de criar recursos Docker proprios `topsv3-bloco29-*`. Ha restauracao parcial no container bruto por contagem estrutural agregada, portanto ele deve permanecer isolado e nunca ser conectado pela aplicacao V3.

Nao houve exposicao versionada de valores reais, mas a sanitizacao ainda nao foi executada. Nova tentativa exige decisao explicita sobre limpeza/recriacao dos recursos proprios, sem tocar em TopsWI/terceiros.

## Risco residual Bloco 29.4

O uso de `--single-transaction` evitou nova restauracao parcial, mas a falha `CONSTRAINT/FK` persiste e exige revisao Pro/humana do raw log externo.

O risco principal agora e operacional: nao ha banco sanitizado valido. O raw log, backup e dump permanecem fora do repositorio e fora do ZIP; nao houve exposicao versionada de dados reais.

## Risco residual Bloco 29.5

Mesmo sanitizada, a quarentena sem `POST_DATA` pode nao representar integridade referencial final. Ela reduz risco de exposicao de dados para diagnostico agregado, mas nao elimina a necessidade de backup consistente ou plano local aprovado para orfaos.

Resultado: risco de exposicao versionada segue controlado; risco operacional de integridade permanece por ausencia de `POST_DATA` e por orfandades agregadas no dominio anuncio.

## Risco residual Bloco 30

O Bloco 30 nao acessa producao, VPS, Docker, banco de producao, backup, dump, SQL bruto, log bruto, midia real ou documento real. O risco de exposicao versionada permanece controlado porque o desenvolvimento segue com fixture sintetica local.

O risco operacional da frente de dados reais permanece adiado: a quarentena sanitizada nao e staging final, nao valida transacoes completas e nao substitui a Opcao A. Antes de homologacao/cutover real, continua obrigatorio obter novo backup consistente ou corrigir a origem/backup, com revisao Pro/humana.

Novos arquivos versionaveis do Bloco 30 devem conter apenas dados artificiais, dominio `example.test`, placeholders nao discaveis, metricas agregadas e ausencia de CPF/RG/documento, IP bruto, storage real, token, Pix/Efi real, e-mail real ou WhatsApp real.
