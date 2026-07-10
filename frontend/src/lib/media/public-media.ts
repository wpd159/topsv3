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
      .filter(
        (midia) =>
          midia.tipo === "FOTO" &&
          midia.visibilidadeMidia === "LIVRE" &&
          midia.autorizada &&
          Boolean(midia.urlPublica)
      )
      .sort((a, b) => (a.ordem ?? Number.MAX_SAFE_INTEGER) - (b.ordem ?? Number.MAX_SAFE_INTEGER))[0] ?? null
  )
}

export function selecionarGaleriaPublicaSegura(midias?: MidiaPublica[] | null): MidiaPublica[] {
  if (!Array.isArray(midias)) return []
  return [...midias]
    .filter(
      (midia) =>
        midia.tipo === "FOTO" &&
        midia.visibilidadeMidia === "LIVRE" &&
        midia.autorizada &&
        Boolean(midia.urlPublica)
    )
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
