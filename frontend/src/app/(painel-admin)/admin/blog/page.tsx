'use client'

import { useEffect, useState } from "react"
import { useRouter } from "next/navigation"
import {
  CalendarDaysIcon,
  EyeIcon,
  PencilSquareIcon,
  PlusIcon,
  TrashIcon,
} from "@heroicons/react/24/solid"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import type { BlogPostSummary } from "@/lib/blog-api"
import { getPublicLogoUrl } from "@/lib/public-site-assets"

function apiUrl(path: string) {
  const base = (process.env.NEXT_PUBLIC_API_URL || "").replace(/\/$/, "")
  return `${base}${path.startsWith("/") ? "" : "/"}${path}`
}

const FALLBACK_IMAGE = getPublicLogoUrl()

export default function BlogAdminPage() {
  const router = useRouter()
  const [posts, setPosts] = useState<BlogPostSummary[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    async function load() {
      try {
        const res = await fetch(apiUrl("/admin/blog-posts"), {
          credentials: "include",
          cache: "no-store",
        })

        if (!res.ok) {
          throw new Error("Não foi possível carregar os posts.")
        }

        setPosts(await res.json())
      } catch (error: any) {
        toast.error(error?.message || "Falha ao carregar blog.")
      } finally {
        setLoading(false)
      }
    }

    load()
  }, [])

  const handleExcluir = async (id: number) => {
    if (!window.confirm("Tem certeza que deseja excluir este post?")) return

    try {
      const res = await fetch(apiUrl(`/admin/blog-posts/${id}`), {
        method: "DELETE",
        credentials: "include",
      })

      if (!res.ok) {
        throw new Error("Não foi possível excluir o post.")
      }

      setPosts((prev) => prev.filter((post) => post.id !== id))
      toast.success("Post removido com sucesso.")
    } catch (error: any) {
      toast.error(error?.message || "Erro ao excluir post.")
    }
  }

  return (
    <div>
      <div className="mb-10 flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-extrabold text-gray-900">Blog / Postagens</h1>
          <p className="text-sm text-gray-500">
            Gerencie as publicações do blog, incluindo status, SEO e páginas públicas.
          </p>
        </div>

        <div className="flex flex-wrap gap-3">
          <Button
            variant="outline"
            onClick={() => router.push("/admin/blog/categorias")}
            className="border-gray-300 text-gray-700 hover:bg-gray-50"
          >
            Categorias do blog
          </Button>
          <Button
            variant="outline"
            onClick={() => router.push("/admin/blog/programatico")}
            className="border-pink-300 text-[#FC1EAD] hover:bg-pink-50"
          >
            Páginas programáticas (SEO)
          </Button>
          <Button
            onClick={() => router.push("/admin/blog/novo")}
            className="flex items-center gap-2 bg-[#FC1EAD] px-6 text-white hover:bg-[#e01a9a]"
          >
            <PlusIcon className="h-5 w-5" />
            Novo Post
          </Button>
        </div>
      </div>

      {loading ? (
        <div className="py-16 text-center text-gray-500">Carregando posts...</div>
      ) : (
        <div className="grid grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-3">
          {posts.map((post) => (
            <Card
              key={post.id}
              className="overflow-hidden rounded-xl border border-gray-100 bg-white shadow-sm transition-all duration-200 hover:shadow-md"
            >
              <div className="flex items-center justify-between border-b bg-gradient-to-r from-[#FC1EAD]/10 to-transparent px-4 py-2">
                <span className="text-xs font-medium text-gray-500">{post.categoria}</span>
                <Badge
                  className={
                    post.status === "PUBLICADO"
                      ? "border-green-300 bg-green-100 text-green-700"
                      : "border-yellow-300 bg-yellow-100 text-yellow-700"
                  }
                >
                  {post.status}
                </Badge>
              </div>

              <div className="relative">
                <img
                  src={post.imagemUrl || FALLBACK_IMAGE}
                  alt={post.titulo}
                  className="h-48 w-full object-cover"
                />
              </div>

              <div className="flex flex-col gap-3 p-5">
                <h2 className="line-clamp-2 text-lg font-semibold text-gray-900">{post.titulo}</h2>

                <div className="flex items-center gap-2 text-sm text-gray-500">
                  <CalendarDaysIcon className="h-4 w-4" />
                  {post.publishedAt
                    ? new Date(post.publishedAt).toLocaleDateString("pt-BR")
                    : "Ainda não publicado"}
                </div>

                <p className="text-sm text-gray-600">
                  <strong>Autor:</strong> {post.autorNome}
                </p>

                <div className="mt-3 flex items-center justify-between border-t pt-3">
                  <Button
                    size="sm"
                    variant="outline"
                    className="flex items-center gap-1 border-blue-300 text-blue-600 hover:bg-blue-50"
                    onClick={() => router.push(`/admin/blog/${post.id}/editar`)}
                  >
                    <PencilSquareIcon className="h-4 w-4" />
                    Editar
                  </Button>

                  <Button
                    size="sm"
                    variant="outline"
                    className="flex items-center gap-1 border-gray-300 text-gray-600 hover:bg-gray-50"
                    onClick={() => router.push(`/blog/${post.slug}`)}
                  >
                    <EyeIcon className="h-4 w-4" />
                    Ver
                  </Button>

                  <Button
                    size="sm"
                    variant="outline"
                    className="flex items-center gap-1 border-red-300 text-red-600 hover:bg-red-50"
                    onClick={() => handleExcluir(post.id)}
                  >
                    <TrashIcon className="h-4 w-4" />
                    Excluir
                  </Button>
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}

      {!loading && posts.length === 0 && (
        <div className="py-16 text-center text-gray-500">Nenhum post encontrado.</div>
      )}
    </div>
  )
}
