function normalizarBaseUrl(value?: string) {
  return (value ?? "").trim().replace(/\/$/, "")
}

function slugify(value?: string | null) {
  if (!value) return ""
  return value
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9\s-]/g, "")
    .replace(/\s+/g, "-")
    .replace(/-+/g, "-")
}

type IndexNowAnuncioContext = {
  slug?: string | null
  estadoUf?: string | null
  cidadeNome?: string | null
  bairroNome?: string | null
}

export function montarUrlsIndexNowAnuncio(context: IndexNowAnuncioContext) {
  const siteUrl = normalizarBaseUrl(
    process.env.NEXT_PUBLIC_SITE_URL || (typeof window !== "undefined" ? window.location.origin : "")
  )

  if (!siteUrl) return []

  const urls = new Set<string>([
    `${siteUrl}/anuncios`,
    `${siteUrl}/acompanhantes`,
  ])

  if (context.slug) {
    urls.add(`${siteUrl}/anuncios/${encodeURIComponent(context.slug)}`)
  }

  const uf = (context.estadoUf || "").toLowerCase()
  const cidadeSlug = slugify(context.cidadeNome)
  const bairroSlug = slugify(context.bairroNome)

  if (uf) {
    urls.add(`${siteUrl}/acompanhantes/${uf}`)
  }

  if (uf && cidadeSlug) {
    urls.add(`${siteUrl}/acompanhantes/${uf}/${cidadeSlug}`)
  }

  if (uf && cidadeSlug && bairroSlug) {
    urls.add(`${siteUrl}/acompanhantes/${uf}/${cidadeSlug}/${bairroSlug}`)
  }

  return Array.from(urls)
}

export async function enviarIndexNowNoCliente(urlList: string[]) {
  // IndexNow passou a ser disparado exclusivamente no backend,
  // depois do commit e só para URLs públicas reais.
  void urlList
}
