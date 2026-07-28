export type ApiFailureKind =
  | 'SESSION_REQUIRED'
  | 'ACCESS_DENIED'
  | 'INTEGRATION_MISSING'
  | 'CONFLICT'
  | 'INVALID_REQUEST'
  | 'TECHNICAL_FAILURE'
  | 'NETWORK_FAILURE'

export class ApiContractError extends Error {
  readonly status: number | null
  readonly kind: ApiFailureKind
  readonly retryable: boolean

  constructor(message: string, kind: ApiFailureKind, status: number | null, retryable = false) {
    super(message)
    this.name = 'ApiContractError'
    this.kind = kind
    this.status = status
    this.retryable = retryable
  }
}

export class BackendContractPendingError extends ApiContractError {
  readonly module: string

  constructor(module: string) {
    super(
      'Esta funcao permanece disponivel, mas aguarda um contrato backend V3.',
      'INTEGRATION_MISSING',
      404
    )
    this.name = 'BackendContractPendingError'
    this.module = module
  }
}

function configuredPublicApiBase() {
  const configured = (process.env.NEXT_PUBLIC_API_URL || '/api/public').replace(/\/$/, '')
  if (!configured) {
    throw new ApiContractError(
      'A integracao da aplicacao nao esta configurada.',
      'TECHNICAL_FAILURE',
      500
    )
  }
  return configured
}

function normalizedPath(path: string) {
  if (!path.startsWith('/')) {
    throw new Error('O caminho da API deve iniciar com barra.')
  }
  return path
}

export function publicApiUrl(path: string) {
  const normalized = normalizedPath(path)
  if (normalized === '/api/public' || normalized.startsWith('/api/public/')) {
    throw new Error('O caminho publico nao deve repetir o prefixo /api/public.')
  }
  return `${configuredPublicApiBase()}${normalized}`
}

export function backendApiRoot() {
  return configuredPublicApiBase().replace(/\/api\/public$/, '')
}

export function adminApiUrl(path: string) {
  const normalized = normalizedPath(path)
  if (normalized === '/api/admin' || normalized.startsWith('/api/admin/')) {
    throw new Error('O caminho administrativo nao deve repetir o prefixo /api/admin.')
  }
  return `${backendApiRoot()}/api/admin${normalized}`
}

export async function apiErrorFromResponse(response: Response): Promise<ApiContractError> {
  switch (response.status) {
    case 400:
    case 422:
      return new ApiContractError('Revise os dados informados e tente novamente.', 'INVALID_REQUEST', response.status)
    case 401:
      return new ApiContractError('Sua sessao expirou. Entre novamente.', 'SESSION_REQUIRED', 401)
    case 403:
      return new ApiContractError('Voce nao tem permissao para acessar esta funcao.', 'ACCESS_DENIED', 403)
    case 404:
      return new ApiContractError(
        'A integracao necessaria para esta funcao ainda nao esta disponivel.',
        'INTEGRATION_MISSING',
        404
      )
    case 409:
      return new ApiContractError('A operacao entrou em conflito com o estado atual.', 'CONFLICT', 409)
    default:
      return new ApiContractError(
        'Nao foi possivel carregar os dados. Tente novamente.',
        'TECHNICAL_FAILURE',
        response.status,
        response.status >= 500
      )
  }
}

export function normalizeApiError(error: unknown): ApiContractError {
  if (error instanceof ApiContractError) return error
  return new ApiContractError(
    'Nao foi possivel conectar ao servico. Verifique a conexao e tente novamente.',
    'NETWORK_FAILURE',
    null,
    true
  )
}

export function requireArrayPayload<T>(payload: unknown): T[] {
  if (!Array.isArray(payload)) {
    throw new ApiContractError(
      'O servico retornou uma resposta incompativel.',
      'TECHNICAL_FAILURE',
      502,
      true
    )
  }
  return payload as T[]
}

export const PENDING_BACKEND_CONTRACTS = {
  blog: 'Blog e conteudo editorial',
  suggestions: 'Sugestoes',
  notices: 'Avisos e FAQ',
  adminUsers: 'Gestao administrativa de usuarios',
  adminAnalytics: 'Rankings e series detalhadas do dashboard',
  systemLogs: 'Registros e trilhas operacionais',
  wizardProgress: 'Observabilidade do progresso do wizard',
  wizardProfile: 'Descricao de perfil no wizard de anuncio',
  moderationLegacyActions: 'Acoes legadas de edicao na moderacao',
  premiumLegacyDashboard: 'Dashboard Premium legado',
  complianceAdmin: 'Auditoria administrativa de compliance',
  publicProfiles: 'Listagem publica por anunciante',
  financialAnalytics: 'Indicadores e series financeiras',
  referrals: 'Indicacoes e recompensas por indicacao',
} as const
