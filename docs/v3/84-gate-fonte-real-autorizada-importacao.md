# Gate de fonte real autorizada da importacao

Status da entrega: `GATE OPERACIONAL LOCAL, SEM FONTE REAL`.

## Objetivo

A Fase 2F define o gate para uso futuro de fonte real de importacao. Esta fase nao usa fonte real, nao le dump, nao abre arquivo de origem, nao processa midia, nao processa pagamentos, nao processa metricas, nao acessa banco e nao inicia importacao.

O gate existe para impedir que um pacote real seja copiado para o repositorio ou usado sem autorizacao expressa.

## Estado atual

- Fonte real ainda nao foi usada.
- Dump real ainda nao foi lido.
- Midia real ainda nao foi lida.
- Pagamentos reais ainda nao foram processados.
- Metricas reais ainda nao foram processadas.
- Pacote real ainda nao foi recebido nesta fase.
- Importacao real continua bloqueada.

## Condicoes para fase futura

Um pacote real so podera ser usado em fase futura quando houver:

- autorizacao expressa;
- revisao Pro do schema ou decisao formal equivalente;
- origem declarada;
- data/hora de extracao declarada;
- responsavel pela geracao declarado;
- manifesto;
- checksums;
- confirmacao de que a origem nao veio de acesso indevido a producao;
- confirmacao de que o pacote esta fora do repositorio;
- confirmacao de que relatorios anexaveis ou publicos foram sanitizados;
- aprovacao especifica para dry-run real ou importacao real.

## Armazenamento proibido

Pacote real nao pode ser copiado para:

- `docs`;
- `backend`;
- `frontend`;
- `scripts`;
- qualquer pasta versionada;
- qualquer subdiretorio de `C:\topsv3`;
- qualquer repositorio Git.

Diretorios locais como `dados-importacao/`, `importacao-real/`, `pacote-importacao-real/`, `entrada-importacao/`, `legacy-dump/` e `legacy-media/` estao proibidos no Git por `.gitignore`, mas o uso correto continua sendo manter a fonte real fora do workspace.

## Manuseio de dados sensiveis

Qualquer dado real deve ser tratado como material operacional temporario. Isso inclui:

- dump;
- CSV/export;
- JSON real;
- planilhas;
- midias;
- documentos privados;
- pagamentos;
- creditos;
- metricas;
- logs de origem;
- manifestos que identifiquem objetos reais.

Relatorios publicos, anexaveis ao ZIP ou versionaveis devem ser sanitizados e nao podem conter dado pessoal, documento, telefone, e-mail real, path real, bucket real, chave real, URL privada, hash de arquivo real ou conteudo de anuncio.

## Validacao local

O script `scripts/local/validar-fonte-importacao-local.ps1` valida apenas metadados de caminho/listagem.

Quando nenhum diretorio e informado, ele retorna `0` e registra que nenhuma fonte real foi validada.

Quando um diretorio for informado em fase futura, ele deve:

- bloquear pacote dentro de `C:\topsv3`;
- bloquear pacote dentro do repositorio Git;
- alertar se a pasta parecer sincronizada/publica;
- verificar presenca nominal de manifesto e checksums;
- nao abrir conteudo de arquivo;
- nao calcular checksum;
- nao acessar rede;
- nao acessar banco;
- nao alterar arquivos de origem.

## Bloqueios

Importacao real permanece bloqueada se:

- a fonte nao estiver autorizada;
- o pacote estiver dentro do repositorio;
- faltar manifesto;
- faltar checksums;
- faltar origem declarada;
- faltar responsavel pela geracao;
- houver duvida de acesso indevido a producao;
- houver risco de vazamento;
- schema seguir `AGUARDANDO_REVISAO_PRO`;
- houver pendencia critica no plano/dry-run.

## Complemento 2G

A Fase 2G consolidou este gate no dossie de transicao. O gate permanece bloqueante e nao autoriza fonte real, dry-run real, ETL ou importacao.
