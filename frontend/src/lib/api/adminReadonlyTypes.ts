export type AdminContadorDto = {
  codigo: string;
  rotulo: string;
  total: number;
};

export type AdminResumoAnunciosDto = {
  totalAtivos: number;
  publicados: number;
  pendentesRevisao: number;
  pausados: number;
  bloqueados: number;
  comContatoConfigurado: number;
  porStatus: readonly AdminContadorDto[];
};

export type AdminResumoModeracaoDto = {
  revisoesAbertas: number;
  revisoesEmAnalise: number;
  anunciosPendentesModeracao: number;
  anunciosBloqueados: number;
  documentosPendentes: number;
};

export type AdminResumoMidiasDto = {
  arquivosTotal: number;
  arquivosPendentes: number;
  arquivosValidados: number;
  midiasPublicaveis: number;
  midiasPendentes: number;
  midiasBloqueadas: number;
  storiesPublicados: number;
  storiesPendentes: number;
};

export type AdminResumoMetricasDto = {
  visualizacoesTotal: number;
  cliquesWhatsappTotal: number;
  cliquesWhatsappPermitidos: number;
  cliquesWhatsappBloqueados: number;
};

export type AdminStatusSistemaDto = {
  app: string;
  ambiente: string;
  local: boolean;
  efiPixMockMode: boolean;
  politicaApi: string;
  pendenciaCsrf: string;
};

export type AdminVisaoGeralDto = {
  contadores: readonly AdminContadorDto[];
  anuncios: AdminResumoAnunciosDto;
  moderacao: AdminResumoModeracaoDto | null;
  midias: AdminResumoMidiasDto | null;
  metricas: AdminResumoMetricasDto | null;
  sistema: AdminStatusSistemaDto | null;
};

export type AdminPaginaDto<T> = {
  itens: readonly T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
};

export type AdminLocalizacaoSanitizadaDto = {
  uf: string | null;
  cidade: string | null;
  bairro: string | null;
};

export type AdminAnuncioListaItemDto = {
  id: string;
  slug: string | null;
  titulo: string | null;
  status: string | null;
  statusModeracao: string | null;
  classificacaoConteudo: string | null;
  localizacao: AdminLocalizacaoSanitizadaDto | null;
  criadoEm: string | null;
  atualizadoEm: string | null;
  publicadoEm: string | null;
  midiasTotal: number | null;
  revisoesTotal: number | null;
  contatoConfigurado: boolean;
  documentoPendente: boolean;
  comercialLimitado: boolean;
};

export type AdminAnuncioDetalheDto = AdminAnuncioListaItemDto & {
  descricaoResumo: string | null;
  categoria: string | null;
  ultimaPublicacaoEm: string | null;
  precoInformado: boolean;
};

export type AdminMidiaListaItemDto = {
  id: string;
  anuncioId: string | null;
  slugAnuncio: string | null;
  tipo: string | null;
  finalidade: string | null;
  ordem: number | null;
  status: string | null;
  classificacaoConteudo: string | null;
  statusArquivo: string | null;
  mimeType: string | null;
  tamanhoBytes: number | null;
  largura: number | null;
  altura: number | null;
  duracaoMs: number | null;
  criadoEm: string | null;
  atualizadoEm: string | null;
  arquivoPrivadoOculto: boolean;
};

export type AdminMidiaDetalheDto = AdminMidiaListaItemDto & {
  arquivoMidiaId: string | null;
};

export type AdminRevisaoListaItemDto = {
  id: string;
  anuncioId: string | null;
  slugAnuncio: string | null;
  tipo: string | null;
  status: string | null;
  conteudoSolicitadoPresente: boolean;
  criadoEm: string | null;
  finalizadoEm: string | null;
};

export type AdminRevisaoDetalheDto = AdminRevisaoListaItemDto & {
  somenteLeitura: boolean;
};

export type AdminOutboxPreviewDto = {
  canalLogico: string | null;
  destinoLogicoSanitizado: string | null;
  assuntoSanitizado: string | null;
  corpoSanitizado: string | null;
  envioExternoExecutado: boolean;
};

export type AdminOutboxPreviewRenderizadaDto = {
  id: string;
  tipoEvento: string | null;
  status: string | null;
  assuntoSanitizado: string | null;
  corpoSanitizado: string | null;
  canalPrevisto: string | null;
  envioExternoExecutado: boolean;
  somentePreview: boolean;
  camposMascarados: readonly string[];
  pendencias: readonly string[];
};

export type AdminOutboxListaItemDto = {
  id: string;
  tipoEvento: string | null;
  entidadeTipo: string | null;
  entidadeId: string | null;
  status: string | null;
  criadoEm: string | null;
  tentativas: number | null;
  proximaTentativaEm: string | null;
  resumoSanitizado: string | null;
  previa: AdminOutboxPreviewDto | null;
  envioExternoExecutado: boolean;
};

export type AdminOutboxDetalheDto = AdminOutboxListaItemDto & {
  dadosSanitizados: Record<string, unknown>;
  somenteLeitura: boolean;
};

