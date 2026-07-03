export type LocalizacaoPublicaDto = {
  uf: string | null;
  cidade: string | null;
  cidadeSlug: string | null;
  bairro: string | null;
  bairroSlug: string | null;
  enderecoResumido: string | null;
};

export type MidiaPublicaDto = {
  tipo: string | null;
  finalidade: string | null;
  ordem: number | null;
  urlPublica: string | null;
  pendenciaMidia: string | null;
  largura: number | null;
  altura: number | null;
  mimeType: string | null;
};

export type SeoRotaPublicaDto = {
  title: string | null;
  description: string | null;
  canonicalPath: string;
  robots: string;
  tipoRota: string;
  indexavelFuturo: boolean;
};

export type PaginacaoPublicaDto = {
  pagina: number;
  tamanho: number;
  totalItens: number;
  totalPaginas: number;
};

export type AnuncioCardPublicoDto = {
  slug: string;
  titulo: string | null;
  descricaoResumo: string | null;
  preco: number | null;
  localizacao: LocalizacaoPublicaDto | null;
  midias: readonly MidiaPublicaDto[];
  destaque: boolean;
  topo: boolean;
  midiaExtra: boolean;
  story: boolean;
  beneficiosPublicos: readonly string[];
  publicadoEm: string | null;
};

export type AnuncioDetalhePublicoDto = {
  slug: string;
  titulo: string | null;
  descricao: string | null;
  preco: number | null;
  localizacao: LocalizacaoPublicaDto | null;
  midias: readonly MidiaPublicaDto[];
  destaque: boolean;
  topo: boolean;
  midiaExtra: boolean;
  story: boolean;
  beneficiosPublicos: readonly string[];
  contatoPublico: string | null;
  pendenciaContatoPublico: string | null;
  publicadoEm: string | null;
  seo: SeoRotaPublicaDto;
};

export type MetricaPublicaRequestDto = {
  visitanteLocalId?: string;
  origemPais?: string;
  origemUf?: string;
  origemCidade?: string;
  dispositivo?: "DESKTOP" | "MOBILE" | "TABLET" | "BOT" | "DESCONHECIDO";
};

export type RegistrarVisualizacaoPublicaResponseDto = {
  registrado: boolean;
  slug: string;
  eventoId: string;
  status: string;
  pendenciaStories: string;
};

export type PoliticaContatoPublicoDto = {
  disponivel: boolean;
  motivoPublico: string;
  pendencia: string | null;
};

export type CliqueWhatsappPublicoResponseDto = {
  registrado: boolean;
  disponivel: boolean;
  whatsappUrl: string | null;
  status: string;
  politica: PoliticaContatoPublicoDto;
  pendenciaStories: string;
};

export type ConfirmarIdadePublicaRequestDto = {
  dataNascimento: string;
  declaracaoMaioridade: boolean;
};

export type SolicitarAnuncioPublicoRequestDto = {
  nomeExibicao: string;
  email: string;
  whatsapp: string;
  uf: string;
  cidade: string;
  bairro: string;
  titulo: string;
  descricao: string;
  preco: number;
  categoria: string;
  aceiteTermos: boolean;
  confirmacaoIdade: boolean;
};

export type SolicitarAnuncioPublicoResponseDto = {
  criado: boolean;
  anuncioId: string;
  revisaoId: string;
  slugLocal: string;
  statusAnuncio: string;
  statusModeracao: string;
  classificacaoConteudo: "LIVRE" | "BLOQUEADO" | string;
  publicado: boolean;
  revisaoCriada: boolean;
  publicacaoAutomaticaExecutada: boolean;
  uploadRealExecutado: boolean;
  pagamentoCriado: boolean;
  creditoCriado: boolean;
  premiumObrigatorio: boolean;
  emailRealEnviado: boolean;
  whatsappRealEnviado: boolean;
  mensagem: string;
};

export type SolicitarAnuncioValidationErrorDto = {
  campo: string;
  codigo: string;
  mensagem: string;
};

export type SolicitarAnuncioValidationErrorResponseDto = {
  criado: false;
  mensagem: string;
  erros: readonly SolicitarAnuncioValidationErrorDto[];
  publicacaoAutomaticaExecutada: false;
  uploadRealExecutado: false;
  pagamentoCriado: false;
  creditoCriado: false;
  premiumObrigatorio: false;
  emailRealEnviado: false;
  whatsappRealEnviado: false;
};

export type StatusIdadePublicaDto = {
  confirmada: boolean;
  expiraEm: string | null;
  motivoPublico: string;
};

export type StoryPublicoDto = {
  ordem: number | null;
  tipo: string | null;
  finalidade: string | null;
  classificacaoConteudo: "LIVRE" | "BLOQUEADO" | string | null;
  urlPublica: string | null;
  largura: number | null;
  altura: number | null;
  duracaoMs: number | null;
  mimeType: string | null;
  pendenciaMidia: string | null;
};

export type PoliticaStoryPublicoDto = {
  autorizado: boolean;
  motivoPublico: string;
  pendencia: string | null;
};

export type ListaStoriesPublicosDto = {
  slug: string;
  idadeConfirmada: boolean;
  autorizado: boolean;
  stories: readonly StoryPublicoDto[];
  politica: PoliticaStoryPublicoDto;
};

export type ListaAnunciosPublicaDto = {
  itens: readonly AnuncioCardPublicoDto[];
  paginacao: PaginacaoPublicaDto;
  seo: SeoRotaPublicaDto;
};

export type PublicApiUnavailable = {
  ok: false;
  data: null;
  status: number;
  requestId: string;
  message: string;
  validationErrors?: readonly SolicitarAnuncioValidationErrorDto[];
};

export type PublicApiOk<T> = {
  ok: true;
  data: T;
  status: number;
  requestId: string;
  source: "api-local";
};

export type PublicApiResponse<T> = PublicApiOk<T> | PublicApiUnavailable;
