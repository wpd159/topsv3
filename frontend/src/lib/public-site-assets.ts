/**
 * URL canónica da logo em produção (apex + nome exacto do ficheiro público).
 * Não depender só de NEXT_PUBLIC_SITE_URL — se essa env estiver errada (staging, typo),
 * a logo deixava de carregar em todo o site.
 *
 * Override opcional: NEXT_PUBLIC_LOGO_URL (URL absoluta completa).
 */
const CANONICAL_LOGO_ABSOLUTE_URL = "https://topsdojob.com/logo-finallllll.webp"

/** Origem pública do site (links, SEO). */
export function getPublicSiteOrigin(): string {
  return (process.env.NEXT_PUBLIC_SITE_URL || "https://topsdojob.com").replace(/\/$/, "")
}

/** Logo: sempre HTTPS absoluto; por defeito igual à URL validada em produção. */
export function getPublicLogoUrl(): string {
  const override = process.env.NEXT_PUBLIC_LOGO_URL?.trim()
  if (override) {
    return override.replace(/\/$/, "")
  }
  return CANONICAL_LOGO_ABSOLUTE_URL
}
