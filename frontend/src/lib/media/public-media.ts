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
  return [...midias]
    .filter((midia) => midia.tipo === "FOTO" && Boolean(fontePublicaSegura(midia)))
    .sort((a, b) => (a.ordem ?? Number.MAX_SAFE_INTEGER) - (b.ordem ?? Number.MAX_SAFE_INTEGER))
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
