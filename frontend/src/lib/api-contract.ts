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
  readonly requestId: string | null
  readonly code: string | null

  constructor(
    message: string,
    kind: ApiFailureKind,
    status: number | null,
    retryable = false,
    requestId: string | null = null,
    code: string | null = null,
  ) {
    super(message)
    this.name = 'ApiContractError'
    this.kind = kind
    this.status = status
    this.retryable = retryable
    this.requestId = requestId
    this.code = code
  }
}

export const UNSUPPORTED_PHOTO_UPLOAD_MESSAGE =
  'Não foi possível ler a foto. Envie um arquivo JPG, PNG ou WebP verdadeiro. Apenas mudar a extensão não resolve.'

const GENERIC_UNSUPPORTED_PHOTO_MESSAGES = new Set([
  'formato de arquivo nao permitido',
  'unsupported media type',
])

function normalizeUnsupportedPhotoMessage(message: string) {
  return message
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[.!?:;]+$/g, '')
    .replace(/\s+/g, ' ')
    .trim()
}

export function resolveUnsupportedPhotoUploadMessage(message: unknown) {
  const candidate = typeof message === 'string' && message.trim()
    ? message.trim()
    : null
  if (!candidate) return UNSUPPORTED_PHOTO_UPLOAD_MESSAGE
  return GENERIC_UNSUPPORTED_PHOTO_MESSAGES.has(normalizeUnsupportedPhotoMessage(candidate))
    ? UNSUPPORTED_PHOTO_UPLOAD_MESSAGE
    : candidate
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

export async function apiErrorFromResponse(
  response: Response,
  options: {
    preserveServerMessage?: boolean
    unsupportedPhotoUpload?: boolean
  } = {},
): Promise<ApiContractError> {
  let serverMessage: string | null = null
  let bodyRequestId: string | null = null
  let serverCode: string | null = null
  if (options.preserveServerMessage) {
    try {
      const body = await response.clone().json() as { message?: unknown; code?: unknown; requestId?: unknown }
      serverMessage = typeof body.message === 'string' && body.message.trim()
        ? body.message.trim()
        : null
      serverCode = typeof body.code === 'string' && body.code.trim()
        ? body.code.trim()
        : null
      bodyRequestId = typeof body.requestId === 'string' && body.requestId.trim()
        ? body.requestId.trim()
        : null
    } catch {
      // O fallback por status permanece autoritativo quando o corpo nao segue o contrato.
    }
  }
  const requestId = bodyRequestId || response.headers.get('X-Request-Id')
  const message = (fallback: string) => serverMessage || fallback
  switch (response.status) {
    case 400:
    case 422:
      return new ApiContractError(message('Revise os dados informados e tente novamente.'), 'INVALID_REQUEST', response.status, false, requestId, serverCode)
    case 401:
      return new ApiContractError(message('Sua sessao expirou. Entre novamente.'), 'SESSION_REQUIRED', 401, false, requestId, serverCode)
    case 403:
      return new ApiContractError(message('Voce nao tem permissao para acessar esta funcao.'), 'ACCESS_DENIED', 403, false, requestId, serverCode)
    case 404:
      return new ApiContractError(
        message('A integracao necessaria para esta funcao ainda nao esta disponivel.'),
        'INTEGRATION_MISSING',
        404,
        false,
        requestId,
        serverCode,
      )
    case 409:
      return new ApiContractError(message('A operacao entrou em conflito com o estado atual.'), 'CONFLICT', 409, false, requestId, serverCode)
    case 415:
      return new ApiContractError(
        options.unsupportedPhotoUpload
          ? resolveUnsupportedPhotoUploadMessage(serverMessage)
          : message('Não foi possível processar o arquivo enviado.'),
        'INVALID_REQUEST',
        415,
        false,
        requestId,
        serverCode,
      )
    default:
      return new ApiContractError(
        message('Nao foi possivel carregar os dados. Tente novamente.'),
        'TECHNICAL_FAILURE',
        response.status,
        response.status >= 500,
        requestId,
        serverCode,
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
  adminUsers: 'Gestao administrativa de usuarios',
  adminAnalytics: 'Rankings e series detalhadas do dashboard',
  systemLogs: 'Registros e trilhas operacionais',
  wizardProfile: 'Descricao de perfil no wizard de anuncio',
  moderationLegacyActions: 'Acoes legadas de edicao na moderacao',
  premiumLegacyDashboard: 'Dashboard Premium legado',
  complianceAdmin: 'Auditoria administrativa de compliance',
  publicProfiles: 'Listagem publica por anunciante',
  financialAnalytics: 'Indicadores e series financeiras',
} as const
