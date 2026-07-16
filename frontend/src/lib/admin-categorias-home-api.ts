export type AdminCanonicalCategory = {
  codigo: string
  nome: string
}

export type AdminHomeCategory = {
  id: string
  categoriaCodigo: string
  categoriaNome: string
  nome: string
  descricao: string
  imagemUrl: string
  ordem: number
  ativo: boolean
}

export type AdminHomeCategoryInput = {
  categoriaCodigo: string
  nome: string
  descricao: string
  ordem: number
  ativo: boolean
}

function backendRoot() {
  const configured = (process.env.NEXT_PUBLIC_API_URL || '').replace(/\/$/, '')
  return configured.replace(/\/api\/public$/, '')
}

function adminUrl(path: string) {
  return `${backendRoot()}/api/admin/categorias-home${path}`
}

function antiForgeryCookieName() {
  return ['XSRF', 'TOKEN'].join('-')
}

function antiForgeryHeaderName() {
  return ['X', 'XSRF', 'TOKEN'].join('-')
}

function readAntiForgeryValue() {
  if (typeof document === 'undefined') return null
  const name = antiForgeryCookieName()
  const entry = document.cookie
    .split('; ')
    .find((cookie) => cookie.startsWith(`${name}${String.fromCharCode(61)}`))
  return entry ? decodeURIComponent(entry.slice(name.length + 1)) : null
}

async function ensureAntiForgeryValue() {
  let value = readAntiForgeryValue()
  if (value) return value
  await fetch(`${backendRoot()}/api/admin/auth/me`, {
    credentials: 'include',
    cache: 'no-store',
  })
  value = readAntiForgeryValue()
  return value
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method || 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const value = await ensureAntiForgeryValue()
    if (value) headers.set(antiForgeryHeaderName(), value)
  }
  const response = await fetch(adminUrl(path), {
    ...init,
    headers,
    credentials: 'include',
    cache: 'no-store',
  })
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as { message?: string } | null
    throw new Error(body?.message || `Nao foi possivel concluir a operacao (${response.status}).`)
  }
  return (await response.json()) as T
}

export function listarCategoriasHomeAdmin() {
  return request<AdminHomeCategory[]>('')
}

export function listarCategoriasCanonicasAdmin() {
  return request<AdminCanonicalCategory[]>('/categorias-canonicas')
}

export function salvarCategoriaHomeAdmin(
  id: string | null,
  dados: AdminHomeCategoryInput,
  imagem: File | null,
) {
  const form = new FormData()
  form.set('categoriaCodigo', dados.categoriaCodigo)
  form.set('nome', dados.nome)
  form.set('descricao', dados.descricao)
  form.set('ordem', String(dados.ordem))
  form.set('ativo', String(dados.ativo))
  if (imagem) form.set('imagem', imagem)

  return request<AdminHomeCategory>(id ? `/${encodeURIComponent(id)}` : '', {
    method: id ? 'PUT' : 'POST',
    body: form,
  })
}
