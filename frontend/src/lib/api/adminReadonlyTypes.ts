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
