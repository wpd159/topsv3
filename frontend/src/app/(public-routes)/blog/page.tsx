import type { Metadata } from "next"
import { BlogContent } from "@/components/blog/blog-content"

export const metadata: Metadata = {
  title: "Blog | Tops do Job",
  description: "Conteúdo institucional, guias e novidades do Tops do Job.",
  alternates: {
    canonical: "https://topsdojob.com/blog",
  },
  openGraph: {
    title: "Blog | Tops do Job",
    description: "Conteúdo institucional, guias e novidades do Tops do Job.",
    url: "https://topsdojob.com/blog",
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
