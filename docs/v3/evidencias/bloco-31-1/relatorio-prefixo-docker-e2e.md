# Relatorio - prefixo Docker E2E Bloco 31.1

## Resultado

OK - o default do E2E local descartavel foi alterado para `topsv3-e2e-local`.

## Execucao observada

- E2E sintetico executado com prefixo explicito `topsv3-e2e-sintetico`.
- Imagem PostgreSQL local usada: `postgres:16`.
- Container temporario criado/removido pelo script: sim.
- Rede temporaria criada/removida pelo script: sim.
- Volume persistente criado: nao.
- Recursos `topsv3-e2e*` remanescentes apos a execucao: nenhum.

## Regras

- Prefixo default do script base: `topsv3-e2e-local`.
- Prefixo explicito do wrapper sintetico: `topsv3-e2e-sintetico`.
- Prefixos vazios ou genericos como `postgres`, `db`, `local`, `backend` e `frontend` sao bloqueados.
- Prefixos fora de `topsv3-*`, `cripto`, TopsWI ou terceiros sao bloqueados.
- O script remove apenas container/rede que ele mesmo cria com o prefixo aceito.

## Preservacao

Nenhum recurso TopsWI/cripto ou `topsv3-bloco29-*` deve ser parado, removido, conectado ou usado como staging final pelo Bloco 31.1.
