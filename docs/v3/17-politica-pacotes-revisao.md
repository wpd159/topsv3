# Política de pacotes de revisão

## Objetivo

Toda execução futura do Codex deve produzir um pacote ZIP de revisão para permitir auditoria externa do que foi efetivamente criado ou modificado naquela execução.

## Regra permanente

Ao final de cada execução futura:

- criar inventário inicial antes de qualquer alteração;
- criar o inventário fora do repositório com `scripts/entrega/criar-inventario-inicial.ps1`;
- executar a tarefa da fase;
- identificar arquivos criados ou modificados por comparação de SHA-256 do índice Git entre inventário inicial e final;
- bloquear a geração se existir qualquer alteração fora do índice Git;
- executar scans e validações;
- criar um ZIP na Área de Trabalho;
- preservar a estrutura relativa a `C:\topsv3`;
- incluir `MANIFESTO-ARQUIVOS.csv`;
- incluir `RESUMO-ENTREGA.md`;
- incluir `RELATORIO-VALIDACOES.md`;
- incluir relatórios internos de testes quando gerados na execução;
- validar hashes internos;
- importar e validar o `MANIFESTO-ARQUIVOS.csv` real extraído do ZIP;
- escanear arquivos de controle antes da compactação e depois da extração;
- excluir arquivos proibidos;
- nunca adicionar o ZIP ao Git.

## Seleção de arquivos

O ZIP deve conter somente arquivos criados ou modificados naquela execução.

Não devem entrar:

- arquivos staged de fases anteriores que não mudaram na execução;
- arquivos ignorados;
- arquivos sensíveis;
- arquivos deletados, exceto como menção no resumo;
- inventário temporário inicial;
- diretório temporário de montagem;
- o próprio ZIP.

A seleção deve usar inventário inicial e final, não apenas `git diff`. O hash decisivo é o hash do conteúdo staged no índice Git, porque o ZIP entrega exatamente o que seria commitado ou usado como checkpoint staged quando o commit local opcional ainda não existir.

Antes de montar o pacote, o gerador deve executar `git diff --name-only -z` e `git ls-files --others --exclude-standard -z`. Qualquer arquivo tracked modificado fora do staging, arquivo novo unstaged ou deleção unstaged deve retornar exit code `2` com a mensagem:

```text
Existem alterações fora do índice Git. Execute a revisão e o staging antes de gerar o pacote.
```

## Inventário inicial

O inventário inicial deve ser CSV UTF-8 com BOM, criado antes de qualquer alteração e fora do repositório. As colunas oficiais, nesta ordem, são:

- `caminho_relativo`;
- `arquivo_presente_no_workspace`;
- `arquivo_presente_no_indice`;
- `modo_git_indice`;
- `tamanho_workspace`;
- `tamanho_indice`;
- `sha256_workspace`;
- `sha256_indice`.

O gerador de pacote deve validar o CSV real antes de usá-lo. Devem ser rejeitados BOM ausente, UTF-8 inválido, coluna ausente, coluna extra, coluna fora de ordem, caminho vazio, caminho absoluto, traversal, barra invertida, duplicidade exata ou por caixa, booleano inválido, tamanho inválido, SHA-256 inválido, modo Git inválido e inconsistência entre presença no workspace/índice e campos obrigatórios.

## Manifesto

`MANIFESTO-ARQUIVOS.csv` deve conter:

- `caminho_relativo`;
- `tipo_alteracao`;
- `tamanho_bytes`;
- `sha256`.

`tipo_alteracao` deve ser `CRIADO` ou `MODIFICADO`.

O SHA-256 deve corresponder ao arquivo efetivamente armazenado dentro do ZIP.

Após a criação do ZIP, o manifesto usado na validação deve ser o CSV extraído do próprio pacote. O script deve exigir UTF-8 com BOM, fazer parsing real do CSV, rejeitar colunas ausentes ou extras, linha vazia, caminho vazio, tipo inválido, tamanho não numérico, SHA-256 inválido, caminho duplicado, caminho absoluto, `..`, barra invertida e qualquer entrada que não exista no ZIP.

## Escopo do manifesto e controles

O `MANIFESTO-ARQUIVOS.csv` cobre apenas arquivos versionaveis criados ou modificados no repositorio desde o inventario inicial. Arquivos de controle do pacote, como `RESUMO-ENTREGA.md` e `RELATORIO-VALIDACOES.md`, sao entradas obrigatorias do ZIP e passam por validacao e scanner, mas nao sao linhas do manifesto de arquivos do repositorio.

