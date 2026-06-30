# Dry-run estrutural da importacao

Status da entrega: `CONTRATO DE DRY-RUN ESTRUTURAL, SEM EXECUCAO REAL`.

## Definicao

Dry-run estrutural, nesta fase, significa validar em memoria se o plano futuro possui etapas, ordem, dependencias e gates suficientes para impedir uma importacao real insegura.

Ele nao e dry-run de dados reais. Nenhum arquivo de origem e aberto, nenhum dump e lido, nenhum banco e conectado e nenhum item e transformado.

## Complemento da Fase 2F

A Fase 2F adiciona o gate de fonte real autorizada antes de qualquer dry-run real. O dry-run estrutural continua em memoria e sem fonte real.

Um dry-run real futuro so podera ocorrer com pacote fora do repositorio, manifesto, checksums, origem declarada, data/hora de extracao, responsavel pela geracao, autorizacao expressa e aprovacao especifica.

## Entradas permitidas nesta fase

- DTOs em memoria;
- catalogos estruturais criados nas Fases 2B, 2C, 2D e 2E;
- textos documentais sanitizados;
- testes unitarios declarativos;
- compilacao Java local com `javac`, quando disponivel.

## Entradas proibidas nesta fase

- dump legado;
- export real de usuarios, anuncios, pagamentos, creditos, metricas ou midias;
- manifesto real de storage;
- arquivo real de entrada do importador;
- banco local persistente;
- banco de producao;
- VPS;
- Efi real;
- API externa;
- storage real;
- Docker/Flyway para executar importacao.

## Resultado esperado

O resultado conceitual do dry-run estrutural deve indicar:

- se o plano e valido;
- quais pendencias existem;
- quais etapas estao aptas para dry-run estrutural;
- quais etapas bloqueiam importacao real;
- se existe relatorio de dry-run;
- se existe gate de bloqueio por pendencia critica.

Na Fase 2E, isso aparece em `ResultadoPlanoImportacaoDto`. O resultado nao e relatorio operacional de dados reais.

## Pendencias de plano

Codigos estruturais adicionados:

- `PLANO_ETAPA_SEM_TIPO`;
- `PLANO_ETAPA_SEM_ORDEM`;
- `PLANO_ETAPA_CRITICA_SEM_DESCRICAO`;
- `PLANO_DEPENDENCIA_INEXISTENTE`;
- `PLANO_ANUNCIO_SEM_DEPENDENCIA_USUARIO_LOCALIDADE`;
- `PLANO_MIDIA_SEM_DEPENDENCIA_ANUNCIO`;
- `PLANO_CREDITO_SEM_DEPENDENCIA_PAGAMENTO`;
- `PLANO_SEO_URL_SEM_DEPENDENCIA_ANUNCIO_LOCALIDADE`;
- `PLANO_SEM_RELATORIO_DRY_RUN`;
- `PLANO_SEM_BLOQUEIO_PENDENCIA_CRITICA`.

Esses codigos validam somente a estrutura do plano. Eles nao criam tabela, migration, SQL, entidade JPA, repository, service, controller ou endpoint.

## Bloqueio de importacao real

A etapa `BLOQUEAR_IMPORTACAO_COM_PENDENCIA_CRITICA` representa o gate obrigatorio: qualquer pendencia bloqueante futura deve impedir importacao, promocao, cutover ou go-live.

Esse gate tambem protege:

- divergencia financeira;
- pagamento sem evidencia;
- credito sem reconciliacao;
- documento privado publicavel;
- midia sem manifesto;
- URL publica sem decisao;
- schema sem aprovacao Pro.

## Testes estruturais

Os testes da fase verificam:

- catalogo com todos os tipos de etapa;
- anuncio dependente de usuario e localidade;
- midia dependente de anuncio;
- creditos dependentes de pagamentos ou alternativa auditavel;
- existencia de relatorio de dry-run;
- existencia de bloqueio critico;
- validador sem acesso a filesystem.

Quando Maven/JUnit nao estiverem disponiveis localmente sem download, a validacao executavel da fase fica restrita ao `javac` dos arquivos principais e aos scanners locais.

## Riscos residuais

- O plano ainda nao foi validado contra fonte real autorizada.
- O dicionario definitivo ainda depende da fonte real.
- O dry-run real de dados depende de fase futura.
- O schema segue condicionado a revisao Pro.
- Regras juridicas finais de retencao documental permanecem para fase futura.
