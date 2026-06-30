# Runbook de recebimento de pacote real

Status da entrega: `RUNBOOK PREVENTIVO, SEM PACOTE REAL`.

## Antes de receber

- Confirmar autorizacao expressa para a fase futura.
- Confirmar responsavel pela geracao.
- Confirmar origem da extracao.
- Confirmar que nao houve acesso indevido a producao.
- Confirmar que o destino operacional fica fora de `C:\topsv3`.
- Confirmar que o destino operacional nao e repositorio Git.
- Confirmar que nenhum arquivo real sera anexado a ZIP de revisao.

## Requisitos minimos do pacote futuro

O pacote real futuro deve conter, fora do repositorio:

- manifesto;
- checksums;
- origem declarada;
- data/hora de extracao;
- responsavel pela geracao;
- lista de arquivos esperados;
- escopo do pacote;
- declaracao de que dados sensiveis serao tratados como material operacional temporario.

## Passos de recebimento

1. Receber somente em diretorio operacional externo ao workspace.
2. Nao copiar para `docs`, `backend`, `frontend`, `scripts` ou qualquer pasta versionada.
3. Nao abrir dump, CSV, JSON real, midia, planilha, pagamento ou metrica nesta fase.
4. Executar apenas a validacao local de metadados em fase futura autorizada:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/local/validar-fonte-importacao-local.ps1 -DiretorioPacote "<diretorio-operacional-externo>"
```

5. Registrar resultado sem anexar dado real.
6. Sanitizar qualquer relatorio que precise ser versionado.
7. Bloquear dry-run real se houver pendencia critica.

## O que o script nao faz

- Nao le conteudo de arquivo.
- Nao calcula checksum de arquivo real.
- Nao abre dump.
- Nao abre CSV.
- Nao abre JSON real.
- Nao abre midia.
- Nao consulta banco.
- Nao chama rede.
- Nao envia dado a API externa.
- Nao altera arquivo de origem.
- Nao importa dados.

## Saidas esperadas

- `0`: nenhuma fonte informada ou metadados basicos aceitos.
- `1`: bloqueio critico, como pacote dentro do repositorio ou ausencia nominal de manifesto/checksums.
- `2`: pendencia operacional, como diretorio inexistente ou erro de leitura de metadados.

## Registro seguro

O registro versionavel deve conter apenas:

- fase;
- data da validacao;
- resultado agregado;
- pendencias sem dado real;
- nomes logicos sanitizados;
- decisao go/no-go.

Nao versionar paths reais, hashes reais de arquivos de origem, nomes reais de pessoas, documentos, telefones, e-mails, URLs privadas, buckets, chaves ou conteudo de anuncio.

## Complemento 2G

O runbook foi referenciado no dossie de transicao como requisito para qualquer fase futura com fonte real. Ele continua preventivo e nao representa recebimento de pacote real.
