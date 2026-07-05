# Relatorio - sanitizacao DB local

- Bloco: 29
- Container esperado: `topsv3-bloco29-postgres`
- Banco sanitizado esperado: `topsv3_prod_sanitizado_bloco29`
- Restore local disponivel: False
- Producao alterada: nao
- Banco de producao acessado: nao
- Midia real copiada: nao
- Documento real copiado: nao

## Resultado

PENDENTE_RESTORE_LOCAL_ISOLADO

A sanitizacao nao foi executada porque o restore local isolado ainda nao esta disponivel.

## Campos/tipos previstos para sanitizacao

- CPF/RG/documento/selfie/documento com foto;
- nome civil;
- e-mail, telefone e WhatsApp reais;
- endereco especifico sensivel;
- IP e user-agent brutos;
- token, senha, certificado;
- storage key, bucket e URL privada de midia;
- payload financeiro e identificadores Pix/Efi sensiveis;
- logs sensiveis.
