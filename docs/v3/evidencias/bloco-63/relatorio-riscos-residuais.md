# Relatorio - Riscos residuais do Bloco 63

## Riscos residuais

- O shell visual melhorou a paridade, mas nao resolve detalhe, filtros, wizard completo ou admin.
- A busca visual da home ainda e um stub seguro e nao uma busca funcional completa.
- Categorias em destaque usam cards estaticos temporarios.
- A paridade precisa de revisao visual humana em desktop/mobile.
- O HML precisa receber novo deploy apenas em fase propria e com decisao expressa.

## Riscos mitigados

- Nenhuma chamada ao backend antigo foi copiada.
- Nenhum upload real foi implementado.
- Nenhum Pix/Efi, webhook ou pagamento real foi implementado.
- Nenhum scroll lock foi introduzido.
- Nenhum botao flutuante do clone foi copiado.
- O clone permaneceu somente leitura.

## Bloqueios permanentes ainda ativos

- Producao e cutover seguem bloqueados.
- Dados reais seguem proibidos.
- Restore/importacao real seguem fora do escopo.
- Pix/Efi real, webhook real, upload real e pagamento real seguem fora do escopo.
