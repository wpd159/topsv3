# Checklist da Fase 2A - importador saneador

Status da entrega: `OK PARA ESTRUTURA LOCAL, SEM IMPORTACAO REAL`.

## Checklist

- [x] Inventário inicial criado fora do repositório.
- [x] Estrutura do módulo `importacao` criada.
- [x] Subpacotes `model`, `relatorio`, `validacao`, `manifesto` e `mapeamento` criados.
- [x] Enums de pendências criados.
- [x] Códigos obrigatórios de pendência criados.
- [x] DTOs/records de relatório criados.
- [x] Manifesto de mídia conceitual criado.
- [x] Mapa legado -> V3 criado.
- [x] Mapa URL atual -> V3 criado.
- [x] Builder de relatório em memória criado.
- [x] Skeleton estrutural de importação em memória criado.
- [x] Testes unitários estruturais criados.
- [x] Nenhum dado real usado.
- [x] Nenhum dump lido.
- [x] Nenhuma conexão com banco criada.
- [x] Nenhuma entidade JPA criada.
- [x] Nenhum repository criado.
- [x] Nenhum controller de domínio criado.
- [x] Nenhum endpoint funcional criado.
- [x] Nenhuma migration criada.
- [x] Nenhum SQL alterado.
- [x] Nenhuma API externa acessada.
- [x] Nenhuma produção, VPS, Efí real ou OpenAI acessada.
- [x] Nenhum remote configurado.
- [x] Nenhum push executado.
- [x] Nenhum commit executado.
- [x] Scanners executados.
- [x] Validação de rotas públicas/SEO executada.
- [x] Validação SQL estática executada.
- [ ] Testes unitários executados.
- [x] ZIP final criado e validado.

## Motivo de teste pendente

Os testes unitários foram criados, mas não foram executados nesta fase porque não há Maven nem Maven Wrapper local disponível sem download. Executar build/testes com download de dependências permanece bloqueado.

## Gates preservados

A Fase 2A preservou os seguintes gates:

- Importação real não iniciada.
- Dump real não lido.
- Banco não acessado.
- Importador funcional não criado.
- Backend de domínio dependente do schema não iniciado.
- Fase 3 ou fase posterior não iniciada.

As caixas desmarcadas anteriores foram removidas para evitar leitura ambígua. A fase criou somente estrutura local e contratos em memória.
