export type VisibilidadeMidia = "LIVRE" | "RESTRITA_18"

export type TipoMidiaPublica = "FOTO" | "VIDEO" | "STORY"

export type MidiaPublica = {
  id: string | number
  tipo: TipoMidiaPublica
  finalidade?: "CAPA" | "GALERIA" | "STORY" | null
  ordem?: number | null
  visibilidadeMidia: VisibilidadeMidia
  autorizada: boolean
  urlPublica?: string | null
  previewUrl?: string | null
  mimeType?: string | null
  largura?: number | null
  altura?: number | null
}

export type OrigemCapaVideoCard =
  | "VIDEO_POSTER_EXISTENTE"
  | "FOTO_PUBLICA_ELEGIVEL"
  | "PREVIEW_PUBLICO_RESTRITO"
  | "PLACEHOLDER_NEUTRO"

export type ClassificacaoCapaVideoCard =
  | "PUBLICA"
  | "RESTRITA_AUTORIZADA"
  | "PREVIEW_RESTRITO"
  | "NEUTRA"

export type CapaVideoCard = {
  url: string | null
  origem: OrigemCapaVideoCard
  classificacao: ClassificacaoCapaVideoCard
  podeExibir: boolean
  altText: string
}

type SelecionarCapaVideoCardParams = {
  video: MidiaPublica
  midiasDoAnuncio: MidiaPublica[]
  autorizacaoValida: boolean
  altText?: string | null
}

const PUBLIC_CARD_MEDIA_HOSTNAMES = new Set([
  "topsdojob.com",
  "www.topsdojob.com",
  "v3.esle.cloud",
])

function urlPublicaSeguraParaCapa(source?: string | null): string | null {
  const value = source?.trim()
  if (!value || value.includes("\0") || value.includes("\\") || value.includes("?") || value.includes("#")) {
    return null
  }

  const relative = value.startsWith("/") && !value.startsWith("//")
  try {
    const url = new URL(value, "https://topsdojob.com")
    const pathSegments = url.pathname.split("/")
    if (
      url.protocol !== "https:"
      || url.username
      || url.password
      || url.port
      || pathSegments.includes(".")
      || pathSegments.includes("..")
      || url.pathname.startsWith("/api/")
      || url.pathname.startsWith("/admin/")
    ) {
      return null
    }

    if (relative) return value
    if (PUBLIC_R2_HOSTNAME.test(url.hostname)) return value
    return PUBLIC_CARD_MEDIA_HOSTNAMES.has(url.hostname.toLowerCase()) ? value : null
  } catch {
    return null
  }
}

function compararFallbackDeCapa(a: MidiaPublica, b: MidiaPublica): number {
  const ordem = (a.ordem ?? Number.MAX_SAFE_INTEGER) - (b.ordem ?? Number.MAX_SAFE_INTEGER)
  if (ordem !== 0) return ordem

  const idA = String(a.id)
  const idB = String(b.id)
  return idA < idB ? -1 : idA > idB ? 1 : 0
}

export function selecionarCapaVideoCard({
  video,
  midiasDoAnuncio,
  autorizacaoValida,
  altText,
}: SelecionarCapaVideoCardParams): CapaVideoCard {
  const textoAlternativo = altText?.trim() || "Vídeo"
  const posterProprio = urlPublicaSeguraParaCapa(video.previewUrl)
  if (posterProprio) {
    return {
      url: posterProprio,
      origem: "VIDEO_POSTER_EXISTENTE",
      classificacao: video.visibilidadeMidia === "RESTRITA_18"
        ? autorizacaoValida ? "RESTRITA_AUTORIZADA" : "PREVIEW_RESTRITO"
        : "PUBLICA",
      podeExibir: true,
      altText: textoAlternativo,
    }
  }

  const fotosOrdenadas = [...midiasDoAnuncio]
    .filter((midia) => midia.tipo === "FOTO")
    .sort(compararFallbackDeCapa)

  for (const foto of fotosOrdenadas) {
    if (foto.visibilidadeMidia !== "LIVRE" || !foto.autorizada) continue
    const url = urlPublicaSeguraParaCapa(foto.urlPublica)
    if (!url) continue
    return {
      url,
      origem: "FOTO_PUBLICA_ELEGIVEL",
      classificacao: "PUBLICA",
      podeExibir: true,
      altText: textoAlternativo,
    }
  }

  for (const foto of fotosOrdenadas) {
    if (foto.visibilidadeMidia !== "RESTRITA_18") continue
    const url = urlPublicaSeguraParaCapa(foto.previewUrl)
    if (!url) continue
    return {
      url,
      origem: "PREVIEW_PUBLICO_RESTRITO",
      classificacao: autorizacaoValida ? "RESTRITA_AUTORIZADA" : "PREVIEW_RESTRITO",
      podeExibir: true,
      altText: textoAlternativo,
    }
  }

  return {
    url: null,
    origem: "PLACEHOLDER_NEUTRO",
    classificacao: "NEUTRA",
    podeExibir: false,
    altText: "Vídeo",
  }
}

