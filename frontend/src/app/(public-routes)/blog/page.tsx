import type { Metadata } from "next"
import { BlogContent } from "@/components/blog/blog-content"
import { buildPublicUrl } from "@/lib/seo/public-url"

export const metadata: Metadata = {
  title: "Blog | Tops do Job",
  description: "Conteúdo institucional, guias e novidades do Tops do Job.",
  alternates: {
    canonical: buildPublicUrl("/blog"),
  },
  openGraph: {
    title: "Blog | Tops do Job",
    description: "Conteúdo institucional, guias e novidades do Tops do Job.",
    url: buildPublicUrl("/blog"),
    type: "website",
    siteName: "Tops do Job",
    locale: "pt_BR",
  },
}

export default function BlogHomePage() {
  return (
    <main className="min-h-screen bg-white">
      <BlogContent />
    </main>
  )
}
