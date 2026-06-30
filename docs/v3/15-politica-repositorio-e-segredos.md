# Política de repositório e segredos

## Objetivo

Proteger o repositório da V3 contra secrets, dados reais, certificados, dumps, backups, uploads, logs e artefatos sensíveis.

## Arquivos proibidos

Não podem entrar no Git:

- `.env` real;
- arquivos `.env.*` reais;
- credenciais Efí;
- certificado Efí;
- senha do certificado Efí;
- tokens e client secrets;
- chaves privadas;
- keystores;
- dumps de banco;
- backups;
- bancos locais;
- storage local;
- uploads;
- logs;
- dados reais de usuários;
- relatórios com dados pessoais reais;
- exportações de usuários;
- payload financeiro integral;
- archives como `.zip`, `.7z`, `.rar`, `.tar`, `.tgz`, `.gz`, `.bz2`, `.xz` e `.iso`;
- exemplos com extensão perigosa composta, como `.p12.example`, `.pem.example`, `.zip.example`, `.7z.example`, `.dump.example` e `.db.example`.

Archives são proibidos por padrão porque podem encapsular `.env`, certificados, chaves privadas, dumps, logs, bancos locais ou dados reais sem inspeção confiável pelo scanner textual.

Arquivos `.example` não são liberados apenas pelo sufixo. Todas as extensões relevantes do nome são analisadas, e qualquer extensão de certificado, keystore, archive, dump ou banco bloqueia o arquivo.

## Arquivos permitidos

Podem ser versionados:

- documentação;
- scripts sem segredo;
- arquivos `.example`;
- `.env.example`;
- `.env.local.example`;
- `.env.*.example`;
- configurações de exemplo sem valor real;
- migrations oficiais futuras no diretório aprovado do Flyway;
- políticas e checklists.

## Regras para `.env.example`

Arquivos de exemplo devem:

- usar valores fictícios;
- não conter token real;
- não conter host de produção sensível;
- não conter certificado;
- indicar quando uma variável é obrigatória;
- documentar ambiente esperado.

## Credenciais e certificado Efí

Credenciais Efí:

- não entram no Git;
- não entram no frontend;
- não entram no JAR;
- não entram em imagem Docker;
- não entram em ZIP de entrega;
- não entram em backup comum baixável pelo painel;
- não possuem caminho fixo de produção no código.

Certificado Efí:

- deve ficar fora do repositório;
- deve ter acesso restrito;
- deve ser configurado por ambiente;
- deve ter rotação documentada;
- deve ser tratado como segredo crítico.

## Dumps, backups e dados pessoais

Dumps, backups e dados pessoais reais devem ficar fora do Git.

Quando forem necessários para diagnóstico ou importação:

- usar ambiente controlado;
- sanitizar quando possível;
- registrar origem;
- restringir acesso;
- definir retenção;
- remover após uso autorizado.

## Quando um secret for commitado

Se um secret entrar no Git:

1. interromper o uso da credencial;
2. revogar ou rotacionar o secret no provedor;
3. avaliar exposição do histórico Git;
4. registrar incidente;
5. limpar o repositório;
6. revisar hooks e regras para evitar recorrência.

É proibido simplesmente apagar o arquivo e continuar usando a mesma credencial.

## Revisão obrigatória

Exigem revisão obrigatória:

- migrations;
- financeiro;
- autenticação;
- autorização;
- SEO;
- backup;
- integração Efí;
- importador;
- scripts de segurança;
- alteração de `.gitignore`;
- alteração de hooks.

## Ferramentas locais

Scripts obrigatórios:

```powershell
.\scripts\security\verificar-codificacao.ps1
.\scripts\security\verificar-arquivos-proibidos.ps1
.\scripts\security\verificar-segredos.ps1
```

O hook de pre-commit executa esses scripts automaticamente para arquivos staged.

## Codificação

Enquanto houver suporte ao Windows PowerShell 5.1:

- scripts `.ps1` devem usar UTF-8 com BOM;
- documentos `.md`, shell scripts, TOML, JSON, YAML e similares devem usar UTF-8 sem BOM;
- CSV gerado para consumo no Windows deve usar UTF-8 com BOM.

O validador de codificação bloqueia UTF-8 inválido, U+FFFD, mojibake e `?` corrompido dentro de palavras.

Também são bloqueados UTF-16, UTF-32, byte NUL e controles inválidos em arquivos textuais. A heurística de `?` corrompido é restrita a textos naturais, para permitir URLs com query string e sintaxe legítima de código.

## Modos Git

O índice Git é lido com `git ls-files --stage -z`.

São permitidos:

- `100644`;
- `100755`.

São bloqueados:

- `120000`, symlink;
- `160000`, gitlink ou submodule;
- qualquer modo desconhecido.

Essa regra evita empacotar ponteiros, links para fora do repositório ou conteúdo não analisado.

## Achado versus erro operacional

Os scanners devem diferenciar:

- achado de segurança ou codificação inválida: exit code `1`;
- erro operacional: exit code `2`.

Erro operacional inclui falha ao listar arquivos staged, falha ao ler blob do índice, scanner indisponível ou Git em estado inesperado. Esses casos bloqueiam commit local opcional e pacote de revisão.

## Gitleaks e fallback

A suíte de testes força `TOPSV3_FORCAR_FALLBACK_GITLEAKS=1` nos cenários isolados do fallback local. Assim, o fallback continua testado mesmo que `gitleaks` seja instalado depois.

Na execução real, o fallback local é sempre executado. Quando `gitleaks` está disponível, ele roda como camada adicional; quando o binário não está disponível, o resultado é registrado como pendente, sem virar aprovação falsa.

Referências externas exatas como `${EFI_CLIENT_SECRET}`, `${EFI_CLIENT_SECRET:}`, `${DATABASE_PASSWORD}`, `$env:EFI_CLIENT_SECRET`, `process.env.EFI_CLIENT_SECRET`, `System.getenv("EFI_CLIENT_SECRET")`, `environment.getProperty("EFI_CLIENT_SECRET")` e `config.get("EFI_CLIENT_SECRET")` são permitidas em exemplos e configuração. Valores literais hardcoded para secrets continuam bloqueados.
