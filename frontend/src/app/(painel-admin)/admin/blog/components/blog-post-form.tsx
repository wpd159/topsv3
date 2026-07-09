'use client'

import { useEffect, useMemo, useRef, useState } from "react"
import { useRouter } from "next/navigation"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import type { BlogCategoriaPublic, BlogPostDetail } from "@/lib/blog-api"
import { fetchPublicBlogCategorias } from "@/lib/blog-api"
import { SafeBlogPostBody } from "@/lib/blog/safe-blog-body"
import { corrigirEstruturaTexto } from "@/lib/text/encoding"

const CATEGORIA_OUTRA = "__outra__"

type Props = {
  mode: "create" | "edit"
  initialPost?: BlogPostDetail | null
  postId?: string
}

type FormState = {
  titulo: string
  slug: string
  categoriaId: number | null
  categoriaCustom: string
  autorNome: string
  imagemUrl: string
  ogImageUrl: string
  resumo: string
  conteudo: string
  status: "RASCUNHO" | "PUBLICADO"
  seoTitle: string
  seoDescription: string
  sitemapPriority: string
  changeFrequency: string
}

function apiUrl(path: string) {
  const base = (process.env.NEXT_PUBLIC_API_URL || "").replace(/\/$/, "")
  return `${base}${path.startsWith("/") ? "" : "/"}${path}`
}

function createInitialState(post?: BlogPostDetail | null): FormState {
  const hasId = post?.categoriaId != null && Number.isFinite(post.categoriaId)
  return {
    titulo: post?.titulo || "",
    slug: post?.slug || "",
    categoriaId: hasId ? Number(post!.categoriaId) : null,
    categoriaCustom: hasId ? "" : (post?.categoria || ""),
    autorNome: post?.autorNome || "Equipe Tops do Job",
    imagemUrl: post?.imagemUrl || "",
    ogImageUrl: post?.ogImageUrl || "",
    resumo: post?.resumo || "",
    conteudo: post?.conteudo || "",
    status: (post?.status as "RASCUNHO" | "PUBLICADO") || "RASCUNHO",
    seoTitle: post?.seoTitle || "",
    seoDescription: post?.seoDescription || "",
    sitemapPriority: String(post?.sitemapPriority ?? "0.6"),
    changeFrequency: post?.changeFrequency || "weekly",
  }
}

