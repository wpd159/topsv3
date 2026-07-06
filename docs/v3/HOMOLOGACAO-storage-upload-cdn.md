# HOMOLOGACAO - Storage, upload e CDN

## Principio

Storage, upload e CDN em homologacao devem preservar a separacao entre midia publica, midia privada operacional e documento privado. Este documento e contrato tecnico; ele nao executa upload, nao cria bucket real, nao acessa storage real e nao autoriza CDN real.

## Classes de arquivo

| Classe | Finalidade | Publicavel | URL publica | Observacao |
| --- | --- | --- | --- | --- |
| Midia publica de anuncio | Fotos aprovadas do anuncio | Sim, apos moderacao | Apenas apos aprovacao | DTO publico pode receber URL publica segura. |
| Stories | Conteudo temporario aprovado | Sim, conforme idade/backend | Apenas apos aprovacao e regra de idade | Bloqueado ate confirmacao quando exigido. |
| Midia pendente | Upload aguardando moderacao | Nao | Nao | Usar placeholder seguro. |
| Midia rejeitada | Arquivo reprovado pela moderacao | Nao | Nao | Nao reaproveitar URL publica antiga. |
| Documento privado | Verificacao documental/operacional | Nunca | Nunca | Nao vira midia publica em nenhuma circunstancia. |

## Separacao obrigatoria

- Bucket/container de midia publica aprovado.
- Bucket/container de midia privada operacional.
- Bucket/container de documento privado.
- Prefixos por ambiente: homologacao separado de producao.
- Credenciais de escrita separadas de credenciais de leitura.
- Usuario da aplicacao sem permissao em bucket de outro ambiente.
- Documento privado nao pode compartilhar prefixo publico.

## Regras de URL e DTO

- URL publica so pode existir para midia aprovada.
- URL privada nunca deve ser versionada.
- Storage key nunca deve aparecer em DTO publico.
- Provider, bucket, etag, hash interno e path privado nao devem aparecer em resposta publica.
- DTO admin deve mascarar dados internos quando nao forem indispensaveis.
- Midia sem URL deve usar placeholder seguro e estavel.
- Placeholder nao deve simular foto real, documento real ou conteudo sensivel.

## Upload futuro

Antes de upload real:

- Definir tamanho maximo por tipo.
- Validar extensao e MIME real.
- Remover metadados sensiveis quando aplicavel.
- Executar antivirus ou scanner equivalente.
- Gerar storage key server-side, nunca enviada pelo cliente.
- Registrar auditoria sanitizada de upload.
- Manter arquivo pendente ate moderacao.
- Bloquear publicacao automatica.
- Validar rollback e expurgo futuro.

## Fotos, Premium e beneficios

- Plano gratuito deve manter ate 2 fotos publicas sinteticas/contratuais como baseline util.
- Premium pode adicionar fotos extras sem remover utilidade do gratuito.
- Beneficios que liberam fotos extras devem respeitar expiracao conjunta.
- Beneficio expirado nao deve criar URL publica nova.
- Expiracao de beneficio nao deve apagar fisicamente arquivo sem politica propria.
- Remocao visual e disponibilidade publica devem seguir status e politica do backend.

## Midia bloqueada e idade

- Conteudo `BLOQUEADO` nao expoe midia sensivel antes da confirmacao de idade.
- Stories exigem idade quando a regra do backend exigir.
- Frontend nao decide classificacao nem liberacao.
- Sem idade/autorizacao, usar placeholder seguro ou estado vazio controlado.

## Remocao e retencao

- Remocao logica deve preceder remocao fisica.
- Remocao fisica depende de politica de retencao e job futuro aprovado.
- Documento privado segue regra propria de retencao e nunca e publicavel.
- `retencao_ate` pode ser nulo quando houver finalidade operacional legitima.
- Expurgo automatico nao deve existir sem decisao juridica e fase propria.

## CDN e cache

- CDN deve usar dominio de homologacao separado.
- Cache deve ter TTL curto em homologacao.
- Rejeicao/remocao deve invalidar cache ou trocar URL publica.
- CDN nao deve cachear documento privado.
- Headers devem evitar indexacao/acesso indevido de midia privada.
- Logs de CDN nao devem registrar payload sensivel em relatorio versionado.

## Rollback

- Publicacao de URL publica deve ser reversivel.
- Reprovar midia deve retirar URL publica do contrato publico.
- Rollback de deploy nao pode reexpor midia rejeitada.
- Retorno de beneficio Premium expirado nao deve reabrir fotos extras sem revalidacao.
- Lista de invalidacao de cache deve ser auditavel e sanitizada.

## Variaveis obrigatorias sem valores reais

| Variavel | Obrigatoria | Escopo | Observacao |
| --- | --- | --- | --- |
| `STORAGE_ENDPOINT` | Sim antes de storage real | homologacao | Endpoint externo fora do Git. |
| `STORAGE_REGION` | Sim antes de storage real | homologacao | Sem valor real versionado. |
| `STORAGE_PUBLIC_BUCKET` | Sim antes de URL publica | midia publica | Separado de documentos privados. |
| `STORAGE_PRIVATE_BUCKET` | Sim antes de upload privado | midia privada | Nao expor em DTO publico. |
| `STORAGE_DOCUMENT_BUCKET` | Sim antes de documento real | documento privado | Nunca publicavel. |
| `STORAGE_ACCESS_KEY` | Sim antes de storage real | secret externo | Fora do Git e dos relatórios. |
| `STORAGE_SECRET_KEY` | Sim antes de storage real | secret externo | Fora do Git e dos relatórios. |
| `CDN_PUBLIC_BASE_URL` | Sim antes de CDN real | midia publica | Dominio de homologacao, nao producao. |
| `UPLOAD_MAX_IMAGE_BYTES` | Sim antes de upload | upload | Valor definido por politica. |
| `UPLOAD_MAX_STORY_BYTES` | Sim antes de upload | stories | Valor definido por politica. |

## Pendencias antes de homologacao real

- Escolher provedor e ambiente isolado.
- Criar buckets/containers reais fora deste bloco.
- Definir credenciais e politica de permissao fora do Git.
- Validar antivirus/moderacao real.
- Validar upload com arquivos sinteticos autorizados.
- Validar CDN/cache/invalidation em homologacao.
- Revisar Pro antes de documento real, midia real, dados reais/sanitizados ou cutover.

## Proibicoes

- Nao usar producao.
- Nao usar dado real.
- Nao executar upload real neste bloco.
- Nao acessar storage real, CDN real, R2/S3 real ou API externa.
- Nao versionar URL privada, storage key, bucket real, credencial, log bruto, midia real ou documento real.
