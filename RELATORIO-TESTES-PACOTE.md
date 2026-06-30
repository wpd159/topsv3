# Relatório de testes do pacote

- Total de cenários: 51
- Cenários OK: 51
- Cenários com falha: 0

| Cenário | Categoria | Exit code esperado | Exit code obtido | Resultado |
| --- | --- | ---: | ---: | --- |
| arquivo criado e incluído | manifesto | 0 | 0 | OK |
| inventário válido aceito | inventario | 0 | 0 | OK |
| inventário sem BOM bloqueia | inventario | 2 | 2 | OK |
| inventário com coluna ausente bloqueia | inventario | 2 | 2 | OK |
| inventário com coluna extra bloqueia | inventario | 2 | 2 | OK |
| inventário com hash inválido bloqueia | inventario | 2 | 2 | OK |
| inventário com caminho duplicado bloqueia | inventario | 2 | 2 | OK |
| alteração tracked unstaged bloqueia pacote | indice | 2 | 2 | OK |
| arquivo novo unstaged bloqueia pacote | indice | 2 | 2 | OK |
| pacote OK quando tudo está staged | indice | 0 | 0 | OK |
| deleção unstaged bloqueia pacote | indice | 2 | 2 | OK |
| arquivo modificado e incluído | manifesto | 0 | 0 | OK |
| staged anterior sem mudança excluído | manifesto | 0 | 0 | OK |
| arquivo alterado antes do inventário não entra | manifesto | 0 | 0 | OK |
| mudança apenas no índice detectada como divergência | índice | 2 | 2 | OK |
| divergência índice workspace bloqueia | índice | 2 | 2 | OK |
| arquivo ignorado bloqueia | proibidos | 2 | 2 | OK |
| arquivo proibido bloqueia | proibidos | 2 | 2 | OK |
| .zip.example bloqueia | proibidos | 2 | 2 | OK |
| .p12.example bloqueia | proibidos | 2 | 2 | OK |
| destino dentro do repositório bloqueia | caminho | 2 | 2 | OK |
| arquivo oculto gitignore incluído quando candidato | manifesto | 0 | 0 | OK |
| caminhos com espaços e acentos funcionam | unicode | 0 | 0 | OK |
| manifesto confere | manifesto | 0 | 0 | OK |
| manifesto interno possui BOM | csv | 0 | 0 | OK |
| hash do manifesto real corresponde ao arquivo interno | manifesto | 0 | 0 | OK |
| manifesto com hash alterado | manifesto | 2 | 2 | OK |
| manifesto com tamanho alterado | manifesto | 2 | 2 | OK |
| manifesto com caminho adicional | manifesto | 2 | 2 | OK |
| manifesto com caminho ausente | manifesto | 2 | 2 | OK |
| manifesto com coluna ausente | manifesto | 2 | 2 | OK |
| manifesto com coluna extra | manifesto | 2 | 2 | OK |
| manifesto sem BOM | manifesto | 2 | 2 | OK |
| manifesto com caminho duplicado | manifesto | 2 | 2 | OK |
| manifesto com tipo inválido | manifesto | 2 | 2 | OK |
| ZIP com entrada extra falha | zip | 2 | 2 | OK |
| ZIP com hash divergente falha | zip | 2 | 2 | OK |
| traversal com slash bloqueia | zip | 2 | 2 | OK |
| traversal com backslash bloqueia | zip | 2 | 2 | OK |
| entrada duplicada por caixa bloqueia | zip | 2 | 2 | OK |
| controle contendo secret bloqueia | secrets | 2 | 2 | OK |
| metadado contendo secret bloqueia | secrets | 2 | 2 | OK |
| symlink staged bloqueia | modos-git | 2 | 2 | OK |
| gitlink staged bloqueia | modos-git | 2 | 2 | OK |
| ZIP parcial e excluído | zip | 2 | 2 | OK |
| resumo UTF-8 correto | codificação | 0 | 0 | OK |
| lista de arquivos uma entrada por linha | resumo | 0 | 0 | OK |
| ZIP não sobrescreve pacote anterior | zip | 0 | 0 | OK |
| stdout e stderr sem deadlock | processo | 0 | 0 | OK |
| CSV com BOM em pwsh quando disponível | csv-pendente | 0 | 0 | OK |
| temporários removidos após suíte | temporários | 0 | 0 | OK |
