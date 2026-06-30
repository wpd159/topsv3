# Backup e rollback

## Objetivo

Garantir preservação real dos dados e retorno controlado antes, durante e depois da virada da V3.

Backup não é apenas baixar um ZIP pelo painel. O painel pode solicitar e acompanhar, mas a execução deve ocorrer em worker/job operacional isolado, com permissões mínimas, logs e alertas.

## Escopo de backup

### PostgreSQL

- dump lógico em formato custom;
- snapshots gerenciados quando disponíveis;
- WAL/PITR se a infraestrutura permitir;
- validação de integridade do dump;
- registro de versão de schema/migrations.

### Mídia

- inventário de objetos;
- bucket;
- chave;
- tamanho;
- ETag/checksum quando disponível;
- status;
- manifesto;
- cópia offsite/imutável quando possível.

### Configurações

- configurações operacionais necessárias;
- systemd;
- Nginx;
- variaveis não sensíveis;
- referências a secrets sem copiar o valor;
- manifestos de deploy.

Secrets devem ficar em cofre separado, não em download comum do painel.

### Mapa SEO

- `seo_url`;
- `seo_redirect`;
- sitemap publicado;
- robots vigente;
- mapa URL atual x URL V3;
- canonical por tipo de página.

### Releases

- artefato backend;
- artefato frontend;
- hash/assinatura;
- versão;
- migrations aplicadas;
- symlink/current;
- configuração de release.

### Logs essenciais

- logs de cutover;
- logs de importação;
- logs de backup;
- logs de restauração;
- logs de erro crítico;
- logs de auditoria.

## Modelo operacional

Tabelas:

- `backup_politica`
- `backup_execucao`
- `backup_artefato`
- `backup_teste_restauracao`

Estados:

- `AGENDADO`
- `EXECUTANDO`
- `SUCESSO`
- `FALHA`
- `EXPIRADO`
- `CANCELADO`

## Backup manual pelo admin

Regras:

- Apenas ADMIN com permissão `BACKUP_GERENCIAR`.
- Reautenticação/MFA antes de iniciar.
- Admin solicita, worker executa.
- Painel exibe progresso e resultado.
- Download, se permitido, deve ser restrito e auditado.
- Secrets não entram no pacote baixável.

## Backup automático

Regras:

- Política por tipo.
- Agenda configuravel.
- Retenção definida.
- Alerta em falha ou atraso.
- Checksum obrigatório.
- Criptografia para artefatos sensíveis.
- Teste periódico de restauração.

Política inicial sugerida:

- diários: 7 versões;
- semanais: 8 versões;
- mensais: 12 versões;
- PITR: 7 a 14 dias se disponível;
- backup obrigatório antes de migration e cutover.

## Retenção

Cada artefato deve ter:

- `retencao_ate`;
- classificação de sensibilidade;
- destino;
- status;
- regra de expiração;
- log de exclusão.

Exclusão de backup deve ser protegida contra a mesma credencial que criou o backup quando possível.

## Checksum

Regras:

- Manifesto com SHA-256.
- Checksum por artefato.
- Checksum do manifesto.
- Validação após escrita.
- Validação antes de restaurar.
- Divergência bloqueia uso do backup.

## Criptografia

Regras:

- Backups com dados pessoais, financeiros ou documentos privados devem ser criptografados.
- Chaves fora do artefato.
- Acesso auditado.
- Rotação planejada.
- Separação por ambiente.

## Teste de restauração

Restauração deve ser testada em ambiente isolado.

Validações:

- banco restaura;
- schema esperado existe;
- contagens batem;
- amostras de usuários/anúncios/mídias conferem;
- créditos conciliam;
- sitemap/mapa SEO existe;
- aplicação sobe contra ambiente restaurado;
- logs registram resultado.

Sem teste de restauração, backup não pode ser considerado confiável.

## Alerta de falha

Alertas obrigatórios:

- backup falhou;
- backup atrasou;
- checksum divergente;
- artefato não encontrado;
- restauração falhou;
- retenção não executou;
- espaço próximo do limite;
- backup pre-cutover ausente.

## Rollback real antes da virada

Rollback deve ser ensaiado antes do cutover.

Componentes:

- release anterior preservado;
- banco legado preservado/congelado;
- backup final verificado;
- configuração anterior preservada;
- DNS/proxy/symlink reversível;
- runbook;
- responsáveis;
- critérios de decisão;
- tempo estimado.

## Cutover com rollback

Sequência mínima:

1. comunicar janela;
2. congelar escrita no legado;
3. gerar backup final de banco, mídia, configuração, SEO e release;
4. validar checksums;
5. executar delta final;
6. reconciliar;
7. ativar V3;
8. rodar smoke tests;
9. monitorar;
10. decidir manter ou voltar conforme critérios.

## Critérios para rollback

Rollback deve ser acionável se ocorrer:

- login/admin indisponível;
- página pública prioritária indisponível;
- erro 5xx persistente;
- pagamentos ou créditos inconsistentes;
- anúncios ativos sem mídia em massa;
- sitemap/canonical gravemente incorreto;
- perda de dados detectada;
- falha de segurança crítica;
- performance inviavel.

## Pos-rollback

Se rollback ocorrer:

- preservar logs da falha;
- manter V3 isolada;
- registrar incident report;
- corrigir causa raiz;
- repetir importação/delta se necessário;
- não tentar nova virada sem novo gate.

## Gate

A V3 não pode virar produção sem:

- backup final validado;
- teste de restauração realizado;
- rollback ensaiado;
- runbook aprovado;
- release anterior disponível;
- mapa SEO salvo;
- logs essenciais ativos;
- alertas configurados.
