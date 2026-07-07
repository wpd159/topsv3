# Relatorio - Riscos do Bloco 62 e da migracao visual

## Riscos principais

### Copia de integracoes antigas

Risco: trazer do clone chamadas para endpoints antigos, auth antigo, upload real, pagamento, Pix/Efi, webhook, clique WhatsApp real ou APIs externas.

Mitigacao: copiar visual e estrutura somente depois de mapear cada chamada para adapters V3.

### SEO de producao em HML

Risco: canonical, sitemap ou metadata apontarem para producao durante HML.

Mitigacao: manter noindex/nofollow, robots `Disallow: /` e parametrizacao por ambiente antes de deploy.

### Storage e midia real

Risco: expor bucket, storage key, provider, hash, URL privada, CDN real ou midia/documento privado.

Mitigacao: usar DTO publico V3, placeholders seguros e validadores de midia sintetica.

### Upload e wizard

Risco: reintroduzir upload real, KYC real, pagamento, Premium obrigatorio ou autopublicacao ao migrar o wizard.

Mitigacao: migrar UX primeiro e manter envio real desabilitado ate fase propria e autorizacao expressa.

### Auth/RBAC

Risco: misturar auth antigo do clone com RBAC/admin V3.

Mitigacao: preservar auth/RBAC V3 e tratar admin como fase separada.

### Mobile e scroll lock

Risco: trazer scroll lock, botao flutuante, fixed indevido, animacao automatica ou elemento que cause instabilidade mobile.

Mitigacao: validar estaticamente `document.body.style.overflow`, `position: fixed`, `100vw`, keyframes e scroll horizontal.

### Dependencias

Risco: aumentar muito a superficie do frontend ao trazer Tailwind, Radix, framer-motion e bibliotecas visuais.

Mitigacao: inventariar dependencias e aprovar apenas as necessarias por fase.

### Clone com alteracoes locais

Risco: usar como referencia arquivos sujos sem distinguir o que e producao real e o que e alteracao local.

Mitigacao: registrar estado do clone, comparar antes de copiar e nao alterar `C:\clone`.

### Mojibake e copy

Risco: trazer textos com codificacao ruim ou copy tecnica visivel.

Mitigacao: manter validadores contra mojibake, copy tecnica e enum visivel.

## Riscos residuais

- A migracao ainda exige revisao humana de paridade visual.
- A equivalencia funcional completa depende de adapters V3 ainda nao migrados para a base do clone.
- HML online nao deve ser tratado como homologacao final ate concluir a migracao visual e gates Pro.
- Producao/cutover continuam bloqueados por dados reais, importacao real, Pix/Efi, upload real, SEO real, monitoramento e rollback.

## Confirmacoes negativas

- Nao houve producao.
- Nao houve dados reais.
- Nao houve alteracao em `C:\clone`.
- Nao houve backend, banco, migration, restore, VPS ou importacao real.
- Nao houve Pix/Efi real, webhook real, upload real ou pagamento real.
- Nao houve push.
- Nao houve fase posterior iniciada.
