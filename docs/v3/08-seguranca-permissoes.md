# Segurança e permissões

## Objetivo

Definir autenticação, sessões, papéis, permissões, tokens, rate limit, logs, auditoria e proteção de dados sensíveis da V3.

## Papéis obrigatórios

### `ADMIN`

Responsável por operações administrativas completas, configurações, staff, financeiro sensível, backup, SEO crítico e auditoria.

### `MODERADOR`

Responsável por revisar anúncios, mídias, documentos, denúncias e conteúdo sujeito a moderação.

### `COMERCIAL`

Responsável por relacionamento comercial, contatos, oportunidades, acompanhamento e ações não destrutivas.

### `USUARIO`

Papel operacional dos anunciantes/usuários finais. Incluído para completar a matriz, embora os papéis administrativos obrigatórios sejam os três acima.

## Matriz de permissões

Permissões devem ser granulares. Exemplos:

- `USUARIO_LER`
- `USUARIO_EDITAR`
- `ANUNCIO_MODERAR`
- `ANUNCIO_EDITAR_STAFF`
- `MIDIA_REVISAR`
- `PREMIUM_GERENCIAR`
- `CREDITO_AJUSTAR`
- `FINANCEIRO_LER`
- `FINANCEIRO_CONCILIAR`
- `SEO_GERENCIAR`
- `BANNER_GERENCIAR`
- `BACKUP_GERENCIAR`
- `AUDITORIA_LER`
- `COMERCIAL_GERENCIAR`
- `SUPORTE_ATENDER`

Regras:

- Papel não deve depender apenas de prefixo de URL.
- Método/ação crítica deve validar permissão.
- Permissões administrativas devem ser testadas por papel.
- COMERCIAL não deve excluir, aprovar, ajustar crédito ou alterar backup por padrão.

## Autenticação

Requisitos:

- Senha armazenada apenas como hash forte.
- Login com resposta neutra quando aplicável.
- Autenticação web baseada em sessão server-side.
- Sessão opaca gerenciada pelo backend.
- Cookie `HttpOnly`.
- Cookie `Secure` fora do ambiente local.
- `SameSite=Lax` por padrão.
- Nenhum token principal de autenticação do navegador no `localStorage`.
- JWT não será usado como mecanismo principal da autenticação do navegador.
- Futuros tokens técnicos terão contrato e ADR separados.
- Sessões revogáveis por usuário, dispositivo e administrador.
- Armazenamento inicial das sessões no PostgreSQL.
- Rotação da sessão após login e elevação de privilégio.
- Expiração absoluta e expiração por inatividade.
- CSRF habilitado em operações autenticadas que alteram estado.
- CORS restrito por ambiente.
- ADMIN, MODERADOR e COMERCIAL usam senha mais token por e-mail obrigatório na versão inicial.
- TOTP/passkey permanecem como evolução futura.

## Token por e-mail

Tokens por e-mail devem usar tabela `token_seguranca`.

Regras:

- Armazenar hash do token, nunca token puro.
- Finalidade explícita: confirmação, recuperação, convite, reautenticação.
- Expiração curta.
- Consumo único.
- Contador de tentativas.
- Rate limit por IP, e-mail e usuário.
- Resposta neutra para evitar enumeração.
- Auditoria de solicitação e consumo.

## Rate limit

Aplicar rate limit em:

- login;
- recuperação de senha;
- validação de token;
- 2FA/MFA;
- cadastro;
- upload;
- webhook;
- clique WhatsApp;
- visualizações;
- endpoints públicos caros de busca;
- ações administrativas sensíveis.

## Bloqueio por tentativa

Regras:

- Bloqueio progressivo por IP/usuário/fingerprint quando houver abuso.
- Bloqueio administrativo auditável.
- Não expor se e-mail existe.
- Alertar em padrões suspeitos contra staff.

## Logs

Logs devem ser estruturados e mascarados.

Não logar:

- senha;
- hash de senha;
- token;
- código de recuperação;
- secret;
- certificado;
- senha do certificado;
- access token;
- client secret;
- payload financeiro completo sensível;
- QR Code Pix;
- Pix cópia e cola;
- CPF;
- nome completo do comprador junto com dados financeiros;
- documentos pessoais;
- dados pessoais além do mínimo operacional.

Logs devem conter:

- request id;
- ator quando autenticado;
- IP;
- user-agent;
- ação;
- recurso;
- resultado;
- latência;
- erro resumido.

