# Segurança

## Reporte de problemas

Falhas de segurança devem ser tratadas como prioridade e registradas sem expor secrets, dados pessoais ou payloads financeiros integrais.

## Proibições

Não versionar:

- `.env` real;
- credenciais Efí;
- certificado Efí;
- tokens;
- chaves privadas;
- dumps;
- backups;
- uploads;
- logs;
- dados reais de usuários.
- archives como `.zip`, `.7z`, `.rar`, `.tar`, `.tgz`, `.gz`, `.bz2`, `.xz` e `.iso`.
- exemplos disfarçados com extensão perigosa composta, como `.p12.example`, `.pem.example`, `.zip.example`, `.7z.example`, `.dump.example` e `.db.example`.

Archives são bloqueados por padrão porque podem esconder `.env`, certificados, dumps, payloads financeiros ou dados reais sem inspeção textual confiável.

## Scanners locais

Os scanners locais analisam o conteúdo staged no índice Git:

```powershell
.\scripts\security\verificar-codificacao.ps1
.\scripts\security\verificar-arquivos-proibidos.ps1
.\scripts\security\verificar-segredos.ps1
```

Exit codes:

- `0`: validação limpa;
- `1`: achado de segurança ou codificação inválida;
- `2`: erro operacional que bloqueia o commit.

Na máquina usada nesta etapa, `gitleaks` não estava instalado no PATH. O fallback local conservador foi usado sem baixar ou instalar ferramentas, e o status de Gitleaks permanece `PENDENTE`.

Desde a Fase 0.2.4, o fallback local é sempre executado. Se `gitleaks` estiver disponível, ele roda como camada adicional; se não estiver, a ausência do binário não vira aprovação falsa.

## Proteções adicionais do índice e do pacote

Os scanners e o gerador validam o modo Git de cada entrada staged:

- `100644` e `100755` são permitidos;
- `120000` é bloqueado como symlink;
- `160000` é bloqueado como gitlink/submodule;
- modos desconhecidos são tratados como erro operacional.

O pacote de revisão valida o manifesto real extraído do ZIP, escaneia arquivos de controle, rejeita traversal, caminhos absolutos, UNC, duplicidades case-insensitive e qualquer divergência entre manifesto, índice, workspace e conteúdo extraído.

O gerador do pacote bloqueia qualquer alteração fora do índice Git antes de montar o ZIP. O inventário inicial precisa ser CSV UTF-8 com BOM, criado fora do repositório, com colunas oficiais e sem caminhos absolutos, traversal, duplicidade ou hashes inválidos.

Referências externas exatas como `${EFI_CLIENT_SECRET}`, `${EFI_CLIENT_SECRET:}`, `${DATABASE_PASSWORD}`, `$env:EFI_CLIENT_SECRET`, `process.env.EFI_CLIENT_SECRET`, `System.getenv("EFI_CLIENT_SECRET")`, `environment.getProperty("EFI_CLIENT_SECRET")` e `config.get("EFI_CLIENT_SECRET")` são permitidas em exemplos/configuração. Literais hardcoded para os mesmos campos continuam bloqueados.

## Incidente com secret

Se um secret for commitado:

1. pare o uso da credencial;
2. revogue ou rotacione o secret no provedor;
3. trate o histórico Git como comprometido;
4. registre o incidente;
5. só então remova o arquivo e corrija o repositório.

Apagar o arquivo e continuar usando a mesma credencial não é suficiente.