export function BlogPostForm({ mode, initialPost, postId }: Props) {
  const router = useRouter()
  const [form, setForm] = useState<FormState>(createInitialState(corrigirEstruturaTexto(initialPost)))
  const [saving, setSaving] = useState(false)
  const [uploadingField, setUploadingField] = useState<"imagemUrl" | "ogImageUrl" | null>(null)
  const [loading, setLoading] = useState(mode === "edit" && !initialPost)
  const [blogCategorias, setBlogCategorias] = useState<BlogCategoriaPublic[]>([])
  const imagemInputRef = useRef<HTMLInputElement | null>(null)
  const ogImagemInputRef = useRef<HTMLInputElement | null>(null)

  useEffect(() => {
    let active = true
    fetchPublicBlogCategorias()
      .then((list) => {
        if (active) setBlogCategorias(Array.isArray(list) ? list : [])
      })
      .catch(() => {
        if (active) setBlogCategorias([])
      })
    return () => {
      active = false
    }
  }, [])

  useEffect(() => {
    setForm(createInitialState(initialPost))
  }, [initialPost])

  useEffect(() => {
    if (mode !== "create" || initialPost) return
    setForm((prev) => {
      if (prev.categoriaId != null || blogCategorias.length === 0) return prev
      return { ...prev, categoriaId: blogCategorias[0].id }
    })
  }, [mode, initialPost, blogCategorias])

  useEffect(() => {
    if (mode !== "edit" || initialPost || !postId) {
      setLoading(false)
      return
    }

    async function loadPost() {
      try {
        const res = await fetch(apiUrl(`/admin/blog-posts/${postId}`), {
          credentials: "include",
          cache: "no-store",
        })

        if (!res.ok) {
          throw new Error("Não foi possível carregar o post.")
        }

        const data = corrigirEstruturaTexto((await res.json()) as BlogPostDetail)
        setForm(createInitialState(data))
      } catch (error: any) {
        toast.error(error?.message || "Falha ao carregar o post.")
        router.push("/admin/blog")
      } finally {
        setLoading(false)
      }
    }

    loadPost()
  }, [initialPost, mode, postId, router])

  const previewUrl = useMemo(() => {
    const slug = (form.slug || form.titulo || "novo-post")
      .toLowerCase()
      .normalize("NFD")
      .replace(/\p{Diacritic}/gu, "")
      .replace(/[^a-z0-9\s-]/g, "")
      .trim()
      .replace(/\s+/g, "-")
    return `/blog/${slug || "novo-post"}`
  }, [form.slug, form.titulo])

  const updateField = (field: keyof FormState, value: string | number | null) => {
    setForm((prev) => ({ ...prev, [field]: value }) as FormState)
  }

  const handleImageSelection = async (
    event: React.ChangeEvent<HTMLInputElement>,
    field: "imagemUrl" | "ogImageUrl"
  ) => {
    const file = event.target.files?.[0]
    if (!file) return

    if (!file.type.startsWith("image/")) {
      toast.error("Selecione um arquivo de imagem válido.")
      event.target.value = ""
      return
    }

    try {
      setUploadingField(field)
      const formData = new FormData()
      formData.append("imagem", file)

      const res = await fetch(apiUrl("/admin/blog-posts/upload-image"), {
        method: "POST",
        credentials: "include",
        body: formData,
      })

      if (!res.ok) {
        const message = await res.text()
        throw new Error(message || "Não foi possível enviar a imagem.")
      }

      const data = (await res.json()) as { url?: string }
      if (!data?.url) {
        throw new Error("A API não retornou a URL da imagem.")
      }

      updateField(field, data.url)
      toast.success(field === "imagemUrl" ? "Imagem destacada enviada." : "Imagem OG enviada.")
    } catch (error: any) {
      toast.error(error?.message || "Não foi possível enviar a imagem.")
    } finally {
      setUploadingField(null)
      event.target.value = ""
    }
  }

  const handlePreview = () => {
    const draftSlug = (form.slug || form.titulo || "")
      .toLowerCase()
      .normalize("NFD")
      .replace(/\p{Diacritic}/gu, "")
      .replace(/[^a-z0-9\s-]/g, "")
      .trim()
      .replace(/\s+/g, "-")

    const effectiveSlug = mode === "edit" && initialPost?.slug
      ? initialPost.slug
      : draftSlug

    if (!effectiveSlug) {
      toast.warning("Preencha o título antes de visualizar o post.")
      return
    }

    if (mode === "create" && !initialPost?.id && !postId) {
      toast.warning("Salve o post antes de visualizar no blog.")
      return
    }

    if (form.status !== "PUBLICADO") {
      toast.warning("Publique o post antes de visualizar no blog.")
      return
    }

    window.open(`/blog/${effectiveSlug}`, "_blank", "noopener,noreferrer")
  }

  const submit = async () => {
    const hasCat =
      form.categoriaId != null ||
      (typeof form.categoriaCustom === "string" && form.categoriaCustom.trim().length > 0)
    if (!form.titulo.trim() || !hasCat || !form.autorNome.trim() || !form.conteudo.trim()) {
      toast.warning("Preencha título, categoria, autor e conteúdo.")
      return
    }

    setSaving(true)
    try {
      const payload: Record<string, unknown> = {
        titulo: form.titulo,
        slug: form.slug,
        resumo: form.resumo,
        conteudo: form.conteudo,
        imagemUrl: form.imagemUrl,
        autorNome: form.autorNome,
        status: form.status,
        seoTitle: form.seoTitle,
        seoDescription: form.seoDescription,
        ogImageUrl: form.ogImageUrl,
        sitemapPriority: Number(form.sitemapPriority || "0.6"),
        changeFrequency: form.changeFrequency,
      }
      if (form.categoriaId != null) {
        payload.categoriaId = form.categoriaId
      } else {
        payload.categoria = form.categoriaCustom.trim()
      }

      const res = await fetch(
        mode === "create"
          ? apiUrl("/admin/blog-posts")
          : apiUrl(`/admin/blog-posts/${initialPost?.id || postId}`),
        {
          method: mode === "create" ? "POST" : "PUT",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload),
        }
      )

      if (!res.ok) {
        const errBody = (await res.text()).trim()
        throw new Error(errBody || "Não foi possível salvar o post.")
      }

      const saved = (await res.json()) as BlogPostDetail
      toast.success(mode === "create" ? "Post criado com sucesso." : "Post atualizado com sucesso.")
      router.push(`/admin/blog/${saved.id}/editar`)
      router.refresh()
    } catch (error: any) {
      toast.error(error?.message || "Falha ao salvar o post.")
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="flex min-h-screen flex-col gap-6 bg-gray-50 p-6 md:flex-row">
      {loading ? (
        <div className="w-full rounded-xl border border-gray-200 bg-white p-10 text-center text-gray-500 shadow-sm">
          Carregando post...
        </div>
      ) : (
        <>
      <div className="flex-1 space-y-4">
        <Input
          value={form.titulo}
          onChange={(event) => updateField("titulo", event.target.value)}
          placeholder="Digite o título do post..."
          className="py-5 text-2xl font-semibold"
        />

        <p className="text-sm text-gray-500">
          Link permanente: <span className="text-blue-600">{previewUrl}</span>
        </p>

        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <div className="space-y-2">
            <Label htmlFor="categoria">Categoria</Label>
            <p className="text-xs text-gray-500">
              <a href="/admin/blog/categorias" className="text-[#FC1EAD] underline" target="_blank" rel="noreferrer">
                Gerenciar categorias
              </a>
            </p>
            <select
              id="categoria"
              value={form.categoriaId != null ? String(form.categoriaId) : CATEGORIA_OUTRA}
              onChange={(event) => {
                const v = event.target.value
                if (v === CATEGORIA_OUTRA) {
                  updateField("categoriaId", null)
                } else {
                  updateField("categoriaId", Number(v))
                  updateField("categoriaCustom", "")
                }
              }}
              className="h-10 w-full rounded-md border border-gray-300 px-3 text-sm text-gray-700"
            >
              {form.categoriaId != null &&
                !blogCategorias.some((c) => c.id === form.categoriaId) && (
                  <option value={String(form.categoriaId)}>
                    {initialPost?.categoria || `Categoria (id ${form.categoriaId})`}
                  </option>
                )}
              {blogCategorias.map((opt) => (
                <option key={opt.id} value={String(opt.id)}>
                  {opt.nome}
                </option>
              ))}
              <option value={CATEGORIA_OUTRA}>Outra (texto livre / legado)</option>
            </select>
            {form.categoriaId == null && (
              <Input
                id="categoria-custom"
                value={form.categoriaCustom}
                onChange={(event) => updateField("categoriaCustom", event.target.value)}
                placeholder="Nome exibido no blog (sem vínculo com categoria salva)"
                className="mt-2"
              />
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="autor">Autor</Label>
            <Input id="autor" value={form.autorNome} onChange={(event) => updateField("autorNome", event.target.value)} />
          </div>
        </div>

        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <div className="space-y-2">
            <Label htmlFor="slug">Slug</Label>
            <Input id="slug" value={form.slug} onChange={(event) => updateField("slug", event.target.value)} />
          </div>

          <div className="space-y-2">
            <Label htmlFor="status">Status</Label>
            <select
              id="status"
              value={form.status}
              onChange={(event) => updateField("status", event.target.value)}
              className="h-10 w-full rounded-md border border-gray-300 px-3 text-sm text-gray-700"
            >
              <option value="RASCUNHO">Rascunho</option>
              <option value="PUBLICADO">Publicado</option>
            </select>
          </div>
        </div>

        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <div className="space-y-2">
            <Label htmlFor="imagemUrl">Imagem destacada</Label>
            <Input id="imagemUrl" value={form.imagemUrl} onChange={(event) => updateField("imagemUrl", event.target.value)} />
            <div className="flex flex-wrap items-center gap-2">
              <input
                ref={imagemInputRef}
                type="file"
                accept="image/*"
                className="hidden"
                onChange={(event) => handleImageSelection(event, "imagemUrl")}
              />
              <Button
                type="button"
                variant="outline"
                className="border-gray-300 text-gray-600"
                onClick={() => imagemInputRef.current?.click()}
                disabled={uploadingField === "imagemUrl"}
              >
                {uploadingField === "imagemUrl" ? "Enviando imagem..." : "Selecionar imagem"}
              </Button>
              {form.imagemUrl ? (
                <Button
                  type="button"
                  variant="outline"
                  className="border-gray-300 text-gray-600"
                  onClick={() => updateField("imagemUrl", "")}
                >
                  Remover imagem
                </Button>
              ) : null}
            </div>
            {form.imagemUrl ? (
              <div className="mt-2 overflow-hidden rounded-xl border border-gray-200 bg-white">
                <img src={form.imagemUrl} alt="Prévia da imagem destacada" className="h-40 w-full object-cover" />
              </div>
            ) : null}
          </div>

          <div className="space-y-2">
            <Label htmlFor="ogImageUrl">Imagem OG</Label>
            <Input id="ogImageUrl" value={form.ogImageUrl} onChange={(event) => updateField("ogImageUrl", event.target.value)} />
            <div className="flex flex-wrap items-center gap-2">
              <input
                ref={ogImagemInputRef}
                type="file"
                accept="image/*"
                className="hidden"
                onChange={(event) => handleImageSelection(event, "ogImageUrl")}
              />
              <Button
                type="button"
                variant="outline"
                className="border-gray-300 text-gray-600"
                onClick={() => ogImagemInputRef.current?.click()}
                disabled={uploadingField === "ogImageUrl"}
              >
                {uploadingField === "ogImageUrl" ? "Enviando imagem OG..." : "Selecionar imagem OG"}
              </Button>
              {form.ogImageUrl ? (
                <Button
                  type="button"
                  variant="outline"
                  className="border-gray-300 text-gray-600"
                  onClick={() => updateField("ogImageUrl", "")}
                >
                  Remover imagem OG
                </Button>
              ) : null}
            </div>
            {form.ogImageUrl ? (
              <div className="mt-2 overflow-hidden rounded-xl border border-gray-200 bg-white">
                <img src={form.ogImageUrl} alt="Prévia da imagem OG" className="h-40 w-full object-cover" />
              </div>
            ) : null}
          </div>
        </div>

        <div className="space-y-2">
          <Label htmlFor="resumo">Resumo</Label>
          <Textarea id="resumo" rows={4} value={form.resumo} onChange={(event) => updateField("resumo", event.target.value)} />
        </div>

        <div className="space-y-2">
          <Label htmlFor="conteudo">Conteúdo</Label>
          <Textarea
            id="conteudo"
            rows={20}
            value={form.conteudo}
            onChange={(event) => updateField("conteudo", event.target.value)}
            className="resize-none bg-white"
          />
          <div className="rounded-lg border border-gray-200 bg-white p-4">
            <p className="mb-2 text-xs font-medium text-gray-500">Pré-visualização do conteúdo</p>
            <SafeBlogPostBody
              conteudo={form.conteudo}
              className="prose prose-gray max-w-none"
              paragraphClassName="mb-3 text-sm leading-7 text-gray-700 last:mb-0"
            />
          </div>
        </div>
      </div>

      <div className="w-full space-y-6 md:w-80">
        <Card className="border border-gray-200 p-4 shadow-sm">
          <h2 className="mb-3 text-lg font-semibold">PUBLICAÇÃO</h2>
          <div className="mb-4 space-y-2 text-sm text-gray-700">
            <p><strong>Status:</strong> {form.status === "PUBLICADO" ? "Publicado" : "Rascunho"}</p>
            <p><strong>Visibilidade:</strong> Público</p>
            <p><strong>SEO:</strong> configurável</p>
          </div>

          <div className="flex flex-col gap-2">
            <Button variant="outline" className="border-gray-300 text-gray-600" onClick={() => updateField("status", "RASCUNHO")}>
              Salvar como rascunho
            </Button>
            <Button variant="outline" className="border-gray-300 text-gray-600" onClick={handlePreview}>
              Visualizar
            </Button>
            <Button className="bg-[var(--brand-pink-contrast)] text-white hover:bg-[#8f005f]" onClick={submit} disabled={saving}>
              {saving ? "Salvando..." : mode === "create" ? "Criar post" : "Salvar alterações"}
            </Button>
          </div>
        </Card>

        <Card className="border border-gray-200 p-4 shadow-sm">
          <h2 className="mb-3 text-lg font-semibold">SEO</h2>
          <div className="space-y-3">
            <div className="space-y-2">
              <Label htmlFor="seoTitle">SEO title</Label>
              <Input id="seoTitle" value={form.seoTitle} onChange={(event) => updateField("seoTitle", event.target.value)} />
            </div>

            <div className="space-y-2">
              <Label htmlFor="seoDescription">SEO description</Label>
              <Textarea id="seoDescription" rows={4} value={form.seoDescription} onChange={(event) => updateField("seoDescription", event.target.value)} />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-2">
                <Label htmlFor="priority">Sitemap priority</Label>
                <Input id="priority" value={form.sitemapPriority} onChange={(event) => updateField("sitemapPriority", event.target.value)} />
              </div>

              <div className="space-y-2">
                <Label htmlFor="changeFrequency">Change frequency</Label>
                <select
                  id="changeFrequency"
                  value={form.changeFrequency}
                  onChange={(event) => updateField("changeFrequency", event.target.value)}
                  className="h-10 w-full rounded-md border border-gray-300 px-3 text-sm text-gray-700"
                >
                  <option value="daily">daily</option>
                  <option value="weekly">weekly</option>
                  <option value="monthly">monthly</option>
                </select>
              </div>
            </div>
          </div>
        </Card>
      </div>
        </>
      )}
    </div>
  )
}