Essa separacao evita misturar artefatos gerados pelo empacotador com o conjunto auditado de mudancas do workspace, sem reduzir a validacao dos controles internos.

## Resumo

`RESUMO-ENTREGA.md` deve conter:

- projeto;
- fase;
- data e hora;
- objetivo;
- workspace;
- branch Git;
- arquivos criados;
- arquivos modificados;
- arquivos removidos, se houver;
- validações executadas;
- testes aprovados e falhas;
- uso de gitleaks ou fallback;
- commit local executado ou não;
- hash do commit, se houver;
- motivo da ausência de commit, quando aplicável;
- confirmação de que nenhum código de aplicação foi criado quando esse for o escopo;
- confirmação de que nenhuma migration foi criada quando esse for o escopo;
- confirmação de que nenhuma integração externa foi acessada;
- confirmação de que o ZIP não contém secrets ou dados reais.

O resumo não deve conter afirmações fixas que o script não recebeu por metadados da execução. Quando o script não souber um fato, deve registrar `não informado` ou bloquear a entrega se o fato for obrigatório para a fase.

## Relatório de validações

`RELATORIO-VALIDACOES.md` deve conter:

- comandos executados;
- exit codes;
- testes aprovados;
- testes com falha;
- scanner utilizado;
- validação de codificação;
- validação do manifesto;
- confirmação de que valores sensíveis não foram exibidos.

## Arquivos proibidos

O ZIP nunca pode conter:

- `.git`;
- `.env` real;
- credenciais;
- secrets;
- tokens;
- certificados;
- chaves privadas;
- certificado Efí;
- senha do certificado;
- dumps;
- backups;
- bancos locais;
- logs;
- storage local;
- uploads;
- dados reais;
- exportações de usuários;
- payload financeiro;
- `node_modules`;
- `.next`;
- `target`;
- caches;
- arquivos ignorados;
- archives internos como `.zip`, `.7z`, `.rar`, `.tar`, `.tgz`, `.gz`, `.bz2`, `.xz` e `.iso`.

Arquivos `.env.example` e exemplos equivalentes só podem entrar quando forem criados ou modificados na execução, contiverem valores fictícios e passarem pelos scanners.

Arquivos `.example` não podem esconder archive, certificado, keystore, dump ou banco. Extensões perigosas em qualquer posição relevante do nome bloqueiam o pacote.

## Validação

Após criar o ZIP, o próprio script deve:

- abrir o ZIP programaticamente;
- rejeitar caminhos absolutos;
- rejeitar `..`;
- rejeitar entradas duplicadas;
- listar entradas internas;
- comparar entradas reais com entradas esperadas;
- confirmar estrutura relativa;
- confirmar presença de `MANIFESTO-ARQUIVOS.csv`, `RESUMO-ENTREGA.md` e `RELATORIO-VALIDACOES.md`;
- confirmar ausência de arquivos proibidos;
- recalcular SHA-256 dos arquivos extraídos temporariamente;
- comparar com o manifesto;
- validar codificação dos arquivos textuais do pacote;
- validar o manifesto real extraído do ZIP contra o manifesto esperado em memória;
- recalcular hashes com base nas linhas do manifesto real;
- escanear `MANIFESTO-ARQUIVOS.csv`, `RESUMO-ENTREGA.md`, `RELATORIO-VALIDACOES.md` e relatórios de testes;
- rejeitar UTF-16, UTF-32, NUL e mojibake em arquivos textuais;
- rejeitar symlink e gitlink no índice Git;
- rejeitar entradas duplicadas por diferença de caixa no Windows;
- confirmar que o ZIP está fora do repositório;
- calcular SHA-256 do próprio ZIP;
- confirmar que o ZIP não foi adicionado ao Git;
- remover a pasta temporária de validação.

Se qualquer validação falhar, o ZIP parcial ou inválido deve ser removido e a execução deve retornar erro operacional.

## Commit e publicação

Commit local é opcional para esta etapa e a ausência de `user.name` ou `user.email` local não bloqueia a Fase 1A. Enquanto não houver commit, o índice Git staged e os ZIPs validados são checkpoints formais.

Nenhum remote, push, deploy, publicação ou envio externo deve ocorrer sem autorização explícita futura.

## Temporários

Testes de pacote devem manter repositórios, inventários, metadados, destinos, extrações e ZIPs de teste sob um único diretório temporário da suíte. O bloco final deve remover esse diretório integralmente e validar que ele deixou de existir.
