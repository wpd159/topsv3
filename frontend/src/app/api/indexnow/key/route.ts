import { NextResponse } from "next/server"
import { getIndexNowConfig } from "@/lib/seo/indexnow"

export async function GET() {
  const config = getIndexNowConfig()

  if (!config.enabled) {
    console.warn("[IndexNow] /api/indexnow/key requested but environment is not configured.", {
      hasKey: Boolean(config.key),
      hasSiteUrl: Boolean(config.siteUrl),
    })

    return new NextResponse("IndexNow não configurado.", {
      status: 404,
      headers: {
        "Content-Type": "text/plain; charset=utf-8",
      },
    })
  }

  return new NextResponse(config.key, {
    status: 200,
    headers: {
      "Content-Type": "text/plain; charset=utf-8",
      "Cache-Control": "public, max-age=300",
    },
  })
}
