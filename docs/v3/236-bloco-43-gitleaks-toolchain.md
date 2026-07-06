# Bloco 43 - Gate gitleaks/toolchain

## Objetivo

Fechar o checkpoint local do Bloco 42, diagnosticar a disponibilidade real do `gitleaks` no PATH e corrigir o controle de pacote para impedir `Objetivo` vazio no `RESUMO-ENTREGA.md`.

## Checkpoint consolidado

- Checkpoint local do Bloco 42: `09ffcd3`.
- Mensagem do commit: `docs: cria matriz prontidao homologacao ate bloco 42`.
- Remote: vazio.
- Push: nao executado.

## Diagnostico gitleaks

- `where.exe gitleaks`: nao localizado.
- `gitleaks version`: comando nao reconhecido.
- Resultado: `PENDENTE_GITLEAKS_REAL_NO_PATH`.
- Instalacao automatica: nao executada.
- Fallback local: mantido como secundario.

## Comando manual recomendado

Sem executar neste bloco, a instalacao manual pode ser feita pelo responsavel da maquina e depois validada em novo bloco:

```powershell
winget install --id Gitleaks.Gitleaks -e
gitleaks version
gitleaks detect --source . --no-git --redact --verbose
```

Se outro gerenciador for escolhido, a origem e a versao devem ser registradas antes de aceitar o gate como concluido.

## Correcao do pacote

`scripts/entrega/criar-pacote-revisao.ps1` agora define objetivo padrao quando `-ResumoExecucao` nao e informado e tambem completa metadados JSON com `objetivo` ausente ou vazio.

Fallback aplicado:

```text
Pacote de revisao da fase <Fase>
```

## Limites preservados

Nao houve producao, VPS, dados reais, restore, staging, Pix/Efi real, webhook, API externa, push ou fase posterior.