function prioridadeTipoGaleria(tipo: TipoMidiaPublica): number {
  if (tipo === "VIDEO") return 0
  if (tipo === "FOTO") return 1
  return 2
}

export function compararMidiasGaleriaPublica(a: MidiaPublica, b: MidiaPublica): number {
  const prioridade = prioridadeTipoGaleria(a.tipo) - prioridadeTipoGaleria(b.tipo)
  if (prioridade !== 0) return prioridade

  const ordem = (a.ordem ?? Number.MAX_SAFE_INTEGER) - (b.ordem ?? Number.MAX_SAFE_INTEGER)
  if (ordem !== 0) return ordem

  const idA = String(a.id)
  const idB = String(b.id)
  return idA < idB ? -1 : idA > idB ? 1 : 0
}

export function ordenarGaleriaPublica(midias?: MidiaPublica[] | null): MidiaPublica[] {
  if (!Array.isArray(midias)) return []
  return [...midias]
    .filter((midia) => midia.tipo === "VIDEO" || midia.tipo === "FOTO")
    .sort(compararMidiasGaleriaPublica)
}

export function selecionarCapaPublicaSegura(midias?: MidiaPublica[] | null): MidiaPublica | null {
  if (!Array.isArray(midias)) return null

  return (
    [...midias]
      .filter((midia) => midia.tipo === "FOTO" && Boolean(fontePublicaSegura(midia)))
      .sort((a, b) => (a.ordem ?? Number.MAX_SAFE_INTEGER) - (b.ordem ?? Number.MAX_SAFE_INTEGER))[0] ?? null
  )
}

export function selecionarGaleriaPublicaSegura(midias?: MidiaPublica[] | null): MidiaPublica[] {
  if (!Array.isArray(midias)) return []
  return ordenarGaleriaPublica(midias).filter((midia) => {
    if (midia.tipo === "VIDEO") {
      return midiaExigeConfirmacaoIdade(midia) || Boolean(fontePublicaSegura(midia))
    }
    return midia.tipo === "FOTO" && Boolean(fontePublicaSegura(midia))
  })
}

export function fontePublicaSegura(midia: MidiaPublica): string | null {
  if (midia.visibilidadeMidia === "LIVRE") {
    return midia.autorizada ? midia.urlPublica ?? null : null
  }
  return midia.autorizada ? midia.urlPublica ?? null : midia.previewUrl ?? null
}

export function midiaExigeConfirmacaoIdade(midia: MidiaPublica): boolean {
  return midia.visibilidadeMidia === "RESTRITA_18" && !midia.autorizada
}

export function mimeTypeVideoDeclaravel(mimeType?: string | null): string | undefined {
  const normalized = mimeType?.trim().toLowerCase()
  if (!normalized || !normalized.startsWith("video/")) return undefined

  // Chrome e Edge rejeitam o source antes de inspecionar um MOV H.264/AAC
  // quando o tipo declarado e video/quicktime. Sem o atributo, o navegador
  // faz a deteccao pelo conteudo e preserva o MIME real da resposta HTTP.
  if (normalized.split(";", 1)[0] === "video/quicktime") return undefined

  return normalized
}

const PUBLIC_R2_HOSTNAME = /^pub-[0-9a-f]{32}\.r2\.dev$/i

export function imagemPublicaR2(source?: string | null): boolean {
  if (!source) return false
  try {
    const url = new URL(source)
    return url.protocol === "https:"
      && PUBLIC_R2_HOSTNAME.test(url.hostname)
      && !url.username
      && !url.password
      && !url.port
      && !url.search
      && !url.hash
  } catch {
    return false
  }
}
