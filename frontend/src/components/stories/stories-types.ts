export type StoryPreviewState = "AVAILABLE" | "IDADE_NAO_CONFIRMADA" | "UNAVAILABLE"
export type StoryViewerState = "LIBERADO" | "IDADE_NAO_CONFIRMADA" | "INDISPONIVEL" | "ERRO_DADOS"
export type StoryContentMode = "ANUNCIO" | "MIDIA_UPLOAD"

export type StoryItem = {
  storyId: string | number
  anuncioId?: string | number | null
  anuncioSlug?: string | null
  usuarioUsername?: string | null
  displayUsername?: string | null
  idade?: number | null
  profileNavigable?: boolean | null
  previewState?: StoryPreviewState | null
  previewUrl?: string | null
  modoConteudo?: StoryContentMode | null
  tipo: "ANUNCIO" | "IMAGE" | "VIDEO"
  expiraEm?: unknown
}

export type StoryViewerItem = {
  storyId: string | number
  anuncioId?: string | number | null
  anuncioSlug?: string | null
  usuarioUsername?: string | null
  displayUsername?: string | null
  idade?: number | null
  profileNavigable?: boolean | null
  viewerState: StoryViewerState
  midiaUrl?: string | null
  modoConteudo?: StoryContentMode | null
  tipo: "ANUNCIO" | "IMAGE" | "VIDEO"
  expiraEm?: unknown
  blockedReason?: string | null
  cidade?: string | null
  uf?: string | null
  preco?: number | null
  resumo?: string | null
}

export type StoryBundle = {
  usuarioId: string | number
  usuarioUsername?: string | null
  displayUsername?: string | null
  idade?: number | null
  profileNavigable?: boolean | null
  avatarUrl?: string | null
  visto?: boolean
  itens: StoryItem[]
}

export function loginPublicoDoBundle(
  b: Pick<StoryBundle, "usuarioUsername" | "profileNavigable">
): string {
  if (!b.profileNavigable) return ""
  return (b.usuarioUsername ?? "").trim()
}

export function rotuloPublicoDoBundle(
  b: Pick<StoryBundle, "usuarioUsername" | "displayUsername" | "profileNavigable">
): string {
  const login = loginPublicoDoBundle(b)
  if (login) return `@${login}`
  const fallback = (b.displayUsername ?? "").trim()
  return fallback || "Perfil"
}

export function rotuloPublicoComIdade(
  nome: string,
  idade?: number | null,
): string {
  return idade == null ? nome : `${nome}, ${idade} anos`
}

export function parseBackendDate(v: unknown): Date | null {
  if (!v) return null
  if (typeof v === "string") {
    const d = new Date(v)
    return Number.isNaN(d.getTime()) ? null : d
  }
  if (Array.isArray(v) && v.length >= 3) {
    const [Y, M, D, h = 0, m = 0, s = 0] = v.map((x) => Number(x))
    const d = new Date(Y, (M || 1) - 1, D || 1, h, m, s)
    return Number.isNaN(d.getTime()) ? null : d
  }
  return null
}

export function isExpired(item: { expiraEm?: unknown }) {
  const end = parseBackendDate(item.expiraEm)
  if (!end) return false
  return end.getTime() <= Date.now()
}

export function getInitials(name: string) {
  const parts = name.trim().split(/\s+/).slice(0, 2)
  return parts.map((p) => p[0]?.toUpperCase()).join("") || "?"
}
