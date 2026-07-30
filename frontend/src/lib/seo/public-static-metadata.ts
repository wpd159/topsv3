import type { Metadata } from "next"
import { buildPublicUrl } from "@/lib/seo/public-url"

export function buildPublicStaticMetadata({
  path,
  title,
  description,
}: {
  path: string
  title: string
  description: string
}): Metadata {
  const canonical = buildPublicUrl(path)
  return {
    title,
    description,
    alternates: { canonical },
    openGraph: {
      title,
      description,
      url: canonical,
      type: "website",
      siteName: "Tops do Job",
      locale: "pt_BR",
    },
  }
}
