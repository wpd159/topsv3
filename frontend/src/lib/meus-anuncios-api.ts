export type MeuAnuncioLocalizacao = {
  uf: string | null
  cidade: string | null
  cidadeSlug: string | null
  bairro: string | null
  bairroSlug: string | null
}

export type MeuAnuncioCapa = {
  urlPublica: string | null
  restrita: boolean
}

export type MeuAnuncio = {
  id: string
  slug: string
  titulo: string
  status: string
  statusModeracao: string
  localizacao: MeuAnuncioLocalizacao | null
  capa: MeuAnuncioCapa | null
  atualizadoEm: string | null
}

export class MeusAnunciosApiError extends Error {
  constructor(
    message: string,
    readonly status: number
  ) {
    super(message)
    this.name = 'MeusAnunciosApiError'
  }
}

const API = (process.env.NEXT_PUBLIC_API_URL || '/api/public').replace(/\/$/, '')

async function request<T>(path: string): Promise<T> {
  const response = await fetch(`${API}${path}`, {
    method: 'GET',
    credentials: 'include',
    cache: 'no-store',
    headers: { Accept: 'application/json' },
  })

  if (!response.ok) {
    let message = `Não foi possível carregar seus anúncios (HTTP ${response.status}).`
    try {
      const body = (await response.json()) as { message?: unknown }
      if (typeof body.message === 'string' && body.message.trim()) message = body.message
    } catch {
      // A resposta sem JSON preserva o status HTTP real no erro abaixo.
    }
    throw new MeusAnunciosApiError(message, response.status)
  }

  return (await response.json()) as T
}

export function listarMeusAnuncios() {
  return request<MeuAnuncio[]>('/minha-conta/anuncios')
}

export function buscarMeuAnuncio(slug: string) {
  return request<MeuAnuncio>(`/minha-conta/anuncios/${encodeURIComponent(slug)}`)
}
