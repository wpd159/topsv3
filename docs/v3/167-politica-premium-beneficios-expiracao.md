# Politica Premium, beneficios e expiracao

## Principios

Premium preserva o comportamento atual aprovado do Tops do Job e novos recursos so podem ser aditivos.

O plano gratuito deve continuar util. A V3 nao cria limite comercial diario de cliques, contatos ou WhatsApp para anuncios gratuitos.

Premium nao e promessa de contratacao. Premium pode aumentar exposicao, organizar midia, destacar o anuncio e oferecer relatorios, sempre com regra auditavel.

## Beneficios considerados

O catalogo local deve preservar beneficios existentes em migrations/docs e dados sinteticos:

- `DESTAQUE`;
- `ANUNCIO_TOPO`;
- `FOTOS_EXTRA`;
- `VIDEO`;
- `STORIES`;
- `CARROSSEL`, quando existir no catalogo futuro;
- `RELATORIO`.

Nomes definitivos dependem de evidencia de producao/revisao Pro. Nome auxiliar usado em dado sintetico nao vira regra final automaticamente.

## Expiracao conjunta

Beneficios ativados por um mesmo grupo, pacote ou campanha devem ser avaliados de forma coerente:

- grupo ativo pode manter beneficios ativos/vencendo;
- grupo expirado faz todos os beneficios do grupo serem tratados como expirados no calculo;
- beneficio ativo apos grupo expirado gera inconsistencia;
- beneficio expirado antes do grupo gera inconsistencia;
- beneficio sem grupo e permitido, mas fica marcado para leitura individual;
- grupo sem beneficio gera inconsistencia operacional.

Este bloco apenas calcula e reporta. Nao executa job, scheduler ou atualizacao em massa.

## Codigos

- `PREMIUM_OK`;
- `BENEFICIO_ATIVO`;
- `BENEFICIO_EXPIRADO`;
- `BENEFICIO_VENCE_EM_BREVE`;
- `BENEFICIO_PENDENTE`;
- `BENEFICIO_SEM_GRUPO`;
- `GRUPO_EXPIRADO_COM_BENEFICIO_ATIVO`;
- `BENEFICIO_EXPIRADO_ANTES_DO_GRUPO`;
- `GRUPO_SEM_BENEFICIOS`;
- `DATA_INVALIDA`;
- `ORIGEM_DESCONHECIDA`.

## Exposicao publica

Publico pode receber apenas flags/rotulos seguros quando o beneficio estiver ativo ou vencendo:

- destaque ativo;
- topo ativo;
- midia extra;
- rotulos publicos de beneficios.

Publico nunca deve receber:

- valor pago;
- credito;
- saldo;
- origem financeira;
- grupo/pacote/campanha administrativo;
- historico de ativacoes;
- dado interno;
- payload;
- telefone bruto;
- documento privado.

## Admin read-only

Admin pode consultar status, beneficios, vencendo e consistencia. O retorno continua sanitizado e sem dado financeiro sensivel.

Papeis locais:

- `ADMIN`: leitura completa do status sanitizado;
- `COMERCIAL`: leitura comercial sem dado financeiro sensivel;
- `MODERADOR`: leitura de status quando util para moderacao;
- `USUARIO`: sem acesso admin.

Nenhum papel ganha autorizacao de compra, ativacao real, credito real, ajuste financeiro ou expiracao real neste bloco.

Creditos/ledger do Bloco 23 sao leitura administrativa separada. Eles podem apontar consistencia entre saldo, movimento e pagamento sintetico, mas nao executam compra, ativacao, ajuste, estorno, conciliacao ou expiracao.

## Complemento Bloco 25 - prova de resultado

Metricas de desempenho podem mostrar comparativo organico/Premium, desde que o texto e os DTOs preservem:

- `promessaResultadoGarantido=false`;
- `gratuitoLimitado=false`;
- Premium como exposicao/tendencia;
- gratuito util e sem limite comercial artificial;
- ausencia de botao ou endpoint de compra, impulsionamento, Pix/Efi, credito ou tracking externo.

## Pendencias Pro

- Confirmar por evidencia de producao os nomes e efeitos comerciais finais.
- Definir politica juridica/comercial de retencao historica de beneficios.
- Definir conciliacao real de pagamento/credito/ativacao.
- Definir ranking final e limites de estoque comercial para topo/destaque.
- Definir rotina futura de expiracao real, com auditoria e rollback.