## Auditoria de IP

Eventos auditáveis:

- login;
- falha de login relevante;
- logout/revogação;
- alteração de senha;
- alteração de papel/permissão;
- aprovação/rejeição de anúncio;
- ajuste de crédito;
- ativação/revogação premium;
- alteração de banner;
- alteração SEO;
- execução de backup;
- tentativa de acesso negada.

## Revogação de sessão

Deve existir:

- revogar sessão atual;
- revogar todas as sessões do usuário;
- revogar sessões de staff por admin;
- revogar por mudança de senha;
- revogar por suspeita de incidente;
- listar sessões ativas para admin autorizado.

## Proteção contra exposição de senha/hash/token

Regras:

- DTOs nunca retornam hash de senha.
- Entidades de credencial não são serializadas diretamente.
- Tokens nunca aparecem em query string quando houver alternativa segura.
- Tokens em URL devem expirar rapidamente e ser consumidos uma vez.
- Dumps de banco não ficam no repositório.
- Exportacoes administrativas mascaram dados sensíveis.

## CSRF, CORS e cookies

Regras:

- CSRF deve ser implementado em operações autenticadas que alteram estado.
- Cookie de sessão deve ser `HttpOnly`.
- Cookie de sessão deve ser `Secure` fora do ambiente local.
- Cookie de sessão deve usar `SameSite=Lax` por padrão.
- Domínio do cookie deve ter escopo mínimo.
- CORS deve usar allowlist por ambiente.
- Métodos e headers permitidos devem ser mínimos.

## Upload e mídia

Regras:

- Limites por tipo de arquivo.
- Validação MIME real.
- Validação de dimensões/duração.
- Scanner de segurança quando disponível.
- Upload direto/presigned quando aplicável.
- Documentos privados separados de mídia pública.

## Secrets

Regras:

- Secrets em cofre ou configuração externa segura.
- Nenhum secret no Git, JAR, frontend, log ou ZIP.
- Rotação documentada.
- Certificados financeiros fora do repositório.
- Permissão mínima por ambiente.
- Credenciais e certificado Efí não entram em imagem Docker, backup comum baixável pelo painel ou caminho fixo de produção no código.

## Webhook financeiro Efí

Requisitos:

- validar webhook conforme mecanismo oficial da Efí;
- aplicar allowlist ou validação de origem quando aplicável;
- aplicar rate limit específico;
- impor tamanho máximo de payload;
- validar JSON antes de processar;
- rejeitar evento inválido;
- armazenar hash do evento;
- não confiar apenas no `txid` recebido no corpo do webhook;
- garantir idempotência por identificador de evento, `txid` e `payload_hash`;
- correlacionar por `txid`;
- consultar ativamente a Efí antes da concessão de créditos quando necessário;
- registrar processamento, resultado e erro resumido;
- permitir retentativa segura;
- manter fila/relatório de eventos com falha.

Regra crítica: nunca conceder créditos somente porque um payload público declarou pagamento concluído.

## Privacidade financeira

CPF deve:

- ser coletado apenas quando necessário para a cobrança;
- ter acesso restrito por permissão;
- possuir política de retenção;
- ser mascarado no painel;
- não aparecer em logs;
- não aparecer em mensagens de erro.

Payload financeiro bruto do provedor deve ser evitado. Quando indispensável para auditoria, deve ter proteção, acesso restrito, hash registrado e retenção curta.

## Ambientes

- H2 console apenas em profile local, se existir.
- `show-sql` desativado em produção.
- Staging com noindex.
- Configuração tipada e validada no boot.
- Falha de configuração crítica deve impedir startup.
- Pix Efí deve ter ambientes separados: `LOCAL_MOCK`, `HOMOLOGACAO` e `PRODUCAO`.
- `EFI_PIX_MOCK_MODE=true` deve ser o padrão local.
- O modo mock nunca pode acessar produção.
- Testes reais de Pix devem usar exclusivamente homologação Efí com credenciais externas ao repositório.

## Gate de segurança

A V3 não pode virar produção se:

- houver secret, dump, certificado ou dado pessoal em artefato;
- admin não exigir MFA ou controle equivalente aprovado;
- sessão não for revogável;
- token por e-mail não tiver expiração/tentativas/hash;
- ajuste financeiro não for auditado;
- CORS/CSRF estiverem indefinidos;
- webhook financeiro não for protegido;
- roles não estiverem testadas.
