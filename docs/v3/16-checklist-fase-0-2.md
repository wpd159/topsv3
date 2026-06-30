# Checklist da Fase 0.2

## Errata SDD

- [x] `decisao_moderacao` usa `revisao_anuncio_id`.
- [x] a nomenclatura antiga de revisão não aparece como alternativa de moderação.
- [x] `anuncio_midia_revisao` usa ações `ADICIONAR`, `SUBSTITUIR`, `REMOVER`, `REORDENAR`.
- [x] Critérios financeiros exigem divergência financeira final zero.
- [x] ADR-003 está aprovado.
- [x] ADR-007 está aprovado.
- [x] ADR-011 foi criado como proposto.
- [x] Convenções técnicas globais foram adicionadas ao modelo de dados.

## Repositório Git

- [x] Git inicializado em `main`.
- [x] Nenhum remote configurado nesta fase.
- [x] `core.hooksPath` configurado localmente para `.githooks`.
- [x] `.gitignore` criado ou revisado.
- [x] `.gitattributes` criado.
- [x] `.editorconfig` criado.
- [x] README criado ou revisado.
- [x] CONTRIBUTING criado.
- [x] SECURITY criado.

## Proteção contra arquivos proibidos

- [x] `.env` é ignorado.
- [x] `storage-local/` é ignorado.
- [x] `backups-local/` é ignorado.
- [x] `logs/` é ignorado.
- [x] `backend/target/` é ignorado.
- [x] `frontend/node_modules/` é ignorado.
- [x] migrations Flyway futuras não são ignoradas.

## Scripts de segurança

- [x] `scripts/security/verificar-arquivos-proibidos.ps1` existe.
- [x] `scripts/security/verificar-segredos.ps1` existe.
- [x] scripts retornam erro quando encontram risco.
- [x] scripts não imprimem valor sensível.
- [x] scripts não modificam arquivos.
- [x] hook de pre-commit executa os três scripts.
- [x] scripts analisam blobs staged.
- [x] comportamento fail-closed validado.
- [x] EfiPixProvider.java não é bloqueado.
- [x] EfiPixConfiguration.java não é bloqueado.
- [x] PasswordService.java não é bloqueado.
- [x] CredentialPolicy.java não é bloqueado.
- [x] segredo staged e removido do working tree continua detectado.
- [x] erro ao ler blob staged bloqueia.
- [ ] comando do gitleaks foi validado conforme versão instalada.
- [x] fallback local foi testado.
- [x] teste de regressão dos hooks foi aprovado.
- [x] ADR-003 está sincronizado em todos os documentos.
- [x] ADR-007 está sincronizado em todos os documentos.
- [x] tolerância financeira final é zero.
- [x] pacote ZIP de revisão foi criado e validado.
- [x] ZIP não foi adicionado ao Git.

## Hardening Fase 0.2.2

- [x] nenhum ponto de interrogação corrompido dentro de palavra.
- [x] nenhum mojibake.
- [x] scripts PowerShell compatíveis com Windows PowerShell 5.1.
- [x] placeholders não ocultam segredo real.
- [x] chaves JSON entre aspas são analisadas.
- [x] arquivos de 5 a 10 MB não escapam do scan.
- [x] archives proibidos são bloqueados.
- [x] testes distinguem detecção de erro operacional.
- [x] pacote é reaberto e validado pelo próprio script.
- [x] resumo contém resultados reais.
- [x] ZIP inválido é removido.
- [x] hashes do índice são comparados com o inventário inicial.

## Hardening Fase 0.2.3

- [x] manifesto real extraído do ZIP é importado e comparado.
- [x] manifesto adulterado por hash, tamanho, coluna, caminho, BOM, duplicidade e tipo inválido é bloqueado.
- [x] arquivos de controle do pacote passam por scan de secrets antes e depois da extração.
- [x] metadado de execução contendo secret fictício é bloqueado sem exibir valor.
- [x] UTF-16LE, UTF-16BE, UTF-32, NUL e controles inválidos em texto são bloqueados.
- [x] `.zip.example` e `.p12.example` são bloqueados.
- [x] senhas com símbolos e espaços são detectadas pelo fallback.
- [x] JSON e YAML multiline com chave sensível são detectados.
- [x] `Âmbito` legítimo, URL com query string e código com ponto de interrogação são permitidos.
- [x] mojibake, palavras com interrogação no lugar de acento e U+FFFD são bloqueados.
- [x] CSV com BOM é gerado por função explícita independente da versão do PowerShell.
- [x] symlink `120000` e gitlink `160000` são bloqueados.
- [x] modo regular `100644` e executável `100755` são permitidos.
- [x] traversal com slash, traversal com backslash e duplicidade por caixa no ZIP são bloqueados.
- [x] captura concorrente de stdout e stderr foi validada sem deadlock.
- [x] hook pre-commit real foi testado na raiz e em subdiretório.
- [x] temporários da suíte de pacote são removidos integralmente.
- [x] relatórios de testes foram atualizados com os cenários da Fase 0.2.3.

## Hardening Fase 0.2.4

- [x] inventário inicial oficial é criado fora do repositório antes de alterações.
- [x] `scripts/entrega/criar-inventario-inicial.ps1` gera CSV UTF-8 com BOM com colunas oficiais.
- [x] gerador de pacote valida BOM, colunas, caminhos, booleanos, tamanhos, SHA-256, modo Git e duplicidade do inventário real.
- [x] qualquer alteração fora do índice Git bloqueia o pacote com mensagem explícita.
- [x] fallback local de secrets é sempre executado.
- [x] Gitleaks é camada adicional quando disponível e fica `PENDENTE` quando não instalado.
- [x] referências externas exatas de secrets são permitidas sem liberar literais hardcoded.
- [x] `.env.*.example` é permitido pelo `.gitignore` somente como exemplo validado pelos scanners.
- [x] commit local é opcional; `user.name` e `user.email` locais não bloqueiam a Fase 1A.
- [x] remote, push, deploy e publicação continuam proibidos sem autorização futura explícita.

## Validações obrigatórias

- [x] `git status --short` executado.
- [x] `git config --local --get core.hooksPath` executado.
- [x] `git check-ignore -v --no-index .env` executado.
- [x] `git check-ignore -v --no-index storage-local/teste.jpg` executado.
- [x] `git check-ignore -v --no-index backups-local/teste.dump` executado.
- [x] `git check-ignore -v --no-index logs/app.log` executado.
- [x] `git check-ignore -v --no-index backend/target/app.jar` executado.
- [x] `git check-ignore -v --no-index frontend/node_modules/pacote/index.js` executado.
- [x] regra de migration futura verificada sem criar arquivo.
- [x] `git diff --cached --check` executado.
- [x] `git diff --cached --stat` executado.

## Saída da fase

- [x] nenhum código de aplicação criado;
- [x] nenhuma migration criada;
- [x] nenhuma integração externa criada;
- [x] nenhum push executado;
- [x] commit local não é obrigatório nesta fase;
- [x] próximos bloqueios da Fase 1A registrados.

Bloqueios para iniciar a Fase 1A:

- revisar e aprovar o pacote ZIP final desta fase;
- concluir os testes e validações obrigatórias sem falha;
- autorizar explicitamente o início da Fase 1A.
