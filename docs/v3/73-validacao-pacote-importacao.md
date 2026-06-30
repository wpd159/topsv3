# Validacao do pacote de importacao

Status da entrega: `VALIDACAO ESTRUTURAL EM MEMORIA`.

## Escopo

`ValidadorPacoteEntradaImportacao` valida apenas o DTO `PacoteEntradaImportacaoDto`. Ele nao abre arquivo, nao consulta filesystem, nao calcula checksum real, nao conecta banco, nao acessa storage, nao chama API externa e nao interpreta dump.

## Regras implementadas

O pacote e invalido quando:

- descritor do pacote esta ausente;
- identificador logico esta ausente;
- versao esta ausente;
- data/hora declarada da extracao esta ausente;
- origem esta ausente;
- lista de arquivos esta vazia;
- tipo obrigatorio de arquivo nao foi declarado;
- arquivo obrigatorio foi declarado como `AUSENTE`;
- tipo de arquivo foi declarado mais de uma vez;
- checksum obrigatorio esta ausente.

O pacote pode continuar estruturalmente valido com alertas quando:

- checksum esta ausente em tipo que nao exige checksum nesta fase.

## Pendencias criadas

Foram adicionados codigos de pendencia estruturais:

- `PACOTE_DESCRITOR_AUSENTE`;
- `PACOTE_SEM_IDENTIFICADOR`;
- `PACOTE_SEM_VERSAO`;
- `PACOTE_SEM_EXTRACAO_DECLARADA`;
- `PACOTE_SEM_ORIGEM`;
- `PACOTE_SEM_ARQUIVOS`;
- `PACOTE_ARQUIVO_DUPLICADO`;
- `PACOTE_ARQUIVO_OBRIGATORIO_AUSENTE`;
- `PACOTE_CHECKSUM_AUSENTE`;
- `PACOTE_CHECKSUM_OBRIGATORIO_AUSENTE`.

Tambem foram adicionados tipos estruturais de entidade para relatorio:

- `PACOTE_IMPORTACAO`;
- `ARQUIVO_PACOTE_IMPORTACAO`.

Esses identificadores sao tecnicos e nao representam tabelas, entidades JPA ou schema de banco.

## Checksum

Tipos que exigem checksum nesta fase:

- `DUMP_BANCO_LEGADO`;
- `MANIFESTO_MIDIA`;
- `EXPORT_PAGAMENTOS`;
- `EXPORT_CREDITOS`;
- `EXPORT_PREMIUM`.

Demais tipos sem checksum geram `PACOTE_CHECKSUM_AUSENTE` com severidade de alerta. A decisao evita bloquear pacote futuro por metadado incompleto que ainda pode ser saneado, mas preserva bloqueio maior para itens financeiros, premium, dump e manifesto de midia.

## Sem I/O

O validador nao recebe `Path`, `File`, stream, datasource, repository, storage client ou client HTTP. O campo `caminhoDeclaradoSanitizado` e tratado como texto declarativo.

Isso preserva as garantias da fase:

- dump real nao e lido;
- caminho real nao e validado por filesystem;
- nenhum arquivo de entrada e aberto;
- nenhum dado real e inspecionado;
- validacao e apenas estrutural do DTO.

## Complemento da Fase 2C

`ValidadorDicionarioImportacao` valida o catálogo de campos esperado por tipo de arquivo. Ele segue a mesma restrição do pacote de entrada: não abre arquivo, não lê filesystem, não conecta banco e não chama rede.

O dicionário de campos ajuda a decidir, em fase futura, quais campos são obrigatórios, sensíveis, saneáveis, bloqueantes ou dependentes de evidência.

## Testes planejados

Foi criado teste unitario para:

- pacote sem identificador invalido;
- pacote sem versao invalido;
- pacote sem arquivo obrigatorio gera pendencia;
- validador nao tenta ler filesystem;
- exemplo sanitizado nao contem URL real, telefone, CPF ou e-mail real.

Os testes dependem de Maven/JUnit local. Se executor local nao existir sem download, a execucao permanece pendente e apenas `javac` do pacote principal pode ser usado.
