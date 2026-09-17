# GA4 — medição pública e limites

Base conferida: `f1969b64114e859989b13ccd12f82b19ef75fe0b`. Escopo exclusivo de instrumentação; sem mudança de métricas internas, autorização, anúncios ou SEO.

## Diagnóstico focal

| Apontamento | Resultado na base | Correção / preservação |
| --- | --- | --- |
| Dois loaders ou page_view manual junto ao automático | Não confirmado: um loader, um config; não havia observador manual do Next | Config deixa de emitir page_view. Um observador do Next passa a emitir a navegação, para aplicar privacidade; exige ajuste externo abaixo |
| Clique WhatsApp repetido | Confirmado: gtag anterior à trava; retomada após 403/verificação chamava o mesmo emissor | Emitir somente no handler do clique aceito, depois das travas; retomada sem novo evento |
| Render/hidratação ou touch+click duplicando a ação | Não confirmado: ação apenas no onClick | Preservado; testes de render/reentrada |
| Admin e páginas privadas na coleta | Confirmado no código: loader no layout raiz, sem exclusão de contexto | Allowlist pública e bloqueio dinâmico antes de envio, inclusive entre mudança de URL e efeito React |
| Local/fixtures | CI já desabilitava analytics; ambiente sem flag podia habilitar | Exigir produção, flag literal true e origem HTTPS canônica; demais hosts/portas não coletam |
| Consentimento | Cookie ausente/malformado concedia analytics; gtag continuava chamável após revogação | Exigir analytics=true no cookie existente; preferências iniciam desmarcadas. Sem fila retrospectiva ou temporizador; revogação remove eventos pendentes e bloqueia SDK |
| Dados no payload | Evento explícito não tinha telefone/token; config não limitava URL completa, título ou referrer | Somente tipos de rotas, page numérico e origem externa do referrer; nenhum segmento livre, query arbitrária, título de usuário ou slug no evento |
| Geografia anormal / bots / fraude | Não demonstrado por esta conferência | Nenhuma classificação ou filtro geográfico criado |

`click_whatsapp` continua significando **intenção de clique no card**, inclusive quando o contato está indisponível ou o aviso é cancelado. Não é conversa iniciada, contato concluído, cadastro nem pagamento. O detalhe não tinha esse evento e não recebe nova instrumentação nesta correção. `event_label` passa de slug a `card_anuncio`, evitando identificadores livres; isso interrompe a comparação por anúncio nessa dimensão.

Page views usam tipos como `/anuncios/[slug]` e `/acompanhantes/[uf]/[cidade]`. Não representam URLs navegáveis/canonicals nem mudam indexação; essa agregação perde detalhamento por perfil/localidade no GA4. Termos, seeds, UTMs e fragments não são enviados. Atribuição por campanha e comparações de volume com o histórico ficam limitadas. Navegações são deduplicadas por contexto em memória, não globalmente por usuário.

Nenhum novo banner ou mecanismo de consentimento foi criado: o opt-in continua na tela de cookies existente. Visitantes sem escolha explícita deixam de ser medidos; é efeito esperado da correção, não queda de tráfego comprovada.

## Estado externo observado e requisito para publicação futura

Consulta somente leitura no Chrome em 17/09/2026, fluxo web cujo Measurement ID corresponde ao código:

- Medição otimizada ativa, inclusive alterações de página por histórico, rolagens, cliques de saída, pesquisa, formulários, vídeos e downloads.
- Redação de e-mail ativa; redação de chaves de query inativa.
- Filtro `Internal Traffic`: excluir, estado **Teste**. Não se conferiram IPs/regras de classificação; o filtro em teste não é prova de exclusão do tráfego administrativo.
- Não foram salvas mudanças, ativados filtros ou enviados eventos de teste. Não se usaram estatísticas transitórias da interface para atribuir defeitos históricos.

**Não publicar esta implementação com a medição otimizada acima ativa.** Proposta concreta, a executar somente em futura autorização coordenada com a publicação:

1. Administrador → Coleta e modificação de dados → Fluxos de dados → fluxo web correspondente → Medição otimizada: desativar a medição otimizada. O page_view inicial passa a ser suprimido pelo `send_page_view:false`; histórico é coberto pelo observador manual. Desligar também a coleta automática dos elementos evita que links, pesquisas, formulários e downloads enviem parâmetros fora da allowlist. Não é um filtro destrutivo dos dados já coletados; afeta eventos futuros.
2. Manter o filtro `Internal Traffic` em **Teste**. Não ativá-lo, não adivinhar IPs e não criar regras por cidade. O bloqueio das rotas privadas é feito na aplicação. Sessões administrativas em páginas públicas não são identificadas por identidade nesta alteração; eventual exclusão por tráfego interno exige conferência separada da regra existente.
3. Reconfirmar essas opções antes do deploy e validar o transporte da tag em ambiente de teste isolado. Não usar a aprovação dos testes de comando como prova de processamento/atribuição nos relatórios da propriedade.

O requisito externo é indispensável: `send_page_view:false` não desliga os eventos de histórico da medição otimizada. Fontes oficiais: [page views](https://developers.google.com/analytics/devguides/collection/ga4/views), [controle de privacidade e ga-disable](https://developers.google.com/tag-platform/security/guides/privacy), [campos de configuração](https://developers.google.com/analytics/devguides/collection/ga4/reference/config).

## Validação focal

```text
node scripts/test-ga4-production.mjs
node scripts/test-public-metrics.mjs
```

Os scripts transpilam e executam o código real com o TypeScript existente, efeitos/handlers com hooks controlados e interceptação da fronteira gtag/SDK. Não carregam o SDK remoto nem fazem chamadas à propriedade. Cobrem navegação, repetição de efeitos, ação única, retomada de verificação, contextos negados, consentimento/revogação, parâmetros privados e falha da telemetria sem falha funcional. O CI executa ambos, além dos gates existentes. Isso não comprova entrega real, ausência de bloqueadores no navegador, conversão ou qualidade do tráfego.
