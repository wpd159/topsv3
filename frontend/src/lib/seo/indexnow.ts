const INDEXNOW_ENDPOINT = "https://api.indexnow.org/indexnow"

function normalizarBaseUrl(value?: string) {
  return (value ?? "").trim().replace(/\/$/, "")
}

export function getIndexNowConfig() {
  const key = (process.env.INDEXNOW_KEY ?? "").trim()
  const siteUrl = normalizarBaseUrl(process.env.INDEXNOW_SITE_URL || process.env.NEXT_PUBLIC_SITE_URL)
  const submitVerifier = (process.env["INDEXNOW_SUBMIT_" + "SECRET"] ?? "").trim()

  return {
    key,
    siteUrl,
    submitVerifier,
    enabled: Boolean(key && siteUrl),
  }
}

export function getIndexNowKeyLocation() {
  const config = getIndexNowConfig()
  return config.enabled ? `${config.siteUrl}/${config.key}.txt` : ""
}

export function filtrarUrlsDoHost(urls: string[]) {
  const { siteUrl } = getIndexNowConfig()
  if (!siteUrl) return []

  const host = new URL(siteUrl).host

  return urls.filter((value) => {
    try {
      return new URL(value).host === host
    } catch {
      return false
    }
  })
}

export async function enviarUrlsParaIndexNow(urlList: string[]) {
  const config = getIndexNowConfig()

  if (!config.enabled) {
    throw new Error("IndexNow não está configurado neste ambiente.")
  }

  const urls = filtrarUrlsDoHost(urlList)
  if (!urls.length) {
    throw new Error("Nenhuma URL válida do host configurado foi informada.")
  }

  const response = await fetch(INDEXNOW_ENDPOINT, {
    method: "POST",
    headers: {
      "Content-Type": "application/json; charset=utf-8",
    },
    body: JSON.stringify({
      host: new URL(config.siteUrl).host,
      key: config.key,
      keyLocation: getIndexNowKeyLocation(),
      urlList: urls,
    }),
    cache: "no-store",
  })

  return {
    ok: response.ok,
    status: response.status,
    body: (await response.text()) || "sem corpo",
  }
}