export type AdminOutboxSimularProcessamentoRequestDto = {
  observacao?: string;
  requestIdCliente?: string;
};

export type AdminOutboxSimularProcessamentoResponseDto = {
  id: string;
  statusAntes: string | null;
  statusDepois: string | null;
  statusAlterado: boolean;
  envioExternoExecutado: boolean;
  auditoriaRegistrada: boolean;
  requestId: string;
  processadoEm: string;
  mensagem: string;
};

export type AdminDecisaoModeracaoAcao = "APROVAR" | "REPROVAR" | "SOLICITAR_AJUSTE";

export type AdminDecidirRevisaoRequestDto = {
  decisao: AdminDecisaoModeracaoAcao;
  classificacaoConteudo?: "LIVRE" | "BLOQUEADO";
  motivo?: string;
  observacao?: string;
  requestIdCliente?: string;
};

export type AdminDecidirMidiaRequestDto = {
  decisao: AdminDecisaoModeracaoAcao;
  classificacaoConteudo?: "LIVRE" | "BLOQUEADO";
  motivo?: string;
  observacao?: string;
  requestIdCliente?: string;
};

export type AdminRemeterRevisaoRequestDto = {
  motivo?: string;
  observacao?: string;
  requestIdCliente?: string;
};

export type AdminAcaoModeracaoResponseDto = {
  id: string;
  recursoTipo: string;
  recursoId: string;
  decisao: string;
  status: string;
  classificacaoConteudo: string;
  auditoriaRegistrada: boolean;
  emailRealEnviado: boolean;
  hardDeleteExecutado: boolean;
  requestId: string;
  decididoEm: string;
  mensagem: string;
};

export type AdminBeneficioAnuncioDto = {
  id: string;
  beneficioCodigo: string | null;
  beneficioNome: string | null;
  escopo: string | null;
  statusOriginal: string | null;
  statusCalculado: string | null;
  inicioEm: string | null;
  fimEm: string | null;
  venceEmBreve: boolean;
  grupoVinculado: boolean;
  grupoId: string | null;
  grupoTipo: string | null;
  grupoStatus: string | null;
  grupoFimEm: string | null;
  codigosConsistencia: readonly string[];
  inconsistente: boolean;
  somenteLeitura: boolean;
};

export type AdminPremiumAnuncioStatusDto = {
  anuncioId: string;
  slug: string | null;
  titulo: string | null;
  premiumAtivo: boolean;
  destaqueAtivo: boolean;
  topoAtivo: boolean;
  possuiStories: boolean;
  possuiMidiaExtra: boolean;
  beneficiosAtivos: number;
  beneficiosExpirados: number;
  beneficiosVencendo: number;
  inconsistenciasTotal: number;
  codigosConsistencia: readonly string[];
  calculadoEm: string | null;
  somenteLeitura: boolean;
  compraOuAtivacaoRealDisponivel: boolean;
  acoesFinanceirasDisponiveis: boolean;
  gratuitoLimitadoPorContato: boolean;
};

export type AdminPremiumConsistenciaItemDto = {
  anuncioId: string | null;
  slug: string | null;
  ativacaoId: string | null;
  beneficioCodigo: string | null;
  codigo: string;
  severidade: string;
  mensagem: string;
  statusCalculado: string | null;
  fimEm: string | null;
  grupoId: string | null;
  grupoStatus: string | null;
  grupoFimEm: string | null;
  detectadoEm: string | null;
};

export type AdminPremiumConsistenciaResumoDto = {
  itens: readonly AdminPremiumConsistenciaItemDto[];
  total: number;
  calculadoEm: string | null;
  somenteLeitura: boolean;
};

export type AdminPremiumVencendoItemDto = {
  anuncioId: string | null;
  slug: string | null;
  ativacaoId: string;
  beneficioCodigo: string | null;
  statusCalculado: string | null;
  fimEm: string | null;
  diasRestantes: number;
  grupoVinculado: boolean;
  somenteLeitura: boolean;
};

export type AdminPremiumVencendoResumoDto = {
  itens: readonly AdminPremiumVencendoItemDto[];
  total: number;
  janelaDias: number;
  calculadoEm: string | null;
  somenteLeitura: boolean;
};

export type AdminCreditoSaldoDto = {
  usuarioId: string;
  saldoProjetado: number | null;
  saldoCalculadoMovimentos: number | null;
  saldoUltimoMovimento: number | null;
  totalMovimentos: number;
  totalEntradas: number;
  totalSaidas: number;
  consistente: boolean;
  codigosConsistencia: readonly string[];
  atualizadoEm: string | null;
  somenteLeitura: boolean;
};

export type AdminCreditoMovimentoDto = {
  id: string;
  usuarioId: string;
  tipo: string | null;
  direcao: string | null;
  quantidade: number | null;
  saldoAntes: number | null;
  saldoDepois: number | null;
  origem: string | null;
  referenciaTipo: string | null;
  referenciaId: string | null;
  chaveOperacionalPresente: boolean;
  criadoEm: string | null;
  somenteLeitura: boolean;
};

