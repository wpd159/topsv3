# Conformidade, privacidade e conteúdo

## Objetivo

Definir requisitos de LGPD, privacidade, conteúdo sensível, documentos privados, denúncia, retenção e resposta a incidente antes da produção da V3.

Este documento não substitui revisão jurídica. A revisão jurídica é gate obrigatório antes da produção.

## LGPD

Princípios obrigatórios:

- finalidade clara para cada dado pessoal;
- minimização de coleta;
- acesso por necessidade;
- retenção definida;
- descarte, anonimização ou exclusão quando aplicável;
- auditoria de acesso administrativo;
- resposta formal a incidentes.

Dados pessoais não podem ser usados como atalho técnico para busca, logs, identificação de erro ou rastreamento indevido.

## Minimização de dados

Regras:

- coletar CPF apenas quando necessário para cobrança ou obrigação legal;
- não registrar CPF em logs;
- mascarar dados pessoais no painel quando a visualização integral não for necessária;
- separar dados públicos de dados privados;
- evitar payload bruto de provedores externos;
- armazenar hash ou campos normalizados quando isso bastar para auditoria.

## Consentimentos e histórico de aceite

Devem existir registros para:

- termos de uso;
- política de privacidade;
- consentimentos opcionais;
- comunicações comerciais, quando aplicável;
- versão do texto aceito;
- data, IP e user-agent do aceite;
- retirada de consentimento quando aplicável.

O histórico de aceite deve ser auditável e preservado enquanto houver obrigação contratual, regulatória ou defesa de direito.

## Classificação de conteúdo

Conteúdos devem ter classificação operacional para:

- anúncio público;
- mídia pública;
- documento privado;
- conteúdo pendente de moderação;
- conteúdo rejeitado;
- conteúdo removido;
- conteúdo denunciado;
- conteúdo bloqueado preventivamente.

Essa classificação deve orientar exibição, indexação, moderação, retenção e auditoria.

## Controle de acesso por idade

Quando aplicável ao tipo de conteúdo e às regras do negócio, a V3 deve prever:

- aviso de conteúdo adulto;
- barreira de confirmação de idade;
- bloqueio de indexação de áreas inadequadas;
- regras claras para mídia e texto;
- revisão jurídica antes da produção.

## Documentos privados

Regras:

- documentos privados não entram em sitemap, JSON-LD, cache público ou payload público;
- acesso deve ser autenticado, autorizado e auditado;
- URLs devem ser assinadas ou mediadas por backend;
- retenção deve ser definida;
- documentos vencidos ou sem finalidade devem ser removidos, anonimizados ou bloqueados conforme política aprovada.

## Denúncia e retirada de conteúdo

A V3 deve permitir:

- denúncia de anúncio, mídia ou usuário;
- registro de motivo;
- triagem administrativa;
- decisão de manter, remover, bloquear preventivamente ou solicitar ajuste;
- comunicação ao usuário quando aplicável;
- auditoria da decisão.

Conteúdo removido deve preservar rastro mínimo para auditoria e defesa, sem continuar publicável.

## Bloqueio preventivo

Bloqueio preventivo pode ser usado quando houver risco legal, segurança, fraude, exposição indevida de dado pessoal ou denúncia grave.

Regras:

- bloquear exibição pública;
- preservar evidência mínima;
- registrar ator, motivo e horário;
- exigir revisão administrativa;
- evitar destruição silenciosa de dados.

## Retenção

Cada domínio deve declarar retenção:

- usuários;
- sessões;
- documentos privados;
- anúncios e mídia;
- pagamentos e créditos;
- suporte;
- comercial;
- auditoria;
- backups;
- logs.

Quando o número exato ainda não estiver aprovado:

```text
DECISAO_PENDENTE: ADR-008
```

O valor deve estar fechado antes da Fase 8.

## Anonimização e exclusão

Regras:

- anonimizar quando a finalidade puder ser preservada sem identificação pessoal;
- excluir quando não houver obrigação de retenção;
- manter bloqueio de recriação abusiva quando houver base legítima;
- registrar execução de anonimização/exclusão em auditoria sem expor dado sensível.

## Resposta a incidente

O runbook de incidente deve cobrir:

- identificação;
- contenção;
- preservação de evidências;
- análise de impacto;
- comunicação interna;
- comunicação externa quando exigida;
- correção;
- revisão pós-incidente.

Incidente com dado pessoal exige avaliação jurídica.

## Auditoria de acesso

Devem ser auditados:

- acesso administrativo a documentos privados;
- consulta financeira sensível;
- alteração de permissões;
- moderação de conteúdo;
- remoção ou restauração de conteúdo;
- exportação administrativa;
- execução de backup;
- resposta a incidente.

Logs e auditoria não devem armazenar secrets, tokens, CPF em claro, Pix cópia e cola, QR Code ou payload financeiro integral.

## Gate jurídico e de privacidade

A V3 não pode ir para produção sem:

- revisão jurídica registrada;
- política de privacidade revisada;
- termos de uso revisados;
- retenção definida ou formalmente aprovada;
- processo de denúncia e retirada de conteúdo;
- auditoria de acesso a dados sensíveis;
- resposta a incidente documentada.
