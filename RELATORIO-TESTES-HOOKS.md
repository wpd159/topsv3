# Relatório de testes dos hooks

- Total de cenários: 72
- Cenários OK: 71
- Cenários pendentes: 1
- Cenários com falha: 0

| Cenário | Scanner esperado | Exit code esperado | Exit code obtido | Resultado |
| --- | --- | ---: | ---: | --- |
| .env real | arquivos | 1 | 1 | OK |
| .p12 | arquivos | 1 | 1 | OK |
| .zip.example composto | arquivos | 1 | 1 | OK |
| .p12.example composto | arquivos | 1 | 1 | OK |
| chave privada | segredos | 1 | 1 | OK |
| URL PostgreSQL com senha | segredos | 1 | 1 | OK |
| bearer token | segredos | 1 | 1 | OK |
| JSON client_secret | segredos | 1 | 1 | OK |
| JSON password | segredos | 1 | 1 | OK |
| senha com símbolos | segredos | 1 | 1 | OK |
| senha com espaços entre aspas | segredos | 1 | 1 | OK |
| JSON multiline | segredos | 1 | 1 | OK |
| YAML multiline | segredos | 1 | 1 | OK |
| segredo e CHANGE_ME na mesma linha | segredos | 1 | 1 | OK |
| linha com formato de regra e secret fora do regex | segredos | 1 | 1 | OK |
| Java token literal hardcoded | segredos | 1 | 1 | OK |
| TypeScript token literal hardcoded | segredos | 1 | 1 | OK |
| .env.production.example com literal bloqueia | segredos | 1 | 1 | OK |
| referência externa e literal na mesma linha bloqueia | segredos | 1 | 1 | OK |
| fallback executado após gitleaks OK simulado | segredos | 1 | 1 | OK |
| gitleaks OK simulado com fallback limpo | segredos | 0 | 0 | OK |
| arquivo textual entre 5 e 10 MB contendo segredo | segredos | 1 | 1 | OK |
| archive .zip | arquivos | 1 | 1 | OK |
| archive .7z | arquivos | 1 | 1 | OK |
| UTF-8 inválido | codificação | 1 | 1 | OK |
| .properties UTF-16LE com senha | codificação | 1 | 1 | OK |
| .json UTF-16LE com client_secret | codificação | 1 | 1 | OK |
| .md UTF-16BE | codificação | 1 | 1 | OK |
| arquivo textual UTF-8 com NUL | codificação | 1 | 1 | OK |
| mojibake | codificação | 1 | 1 | OK |
| Responsável corrompido com interrogação | codificação | 1 | 1 | OK |
| aplicação corrompida com dupla interrogação | codificação | 1 | 1 | OK |
| Validação em mojibake | codificação | 1 | 1 | OK |
| U+FFFD | codificação | 1 | 1 | OK |
| segredo staged removido do working tree | segredos | 1 | 1 | OK |
| erro ao listar staged | codificação | 2 | 2 | OK |
| erro ao ler blob | codificação | 2 | 2 | OK |
| erro operacional do scanner | segredos | 2 | 2 | OK |
| modo Git symlink 120000 simulado | codificação | 2 | 2 | OK |
| modo Git gitlink 160000 simulado | codificação | 2 | 2 | OK |
| .env.example com CHANGE_ME | todos | 0 | 0 | OK |
| .env.production.example com placeholder | todos | 0 | 0 | OK |
| .env.production.example com referências externas | todos | 0 | 0 | OK |
| YAML com referência externa default vazia | todos | 0 | 0 | OK |
| PowerShell com referência env externa | todos | 0 | 0 | OK |
| Java token gerado e System.getenv | todos | 0 | 0 | OK |
| TypeScript token de resposta e process.env | todos | 0 | 0 | OK |
| Java environment.getProperty e config.get externos | todos | 0 | 0 | OK |
| application.example.yml com placeholder | todos | 0 | 0 | OK |
| certificado-configuracao.example.yml textual | todos | 0 | 0 | OK |
| EfiPixProvider.java | todos | 0 | 0 | OK |
| EfiPixConfiguration.java | todos | 0 | 0 | OK |
| PasswordService.java | todos | 0 | 0 | OK |
| CredentialPolicy.java | todos | 0 | 0 | OK |
| documentação menciona client_secret sem valor | todos | 0 | 0 | OK |
| JSON client_secret CHANGE_ME | todos | 0 | 0 | OK |
| migration Flyway fictícia | todos | 0 | 0 | OK |
| caminho com espaço | todos | 0 | 0 | OK |
| caminho com acento | todos | 0 | 0 | OK |
| perguntas legítimas terminadas em interrogação | todos | 0 | 0 | OK |
| Âmbito com A circunflexo legítimo | todos | 0 | 0 | OK |
| URL com query string | todos | 0 | 0 | OK |
| TypeScript com operador interrogação | todos | 0 | 0 | OK |
| repositório sem staged files | todos | 0 | 0 | OK |
| scripts de segurança sem segredo | todos | 0 | 0 | OK |
| texto português UTF-8 válido | todos | 0 | 0 | OK |
| modo Git 100755 permitido | todos | 0 | 0 | OK |
| pre-commit real na raiz com arquivo limpo | hook | 0 | 0 | OK |
| pre-commit real a partir de subdiretório | hook | 0 | 0 | OK |
| pre-commit real bloqueia secret staged | hook | 1 | 1 | OK |
| pre-commit bloqueia PowerShell ausente simulado | hook | 2 | 2 | OK |
| gitleaks real instalado | gitleaks | 1 | - | PENDENTE |
