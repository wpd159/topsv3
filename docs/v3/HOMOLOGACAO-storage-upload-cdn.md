# HOMOLOGACAO - Storage, upload e CDN

## Principio

Storage, upload e CDN em homologacao preservam a separacao entre midia publica, midia privada operacional e documento privado. O provider unico aprovado para a V3 e Cloudflare R2, com buckets e credencial exclusivos de HML. Os endpoints autenticados do wizard gravam novas midias somente na area privada e dependem da moderacao para qualquer publicacao.

## Classes de arquivo

| Classe | Finalidade | Publicavel | URL publica | Observacao |
| --- | --- | --- | --- | --- |
| Midia publica de anuncio | Fotos aprovadas do anuncio | Sim, apos moderacao | Apenas apos aprovacao | DTO publico pode receber URL publica segura. |
| Stories | Conteudo temporario aprovado | Sim, conforme idade/backend | Apenas apos aprovacao e regra de idade | Bloqueado ate confirmacao quando exigido. |
| Midia pendente | Upload aguardando moderacao | Nao | Nao | Usar placeholder seguro. |
| Midia rejeitada | Arquivo reprovado pela moderacao | Nao | Nao | Nao reaproveitar URL publica antiga. |
| Documento privado | Verificacao documental/operacional | Nunca | Nunca | Nao vira midia publica em nenhuma circunstancia. |

## Separacao obrigatoria

- Bucket `topsdojob-hml-midias-publicas` para midia aprovada.
- Bucket `topsdojob-hml-midias-privadas` para midia pendente ou restrita.
- Bucket `topsdojob-hml-documentos` para documento privado.
- Prefixos HML obrigatorios: `hml/midias-aprovadas/`, `hml/midias-pendentes/` e `hml/documentos/`.
- Token HML com `Object Read & Write` limitado somente aos tres buckets HML.
- Usuario da aplicacao sem permissao em bucket de producao ou outro ambiente.
- Documento privado nao pode compartilhar prefixo publico.
- Toda operacao `PUT`, `HEAD`, `GET` ou `DELETE` deve rejeitar chave fora do prefixo da area antes de acessar o provider.
- A origem publica legada auditada e somente uma origem de leitura para o cutover. Ela nao muda os buckets HML, nao concede escrita no bucket legado e nao pode ser usada por upload novo.

## Regras de URL e DTO

- URL publica so pode existir para midia aprovada.
- URL privada nunca deve ser versionada.
- Storage key nunca deve aparecer em DTO publico.
- Provider, bucket, etag, hash interno e path privado nao devem aparecer em resposta publica.
- DTO admin deve mascarar dados internos quando nao forem indispensaveis.
- Midia sem URL deve usar placeholder seguro e estavel.
- Placeholder nao deve simular foto real, documento real ou conteudo sensivel.

## Upload autenticado

O contrato implementado exige:

- Definir tamanho maximo por tipo.
- Validar extensao e MIME real.
- Remover metadados sensiveis quando aplicavel.
- Executar antivirus ou scanner equivalente antes do uso operacional; este gate continua pendente.
- Gerar storage key server-side, nunca enviada pelo cliente.
- Registrar auditoria sanitizada de upload.
- Manter arquivo pendente ate moderacao.
- Bloquear publicacao automatica.
- Validar rollback e expurgo futuro.

## Fotos, Premium e beneficios

- Plano base permite ate 4 fotos.
- `FOTOS_EXTRA_5` ativo eleva o total permitido para ate 10 fotos.
- Cada anuncio permite ate 1 video.
- O backend retorna e aplica os limites; o frontend apenas os apresenta.
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
| `R2_ENABLED` | Sim | homologacao | Ativa a abstracao unica R2. |
| `R2_ENDPOINT` | Sim | homologacao | Endpoint externo fora do Git. |
| `R2_REGION` | Sim | homologacao | Regiao SigV4; Cloudflare usa `auto`. |
| `R2_PUBLIC_MEDIA_BUCKET` | Sim | midia publica | Bucket exclusivo de HML. |
| `R2_PRIVATE_MEDIA_BUCKET` | Sim | midia privada | Sem acesso publico. |
| `R2_DOCUMENT_BUCKET` | Sim | documento privado | Nunca publicavel. |
| `R2_PUBLIC_MEDIA_PREFIX` | Sim | midia publica | Restrito a `hml/midias-aprovadas/`. |
| `R2_PRIVATE_MEDIA_PREFIX` | Sim | midia privada | Restrito a `hml/midias-pendentes/`. |
| `R2_DOCUMENT_PREFIX` | Sim | documento privado | Restrito a `hml/documentos/`. |
| `R2_ACCESS_KEY` | Sim | secret externo | Fora do Git e dos relatorios. |
| `R2_SIGNING_VALUE` | Sim | material de assinatura externo | Fora do Git e dos relatorios. |
| `R2_PUBLIC_BASE_URL` | Nao nesta fase | midia publica | Vazio ate dominio publico HML ser aprovado. |
| `R2_PRESERVED_PUBLIC_MEDIA_BUCKET` | Somente no cutover | midia legada `LIVRE` | Bucket publico de origem auditado; nunca usado como destino de escrita V3. |
| `R2_PRESERVED_PUBLIC_MEDIA_PREFIX` | Somente no cutover | midia legada `LIVRE` | Prefixo exato auditado; nao aceita caminho pai, relativo ou fora do padrao. |
| `R2_PRESERVED_PUBLIC_BASE_URL` | Somente no cutover | midia legada `LIVRE` | Origem HTTPS publica existente; deve coincidir com bucket/prefixo e nao substitui `R2_PUBLIC_BASE_URL`. |
| `R2_SIGNED_URL_TTL_SECONDS` | Sim | objetos privados | TTL curto, limitado a no maximo sete dias. |
| `UPLOAD_MAX_IMAGE_BYTES` | Sim antes de upload | upload | Valor definido por politica. |
| `UPLOAD_MAX_VIDEO_BYTES` | Sim | upload de video | Valor definido por politica. |

## Pendencias antes de homologacao real

- Definir dominio publico HML somente para o bucket de midias aprovadas.
- Validar antivirus/moderacao real.
- Validar upload autenticado com arquivos sinteticos autorizados no HML.
- Validar CDN/cache/invalidation em homologacao.
- Revisar Pro antes de documento real, midia real, dados reais/sanitizados ou cutover.

## Proibicoes

- Nao usar producao.
- Nao usar dado real.
- Nao executar upload com dado ou midia real sem autorizacao propria.
- Nao acessar buckets ou credenciais de producao.
- Nao habilitar acesso publico para midia privada ou documento.
- Nao versionar URL privada, storage key, bucket real, credencial, log bruto, midia real ou documento real.