export type AdminCreditoPaginaDto<T> = {
  itens: readonly T[];
  total: number;
  pagina: number;
  tamanho: number;
  somenteLeitura: boolean;
};

export type AdminCreditoConsistenciaItemDto = {
  usuarioId: string | null;
  movimentoId: string | null;
  pagamentoId: string | null;
  codigo: string;
  severidade: string;
  mensagem: string;
  detectadoEm: string | null;
  somenteLeitura: boolean;
};

export type AdminCreditoConsistenciaResumoDto = {
  itens: readonly AdminCreditoConsistenciaItemDto[];
  total: number;
  calculadoEm: string | null;
  somenteLeitura: boolean;
};

export type AdminPagamentoListaItemDto = {
  id: string;
  usuarioId: string;
  provedorDeclarado: string | null;
  provedorClassificado: string | null;
  metodo: string | null;
  statusInterno: string | null;
  statusOperacional: string | null;
  quantidadeCreditos: number | null;
  moeda: string | null;
  evidenciaTransacaoPresente: boolean;
  evidenciaTransacaoMascarada: string | null;
  evidenciaProvedorPresente: boolean;
  creditoVinculado: boolean;
  criadoEm: string | null;
  atualizadoEm: string | null;
  somenteLeitura: boolean;
};

export type AdminPagamentoDetalheDto = AdminPagamentoListaItemDto & {
  eventosSanitizadosTotal: number;
  webhooksSanitizadosTotal: number;
  codigosConsistencia: readonly string[];
  payloadSensivelOculto: boolean;
  cobrancaRealDisponivel: boolean;
  webhookRealProcessado: boolean;
  pixEfiRealExecutado: boolean;
};

export type AdminPagamentoPaginaDto<T> = {
  itens: readonly T[];
  total: number;
  pagina: number;
  tamanho: number;
  somenteLeitura: boolean;
};

export type AdminPagamentoConsistenciaItemDto = {
  usuarioId: string | null;
  pagamentoId: string | null;
  movimentoCreditoId: string | null;
  codigo: string;
  severidade: string;
  mensagem: string;
  detectadoEm: string | null;
  somenteLeitura: boolean;
};

export type AdminPagamentoConsistenciaResumoDto = {
  itens: readonly AdminPagamentoConsistenciaItemDto[];
  total: number;
  calculadoEm: string | null;
  somenteLeitura: boolean;
};

export type AdminDesempenhoDiarioDto = {
  dataReferencia: string | null;
  visualizacoes: number;
  cliquesWhatsapp: number;
  taxaCliqueView: number;
  premiumAtivo: boolean;
  somenteLeitura: boolean;
};

export type AdminDesempenhoOrigemDto = {
  uf: string | null;
  cidade: string | null;
  bairro: string | null;
  visualizacoes: number;
  cliquesWhatsapp: number;
  taxaCliqueView: number;
  origemAgregada: boolean;
  somenteLeitura: boolean;
};

export type AdminDesempenhoComparativoPremiumDto = {
  visualizacoesOrganicas: number;
  cliquesOrganicos: number;
  taxaCliqueViewOrganica: number;
  visualizacoesComPremium: number;
  cliquesComPremium: number;
  taxaCliqueViewPremium: number;
  beneficiosExposicaoAtivos: readonly string[];
  mensagemSegura: string;
  promessaResultadoGarantido: boolean;
  gratuitoLimitado: boolean;
  somenteLeitura: boolean;
};

export type AdminDesempenhoAnuncioDto = {
  anuncioId: string;
  usuarioId: string | null;
  slug: string | null;
  titulo: string | null;
  visualizacoesTotal: number;
  cliquesWhatsappTotal: number;
  taxaCliqueView: number;
  diario: readonly AdminDesempenhoDiarioDto[];
  origens: readonly AdminDesempenhoOrigemDto[];
  comparativoPremium: AdminDesempenhoComparativoPremiumDto;
  avisoResultado: string;
  dadosSensiveisOcultos: boolean;
  trackingExternoExecutado: boolean;
  somenteLeitura: boolean;
};

export type AdminDesempenhoAnuncianteDto = {
  usuarioId: string;
  anunciosTotal: number;
  visualizacoesTotal: number;
  cliquesWhatsappTotal: number;
  taxaCliqueView: number;
  anuncios: readonly AdminDesempenhoAnuncioDto[];
  avisoResultado: string;
  endpointAnuncianteRealDisponivel: boolean;
  dadosSensiveisOcultos: boolean;
  somenteLeitura: boolean;
};

export type AdminDesempenhoResumoDto = {
  anunciosComMetricas: number;
  visualizacoesTotal: number;
  cliquesWhatsappTotal: number;
  taxaCliqueView: number;
  visualizacoesOrganicas: number;
  visualizacoesComPremium: number;
  mensagemSegura: string;
  calculadoEm: string | null;
  trackingExternoExecutado: boolean;
  gratuitoLimitado: boolean;
  dadosSensiveisOcultos: boolean;
  somenteLeitura: boolean;
};
