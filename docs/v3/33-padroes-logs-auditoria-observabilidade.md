# Padrões de logs, auditoria e observabilidade

## Escopo das Fases 1C.2 e 1C.3

Este documento define padrões transversais para logs, auditoria e observabilidade. A Fase 1C.3 implementa apenas logging HTTP básico seguro por `requestId` na camada local. Auditoria persistente, banco, fila, integração externa, APM e regra de negócio continuam fora de escopo.

## Princípios

- Logs são para operação e diagnóstico, não para armazenar dados sensíveis.
- Auditoria registra comandos críticos e decisões relevantes.
- Observabilidade deve permitir correlação por `requestId`.
- Produção, VPS, banco e Efí real permanecem intocados nesta fase.
- Dados pessoais devem ser minimizados, mascarados ou substituídos por hash quando houver justificativa.

## Request id e correlação

Todo fluxo futuro deve propagar:

- `requestId`;
- identificador opaco de usuário autenticado, quando houver;
- origem segura do evento;
- timestamp em UTC;
- resultado da operação.

O `requestId` deve aparecer em:

- resposta HTTP;
- erro padronizado;
- logs da requisição;
- auditoria de comando crítico quando aplicável.

Na Fase 1C.3, o `RequestIdFilter` adiciona `X-Request-Id` na resposta, coloca o valor no MDC durante a requisição e registra log operacional mínimo sem query string, payload, token, cookie ou dado pessoal.

## Logs seguros

Campos permitidos por padrão:

- `timestamp`;
- `level`;
- `requestId`;
- `event`;
- `httpMethod`;
- `pathTemplate`;
- `status`;
- `durationMs`;
- `actorId` opaco quando autenticado;
- `resourceType`;
- `resourceId` opaco quando seguro;
- `result`.

Campos proibidos:

- senha;
- hash de senha;
- token;
- cookie;
- certificado;
- segredo;
- chave privada;
- QR Code Pix;
- Pix copia e cola;
- payload financeiro integral;
- documento pessoal em claro;
- e-mail completo quando não for indispensável;
- telefone completo quando não for indispensável;
- stack trace em resposta HTTP.

## Mascaramento e minimização

Diretrizes:

- e-mail em log operacional deve ser mascarado ou substituído por identificador opaco;
- telefone deve ser mascarado ou hash quando necessário para correlação;
- IP pode ser truncado, hash ou tratado conforme política de segurança;
- payloads de provedores financeiros devem ser resumidos por hash e campos mínimos;
- dados de mídia devem usar IDs e hashes, não URLs privadas ou chaves sensíveis.

## Níveis de log

- `DEBUG`: apenas local, sem dados sensíveis.
- `INFO`: eventos operacionais esperados.
- `WARN`: condição inesperada recuperável ou tentativa negada.
- `ERROR`: falha que exige investigação.

Regra: `DEBUG` não deve ser habilitado em produção sem janela, responsável e revisão.

## Auditoria de comandos críticos

Comandos críticos futuros devem gerar evento de auditoria:

- login administrativo;
- troca de senha;
- revogação de sessão;
- alteração de papel/permissão;
- aprovação/rejeição de anúncio;
- decisão de moderação;
- ativação premium;
- ajuste de crédito;
- aprovação, conciliação, estorno ou cancelamento financeiro;
- alteração de banner publicado;
- alteração SEO crítica;
- execução de importação;
- solicitação de backup;
- restore/teste de restauração;
- alteração de configuração sensível.

O shell admin local da Fase 1C.6B não executa comandos críticos. Ele apenas mapeia módulos futuros. Quando as ações reais forem implementadas, cada comando crítico deverá exigir autenticação, autorização por perfil e evento de auditoria.

Campos mínimos de auditoria:

- `id`;
- `requestId`;
- `atorUsuarioId`;
- `acao`;
- `recursoTipo`;
- `recursoId`;
- `resultado`;
- `motivo`;
- `ipHash`;
- `userAgentHash`;
- `antesHash`;
- `depoisHash`;
- `criadoEm`.

Não armazenar:

- segredo;
- token;
- senha;
- certificado;
- payload financeiro integral;
- dados pessoais desnecessários.

## Resultado de auditoria

Valores planejados:

- `SUCESSO`;
- `NEGADO`;
- `FALHA_VALIDACAO`;
- `FALHA_CONCORRENCIA`;
- `FALHA_INTERNA`.

Esses valores são contrato documental para fases futuras. A decisão final entre CHECK constraint e catálogo fica para a fase de migrations.

## Observabilidade HTTP

Cada requisição futura deve produzir métrica segura:

- total de requests por rota lógica;
- latência por rota lógica;
- contagem por status HTTP;
- erros por código de erro padronizado;
- contagem de 401/403/409/422;
- rate limit quando implementado.

Rotas devem ser registradas como template, não como URL com parâmetros sensíveis.

## Observabilidade de domínio futuro

Eventos futuros que exigem métrica:

- criação e publicação de anúncio;
- decisão de moderação;
- upload/processamento de mídia;
- tentativa e conclusão de pagamento;
- crédito concedido;
- ativação premium;
- webhook recebido/processado;
- importação executada;
- pendência de importação;
- backup solicitado/concluído.

Essas métricas devem usar contagem e IDs opacos, sem payload real.

## Erros e incidentes

Erros 500 devem:

- retornar mensagem genérica ao cliente;
- registrar `requestId`;
- registrar exceção nos logs internos seguros;
- não expor stack trace ao cliente;
- não exibir SQL ou configuração interna.

Incidentes devem ser rastreáveis por `requestId`, janela de tempo e tipo de evento.

## Retenção

Retenção de logs e auditoria deve respeitar:

- LGPD;
- necessidade operacional;
- segurança;
- auditoria financeira;
- moderação;
- backup e rollback.

A política final de retenção será detalhada em fase futura antes de produção.

## Fora de escopo

- configurar stack externa de logs;
- criar tabela de auditoria;
- criar dashboard;
- criar métrica real;
- integrar APM;
- acessar banco;
- acessar produção.
