# Relatorio - hardening do validador renderizado

## Resultado

Validador reforcado.

## Script

- `scripts/local/validar-publico-renderizado-sintetico-local.ps1`.

## Novas categorias detectadas

- `status_tecnico_upper_snake`;
- `politica_whatsapp_publico`;
- `politica_exposicao_publica`;
- `estado_agegate_interno`;
- `snake_case_visivel`;
- `upper_snake_case_visivel`;
- `texto_tecnico_generico`.

## Politica de relatorio

O relatorio renderizado lista categoria e rota. Ele nao imprime payload bruto sensivel caso uma violacao apareca no futuro.

## Evidencias de saida

As evidencias do Bloco 32.1 sao gravadas em `docs/v3/evidencias/bloco-32-1/`.
