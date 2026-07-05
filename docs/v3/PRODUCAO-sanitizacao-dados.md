# Producao - sanitizacao de dados

## Campos/tipos obrigatorios

Sanitizar ou remover:

- CPF, RG, documento, selfie e documento com foto;
- nome civil;
- e-mail real;
- telefone real;
- WhatsApp real;
- endereco especifico sensivel;
- IP bruto;
- user-agent bruto;
- token;
- senha;
- certificado;
- storage key;
- bucket;
- URL privada de midia;
- payload financeiro sensivel;
- identificador Pix/Efi sensivel;
- logs sensiveis.

## Dados que podem ser preservados no banco sanitizado

- IDs relacionais;
- slug publico apenas dentro do banco sanitizado, nunca em relatorio versionado;
- cidade, UF e bairro;
- status do anuncio;
- classificacao mapeavel para `LIVRE`/`BLOQUEADO`;
- datas agregadas;
- status Premium e expiracao;
- contagens agregadas;
- metricas agregadas;
- relacao entre anuncio, cidade, bairro, midia e Premium.

## Midia

- nao copiar arquivo real;
- nao baixar foto/video;
- substituir URL publica/privada por `null` ou placeholder local;
- preservar apenas contagem e metadata sanitizada;
- sanitizar storage key, bucket, provider e hash quando existirem.

## Estado Bloco 29

Sanitizacao real ficou pendente porque o restore local isolado nao foi executado.

## Estado Bloco 29.1

Os scripts foram preparados para sanitizar o banco sanitizado local, nunca o banco bruto. Como o restore continua bloqueado por ausencia de cliente PostgreSQL 17.x local, nenhuma sanitizacao real foi executada nesta rodada.

## Estado Bloco 29.2

Nenhuma sanitizacao real foi executada porque a imagem `postgres:17` nao foi obtida: o Docker daemon local estava indisponivel. A sanitizacao permanece condicionada ao restore local isolado e deve atuar somente no banco sanitizado.

## Estado Bloco 29.3

O restore bruto local foi iniciado em container exclusivo `topsv3-bloco29-pg17-bruto`, mas falhou com `FALHA_PG_RESTORE_RAW`. O container sanitizado `topsv3-bloco29-pg17-sanitizado` foi criado, porem nao recebeu restore completo.

Sanitizacao real nao foi executada. Qualquer nova tentativa deve primeiro resolver o restore sem alterar recursos TopsWI/terceiros e sem versionar dados reais.

## Estado Bloco 29.4

Sanitizacao real continuou bloqueada. O restore bruto falhou novamente mesmo apos limpeza dos recursos proprios e uso de `--single-transaction`.

Os bancos ficaram com 0 tabelas apos a falha, mas ainda nao existe banco sanitizado valido. Nao executar sanitizacao ate o restore completo ser aprovado.

## Estado Bloco 29.5

A sanitizacao passa a ser permitida apenas sobre o banco de quarentena sem `POST_DATA`, no container `topsv3-bloco29-pg17-quarentena`. O resultado continua inadequado para staging final ate decisao Pro/humana.

Resultado: sanitizacao da quarentena executada e validacao agregada retornou total sensivel 0. A aprovacao vale apenas para a quarentena, nao para staging final.
