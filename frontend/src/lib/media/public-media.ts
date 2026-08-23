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
