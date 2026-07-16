# HOMOLOGACAO - Importacao real e dry-run

## Principio

Importacao real so pode existir apos fonte autorizada, restore/sanitizacao aprovados, dry-run obrigatorio e revisao Pro. Este documento define contrato; nao executa importacao.

## Entradas obrigatorias

- Fonte autorizada formalmente.
- Copia sanitizada ou base autorizada fora do repositorio.
- Inventario de arquivos sem expor conteudo sensivel.
- Mapeamento de campos legado -> V3.
- Relatorio de divergencias antes de qualquer escrita operacional.
- Plano de rollback.

## Dry-run obrigatorio

O dry-run deve processar a fonte sem gravar dados definitivos. O relatorio deve conter apenas agregados e status, nunca dado real bruto.

Cobertura minima:

- slugs;
- cidades;
- bairros;
- status;
- classificacao `LIVRE`/`BLOQUEADO`;
- midia publica;
- documento privado;
- Premium/beneficios;
- metricas agregadas;
- duplicidade;
- orfaos/FK;
- divergencias de schema;
- registros rejeitados;
- logs sanitizados.

## Regras por dominio

| Tema | Contrato |
| --- | --- |
| Slugs | Preservar quando seguro; conflito exige relatorio e decisao humana. |
| Cidades/bairros | Normalizar sem perder rastreabilidade. |
| Status | Mapear para estados V3 aprovados; desconhecido bloqueia escrita definitiva. |
| Classificacao | Apenas `LIVRE` ou `BLOQUEADO`; sem estados intermediarios. |
| Midia publica | Apenas midia aprovada pode gerar URL publica futura. |
| Documento privado | Nunca entra em DTO publico e nunca vira midia publica. |
| Premium/beneficios | Importar como leitura/estado validado, sem promessa financeira automatica. |
| Metricas | Agregadas e minimizadas; sem IP bruto, user-agent bruto ou contato real em relatorio. |
| Duplicidade | Resolver por regra documentada antes de gravacao definitiva. |
| Orfaos/FK | Bloqueiam promocao para staging final sem decisao Pro/humana. |

## Logs e relatorios

- Logs brutos ficam fora do repositorio.
- Relatorios versionados devem conter apenas agregados.
- Divergencias devem usar contagens, chaves tecnicas sanitizadas e status.
- Nenhum documento, WhatsApp, e-mail real, URL privada ou storage key pode aparecer em relatorio versionado.

## KYC privado

- O vinculo da pasta documental deve ser comprovado na origem e depois resolvido pelo mapa canonico de usuarios; o nome do diretorio nunca substitui o mapeamento origem -> V3.
- A varredura aceita recursao abaixo de `usuarios/{id}/documentos/`, mas somente referencia de banco conciliada, binario privado valido e parte comprovada podem entrar no operacional.
- PDF unico e imagens frente/verso sao suportados. Mesmo usuario e checksum consolidam; checksum associado a usuarios diferentes, conflito documental ou objeto sem autoridade de banco exige revisao manual.
- Status historico confiavel e preservado. A ausencia ou ambiguidade do arquivo de um usuario aprovado nao autoriza rebaixamento nem exigencia automatica de novo envio.
- Para a origem atual, decisao administrativa explicita prevalece; sem ela, a existencia de documento persistido reproduz o estado `Conta verificada` exibido pelo frontend legado. `is_verificado` confirma conta/e-mail e nao e fonte de KYC.
- Contagens de status devem usar `count(distinct usuario.id)` e o mesmo corte transacional do snapshot. Crescimento posterior da producao deve ser registrado separadamente, nunca agregado ao manifesto anterior.
- O destino usa somente `ObjectStorage` privado no prefixo isolado do dry-run. URL publica e proibida; acesso administrativo usa URL temporaria curta e nao registrada em evidencia.
- A prova de idempotencia exige dois PostgreSQL 17 e dois escopos R2 descartaveis independentes: primeira execucao reconcilia, segunda cria zero registro/objeto e os fingerprints normalizados devem coincidir.

## Rollback

- Importacao definitiva exige backup anterior.
- Cada lote deve ter identificacao de execucao.
- Reversao deve ser testada antes de cutover.
- Falha em duplicidade, FK, classificacao ou documento privado bloqueia Go.

## Gate Pro

Pro e obrigatorio antes de usar dados reais/sanitizados operacionais, executar escrita definitiva ou promover resultado para homologacao/cutover.
