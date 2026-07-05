# Relatorio - campos sensiveis sanitizados na quarentena

- Bloco: 29.5
- Resultado: OK_CAMPOS_SENSIVEIS_SANITIZADOS_QUARENTENA_COM_ALERTA_RESIDUAL
- Colunas sensiveis candidatas tratadas: 153
- Erros agregados de sanitizacao por coluna: 4
- Classificacao dos erros agregados: `ALERTA_SANITIZACAO_AGREGADA_NAO_CLASSIFICADA`
- Valores brutos listados: nao
- Slugs reais listados: nao
- Nome real de tabela/coluna/constraint/ID/slug/valor listado: nao
- CPF/RG/documento/selfie: sanitizados quando presentes.
- Nome civil: sanitizado quando presente.
- E-mail/telefone/WhatsApp: sanitizados quando presentes.
- Endereco especifico/IP/user-agent: sanitizados quando presentes.
- Token/senha/certificado/storage/bucket/URL: sanitizados quando presentes.
- Payload financeiro/Pix/Efi/log sensivel: sanitizados quando presentes.
- Validacao posterior por padroes sensiveis: `total_sensivel=0`.
- Aprovacao final com dados sanitizados depende de revisao Pro/humana ou novo bloco para classificar os 4 erros agregados.
- Banco de quarentena continua proibido para staging final.
