# Regras absolutas da V3

Estas regras não podem ser violadas durante diagnóstico, desenho, implementação, migração ou virada da V3.

## Dados

1. Nenhum dado de produção deve ser alterado por scripts exploratorios.
2. Nenhuma correção pode ser feita silenciosamente pelo importador.
3. Toda entidade importada deve manter referência ao legado.
4. Saldos de créditos, pagamentos e ativações premium exigem reconciliação com tolerância zero.
5. Dados pessoais, hashes, certificados, dumps e secrets não podem entrar no repositório, no classpath, no frontend, no JAR, em imagens de deploy ou em ZIPs de entrega.
6. Dados privados devem ter acesso auditável e política de retenção.

## SEO e URLs públicas

As rotas abaixo devem ser preservadas:

- `/anuncios/[slug]`
- `/acompanhantes/[uf]/[cidade]`
- `/acompanhantes/[uf]/[cidade]/[bairro]`
- `/sitemap.xml`
- `/robots.txt`

Regras obrigatórias:

- O canonical único é `https://topsdojob.com`, sem `www`.
- `www.topsdojob.com` deve redirecionar para sem `www` com 301.
- Não pode existir cadeia de redirects em URLs prioritárias.
- Sitemap deve conter apenas URLs 200, canônicas e indexáveis.
- Robots deve ter uma única fonte de verdade por ambiente.
- Staging, preview, admin e endpoints internos devem ser `noindex`.
- Slugs antigos devem ser preservados sempre que possível.
- Quando um slug precisar mudar, deve haver redirect individual 301.

## Operação comercial

1. O legado deve continuar operando enquanto a V3 é construída.
2. Não deve haver mistura de escrita entre legado e V3.
3. A V3 não deve assumir tráfego público antes dos gates de aceite.
4. Cutover deve ter janela definida, responsáveis, smoke tests e plano de retorno.
5. A equipe comercial deve ter papel próprio, com permissões não destrutivas por padrão.

## Rollback

1. Rollback deve ser real e ensaiado.
2. Rollback não pode depender de improviso manual sem runbook.
3. Antes da virada deve existir backup final de banco, mídia, configurações, mapa SEO, release e logs essenciais.
4. O legado deve permanecer preservado e congelado por uma janela de segurança.
5. A decisão de rollback deve ter critérios objetivos: erro 5xx, quebra de login/admin, divergência financeira, falha de mídia, queda SEO crítica ou falha de pagamento.

## Backup

1. Backup deve cobrir PostgreSQL, mídia, configurações, mapa SEO, releases e logs essenciais.
2. Deve existir backup manual pelo admin, mas a execução técnica deve ser isolada em job/worker operacional.
3. Deve existir backup automático com retenção.
4. Todo backup precisa de manifesto, checksum e status.
5. Backups sensíveis devem ser criptografados.
6. Restauração deve ser testada em ambiente isolado.
7. Falhas devem gerar alerta.

## Mídia

1. Deve existir uma fonte canônica de mídia.
2. URL pública não é fonte de verdade; é derivação de storage, chave e política.
3. Placeholder não é mídia real.
4. Stories devem estar vinculados ao anúncio.
5. Documentos privados não podem compartilhar o mesmo tratamento de mídia pública.
6. Migração de Supabase/R2 deve ser verificada por manifesto, tamanho e checksum/ETag quando disponível.

## Banners

Banners obrigatórios:

- Desktop: 1452 x 500 px.
- Mobile: 1080 x 900 px.

O admin deve permitir:

- upload;
- recorte/crop;
- preview;
- alt text;
- link;
- botão;
- status ativo/inativo;
- agendamento;
- histórico;
- rollback de banner anterior.

As dimensões são fixas por slot. O conteúdo é editável pelo painel.

## Premium

1. Benefícios premium devem ser explícitos, versionados e auditáveis.
2. Toda ativação deve registrar origem, ator, anúncio, usuário, início, fim, custo, regra aplicada e motivo.
3. `ANUNCIO_TOPO` deve ter comportamento claro e testável.
4. `POSICAO_GARANTIDA_TOP20` deve ser benefício explícito, sem prometer posição 1.
5. Outros benefícios não podem criar prioridade escondida no ranking.

## Créditos

1. Créditos exigem razão append-only.
2. Saldo é projeção/materialização, não única fonte contábil.
3. Movimento deve registrar saldo antes/depois, origem, referência, idempotência e ator.
4. Pagamento aprovado deve gerar no máximo um movimento correspondente.
5. Ajustes manuais exigem permissão, justificativa e auditoria.

## Pagamentos Pix Efí

1. Efí Bank/Efí Pay é a integração Pix ativa inicial da V3.
2. Mercado Pago é legado descartado; não pode entrar como nova dependência, novo contrato ativo ou nomenclatura central.
3. Supabase é legado de importação somente, quando aplicável.
4. Entidades centrais devem usar nomes genéricos como `pagamento`, `pagamento_evento`, `pagamento_webhook` e `pagamento_conciliacao`.
5. O domínio financeiro não deve usar nomes herdados como `pagamentos_mp`, `PagamentoMP` ou `mp_payment_id`.
6. Toda aprovação Pix deve ser confirmada por webhook validado e/ou consulta ativa ao provedor antes de conceder crédito.
7. Webhook ou consulta duplicada nunca pode duplicar pagamento, crédito ou ativação premium.
8. O modo local deve usar mock por padrão e nunca acessar produção.

## Segurança

1. Senhas, hashes, tokens e secrets não podem ser expostos em logs, responses, commits, artefatos ou documentos.
2. ADMIN, MODERADOR e COMERCIAL devem ter permissões explícitas.
3. Sessão deve ser revogável.
4. Tokens por e-mail devem ser armazenados como hash, com finalidade, expiração, tentativas e consumo único.
5. Operações sensíveis devem ter rate limit, auditoria de IP e bloqueio por tentativa.
6. Webhooks financeiros devem ser autenticados/idempotentes conforme o provedor.

## Português

Painel, textos internos, mensagens operacionais, relatórios e documentação funcional devem ficar em português do Brasil sempre que possível. No código e no banco, nomes sem acentos podem ser usados por consistência técnica.

## Proibicoes nesta etapa

- Não implementar código.
- Não criar migrations.
- Não alterar banco.
- Não remover arquivos.
- Não alterar produção.
- Não mexer em secrets.
- Não copiar valores sensíveis para a documentação.
