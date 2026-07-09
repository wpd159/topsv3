import { NextRequest, NextResponse } from "next/server"
import {
  enviarUrlsParaIndexNow,
  filtrarUrlsDoHost,
  getIndexNowConfig,
  getIndexNowKeyLocation,
} from "@/lib/seo/indexnow"

export async function POST(req: NextRequest) {
  try {
    const config = getIndexNowConfig()

    if (!config.enabled) {
      console.warn("[IndexNow] submit requested without required environment.", {
        hasKey: Boolean(config.key),
        hasSiteUrl: Boolean(config.siteUrl),
      })

      return NextResponse.json(
        { error: "IndexNow não está configurado neste ambiente." },
        { status: 503 }
      )
    }

    if (config.submitVerifier) {
      const siteOrigin = new URL(config.siteUrl).origin
      const requestOrigin = req.headers.get("origin")?.trim()
      const requestReferer = req.headers.get("referer")?.trim()
      const origemInterna =
        requestOrigin === siteOrigin ||
        Boolean(requestReferer && requestReferer.startsWith(siteOrigin))

      const headerVerifier = req.headers.get("x-indexnow-submit-secret")?.trim()
      if (!origemInterna && (!headerVerifier || headerVerifier !== config.submitVerifier)) {
        return NextResponse.json({ error: "Acesso não autorizado." }, { status: 401 })
      }
    }

    const body = await req.json()
    const urlList = filtrarUrlsDoHost(Array.isArray(body?.urlList) ? body.urlList : [])

    if (!urlList.length) {
      return NextResponse.json(
        { error: "urlList é obrigatório e deve conter URLs válidas do próprio site." },
        { status: 400 }
      )
    }

    const response = await enviarUrlsParaIndexNow(urlList)

    console.info("[IndexNow] submit finished.", {
      status: response.status,
      ok: response.ok,
      urlCount: urlList.length,
      keyLocation: getIndexNowKeyLocation(),
    })

    return NextResponse.json(
      {
        ok: response.ok,
        status: response.status,
        keyLocation: getIndexNowKeyLocation(),
        response: response.body,
      },
      { status: response.status }
    )
  } catch (error) {
    console.error("[IndexNow] submit failed.", error)
    return NextResponse.json(
      { error: "Erro ao enviar URLs para o IndexNow." },
      { status: 500 }
    )
  }
}
