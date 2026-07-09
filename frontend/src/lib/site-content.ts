import { corrigirEstruturaTexto } from "@/lib/text/encoding"

export type SiteContentKey =
  | "quem-somos"
  | "footer-resumo-institucional"
  | "termos-de-uso"
  | "politica-privacidade"
  | "politica-cookies"
  | "consentimento-promocional"
  | "verificacao"
  | "popup-login"
  | "texto-whatsapp"

export type SiteContentEntry = {
  id?: number | null
  contentKey: string
  titulo: string
  corpo: string
  contentVersion?: number | null
  contentHash?: string | null
  updatedBy?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

const FALLBACKS: Record<SiteContentKey, SiteContentEntry> = {
  "quem-somos": {
    contentKey: "quem-somos",
    titulo: "Quem Somos",
    corpo:
      "Bem-vindo ao Tops do Job.\n\nSomos uma plataforma voltada para classificados com foco em privacidade, segurança operacional e boa experiência de navegação.",
    contentVersion: 1,
    contentHash: null,
  },
  "footer-resumo-institucional": {
    contentKey: "footer-resumo-institucional",
    titulo: "Resumo institucional do footer",
    corpo:
      "O Tops do Job conecta acompanhantes e clientes com discrição, visibilidade e foco em uma experiência mais segura de navegação.",
    contentVersion: 1,
    contentHash: null,
  },
  "termos-de-uso": {
    contentKey: "termos-de-uso",
    titulo: "Termos de Uso",
    corpo: "Atualize os termos de uso no painel administrativo.",
    contentVersion: 1,
    contentHash: null,
  },
  "politica-privacidade": {
    contentKey: "politica-privacidade",
    titulo: "Política de Privacidade",
    corpo: "Atualize a política de privacidade no painel administrativo.",
    contentVersion: 1,
    contentHash: null,
  },
  "politica-cookies": {
    contentKey: "politica-cookies",
    titulo: "Política de Cookies",
    corpo: "Atualize a política de cookies no painel administrativo.",
    contentVersion: 1,
    contentHash: null,
  },
  "consentimento-promocional": {
    contentKey: "consentimento-promocional",
    titulo: "Consentimento Promocional",
    corpo:
      "Autorizo o envio de comunicados promocionais e novidades da plataforma. Esse consentimento é opcional e pode ser revogado futuramente.",
    contentVersion: 1,
    contentHash: null,
  },
  verificacao: {
    contentKey: "verificacao",
    titulo: "Verificação",
    corpo:
      "Explique aqui como funciona a verificação, a proteção de conteúdo e os avisos legais aplicáveis.",
    contentVersion: 1,
    contentHash: null,
  },
  "popup-login": {
    contentKey: "popup-login",
    titulo: "Aviso Importante",
    corpo:
      "Leia os avisos da plataforma e confirme seus dados antes de continuar.",
    contentVersion: 1,
    contentHash: null,
  },
  "texto-whatsapp": {
    contentKey: "texto-whatsapp",
    titulo: "Aviso WhatsApp",
    corpo:
      "A Tops do Job não intermedeia encontros nem pagamentos antecipados. Confirme dados e negocie com cautela.",
    contentVersion: 1,
    contentHash: null,
  },
}

function apiUrl(path: string) {
  const base = (process.env.NEXT_PUBLIC_API_URL || "").replace(/\/$/, "")
  return `${base}${path.startsWith("/") ? "" : "/"}${path}`
}

export async function fetchPublicSiteContent(
  key: SiteContentKey
): Promise<SiteContentEntry> {
  try {
    const res = await fetch(apiUrl(`/site-content/public/${key}`), {
      cache: "no-store",
      credentials: "include",
    })

    if (!res.ok) {
      return FALLBACKS[key]
    }

    const data = corrigirEstruturaTexto((await res.json()) as SiteContentEntry)
    return {
      ...FALLBACKS[key],
      ...data,
      contentKey: data?.contentKey || key,
      contentVersion: data?.contentVersion ?? FALLBACKS[key].contentVersion,
      contentHash: data?.contentHash ?? FALLBACKS[key].contentHash,
    }
  } catch {
    return FALLBACKS[key]
  }
}

export async function fetchAllPublicSiteContent(): Promise<SiteContentEntry[]> {
  try {
    const res = await fetch(apiUrl("/site-content/public"), {
      cache: "no-store",
      credentials: "include",
    })
    if (!res.ok) return Object.values(FALLBACKS)
    const data = await res.json()
    return corrigirEstruturaTexto(
      Array.isArray(data) && data.length ? data : Object.values(FALLBACKS)
    )
  } catch {
    return Object.values(FALLBACKS)
  }
}

export function getFallbackSiteContent(key: SiteContentKey) {
  return FALLBACKS[key]
}

export const SITE_CONTENT_KEYS: SiteContentKey[] = [
  "quem-somos",
  "footer-resumo-institucional",
  "termos-de-uso",
  "politica-privacidade",
  "politica-cookies",
  "consentimento-promocional",
  "verificacao",
  "popup-login",
  "texto-whatsapp",
]
