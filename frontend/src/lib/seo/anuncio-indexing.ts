const MIN_DESCRICAO_ANUNCIO_INDEX = 120
const MIN_FOTOS_ANUNCIO_INDEX = 4

const TITULOS_GENERICOS = new Set([
  "acompanhante",
  "acompanhante 1",
  "acompanhante 2",
  "anuncio",
  "anuncio 1",
  "perfil",
  "teste",
  "nova na cidade",
  "novinha chegando na cidade",
])

const SLUGS_GENERICOS = new Set([
  "acompanhante-1",
  "acompanhante-2",
  "sem-acompanhante",
  "nova-na-cidade",
  "novinha-chegando-na-cidade",
  "teste",
])

function texto(value: unknown): string {
  return typeof value === "string" ? value.trim() : ""
}

function normalizar(value: unknown): string {
  return texto(value)
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .replace(/\s+/g, " ")
    .trim()
}

function normalizarSlug(value: unknown): string {
  return normalizar(value).replace(/\s+/g, "-")
}

function listaMidias(value: unknown): string[] {
  if (!Array.isArray(value)) return []
  return value.filter((item): item is string => typeof item === "string" && item.trim().length > 0)
}

function anuncioEstaPublicavel(anuncio: Record<string, unknown>) {
  if (
    anuncio.ativo === false ||
    anuncio.active === false ||
    anuncio.publicado === false ||
    anuncio.published === false
  ) {
    return false
  }

  const status = normalizar(anuncio.status || anuncio.situacao)
  if (!status) return true

  return ![
    "pendente",
    "pending",
    "reprovado",
    "rejeitado",
    "rejected",
    "inativo",
    "inactive",
    "pausado",
    "expirado",
    "draft",
    "rascunho",
  ].includes(status)
}

function tituloEhUtil(anuncio: Record<string, unknown>) {
  const titulo = normalizar(anuncio.titulo || anuncio.nome || anuncio.username)
  const slug = normalizarSlug(anuncio.slug)

  if (titulo.length < 8) return false
  if (TITULOS_GENERICOS.has(titulo)) return false
  if (SLUGS_GENERICOS.has(slug)) return false
  if (/^(acompanhante|anuncio|perfil|teste)\s*\d*$/.test(titulo)) return false

  return true
}

function descricaoEhUtil(anuncio: Record<string, unknown>) {
  const descricao = texto(
    anuncio.descricaoAnuncio || anuncio.descricao || anuncio.bio || anuncio.sobre || anuncio.descricaoPerfil
  )
  return descricao.replace(/\s+/g, " ").trim().length >= MIN_DESCRICAO_ANUNCIO_INDEX
}

function temCidade(anuncio: Record<string, unknown>) {
  return Boolean(texto(anuncio.cidadeNome || anuncio.cidade))
}

function totalFotosVisiveis(anuncio: Record<string, unknown>) {
  const fotos = listaMidias(anuncio.fotos)
  if (fotos.length > 0) return fotos.length

  const fotosUrl = listaMidias(anuncio.fotosUrl)
  if (fotosUrl.length > 0) return fotosUrl.length

  return listaMidias(anuncio.imagens).length
}

export function shouldIndexAnuncio(anuncio: unknown): boolean {
  if (!anuncio || typeof anuncio !== "object") return false

  const item = anuncio as Record<string, unknown>

  return (
    anuncioEstaPublicavel(item) &&
    temCidade(item) &&
    tituloEhUtil(item) &&
    descricaoEhUtil(item) &&
    totalFotosVisiveis(item) >= MIN_FOTOS_ANUNCIO_INDEX
  )
}
