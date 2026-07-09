/** Slug derivado do rótulo (espelha o backend). Útil para URLs legadas sem FK. */
export function slugifyCategoria(categoria: string | null | undefined): string {
  if (!categoria?.trim()) return ""
  let s = categoria
    .trim()
    .toLowerCase()
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
  s = s.replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "")
  return s
}
