"use client"

import { useCallback, useEffect, useState } from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { ArrowPathIcon, EyeIcon, GlobeAltIcon } from "@heroicons/react/24/solid"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"

function apiUrl(path: string) {
  const base = (process.env.NEXT_PUBLIC_API_URL || "").replace(/\/$/, "")
  return `${base}${path.startsWith("/") ? "" : "/"}${path}`
}

type ProgRow = {
  id: number
  tema: string
  estadoUf: string
  cidadeSlug: string
  cidadeNome: string
  title: string
  published: boolean
  indexed: boolean
  contentQualityOk: boolean
  updatedAtIso: string | null
}

const TEMAS = ["acompanhantes", "garotas-de-programa", "anuncios-adultos"] as const

export default function BlogProgramaticoAdminPage() {
  const router = useRouter()
  const [rows, setRows] = useState<ProgRow[]>([])
  const [loading, setLoading] = useState(true)
  const [filtroTema, setFiltroTema] = useState("")
  const [filtroUf, setFiltroUf] = useState("")
  const [filtroPub, setFiltroPub] = useState<boolean | "">("")

  const [batchTema, setBatchTema] = useState("acompanhantes")
  const [batchUf, setBatchUf] = useState("GO")
  const [batchSoAnuncios, setBatchSoAnuncios] = useState(true)
  const [batchReprocess, setBatchReprocess] = useState(false)
  const [batchLoading, setBatchLoading] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const q = new URLSearchParams()
      if (filtroTema) q.set("tema", filtroTema)
      if (filtroUf) q.set("uf", filtroUf.trim().toUpperCase())
      if (filtroPub !== "") q.set("published", String(filtroPub))
      const res = await fetch(apiUrl(`/admin/blog-programmatic?${q.toString()}`), {
        credentials: "include",
        cache: "no-store",
      })
      if (!res.ok) throw new Error("Falha ao listar páginas programáticas.")
      setRows(await res.json())
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : "Erro ao carregar.")
    } finally {
      setLoading(false)
    }
  }, [filtroTema, filtroUf, filtroPub])

  useEffect(() => {
    load()
  }, [load])

  const togglePublish = async (id: number, published: boolean) => {
    try {
      const res = await fetch(apiUrl(`/admin/blog-programmatic/${id}/published`), {
        method: "PATCH",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ published: !published }),
      })
      if (!res.ok) throw new Error("Não foi possível atualizar.")
      toast.success(!published ? "Publicado." : "Despublicado.")
      await load()
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : "Erro.")
    }
  }

  const reprocess = async (id: number) => {
    try {
      const res = await fetch(apiUrl(`/admin/blog-programmatic/${id}/reprocess`), {
        method: "POST",
        credentials: "include",
      })
      if (!res.ok) throw new Error("Reprocessamento falhou.")
      toast.success("Conteúdo reprocessado.")
      await load()
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : "Erro.")
    }
  }

  const runBatch = async () => {
    setBatchLoading(true)
    try {
      const res = await fetch(apiUrl("/admin/blog-programmatic/batch"), {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          tema: batchTema,
          uf: batchUf.trim().toUpperCase(),
          apenasCidadesComAnuncios: batchSoAnuncios,
          reprocessarExistentes: batchReprocess,
        }),
      })
      if (!res.ok) throw new Error("Lote rejeitado pela API.")
      const data = await res.json()
      toast.success(
        `Criadas: ${data.criadas}, atualizadas: ${data.atualizadas}, ignoradas: ${data.ignoradas} (total ${data.totalProcessadas})`
      )
      await load()
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : "Erro no lote.")
    } finally {
      setBatchLoading(false)
    }
  }

  return (
    <div>
      <div className="mb-8 flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div>
          <h1 className="text-3xl font-extrabold text-gray-900">Blog · Páginas programáticas (SEO)</h1>
          <p className="text-sm text-gray-500">
            Landing pages /blog/cidade/[tema]/[cidade] com conteúdo gerado por template. Não altera posts editoriais.
          </p>
        </div>
        <Button variant="outline" onClick={() => router.push("/admin/blog")}>
          Voltar aos posts
        </Button>
      </div>

      <Card className="mb-8 p-6">
        <h2 className="text-lg font-semibold text-gray-900">Gerar em lote (por UF)</h2>
        <p className="mt-1 text-sm text-gray-600">
          Cria rascunhos para todas as cidades do estado. Publique depois pela tabela (ou amplie textos antes).
        </p>
        <div className="mt-4 flex flex-wrap items-end gap-4">
          <div>
            <label className="text-xs font-medium text-gray-600">Tema</label>
            <select
              className="mt-1 block rounded-md border border-gray-300 px-3 py-2 text-sm"
              value={batchTema}
              onChange={(e) => setBatchTema(e.target.value)}
            >
              {TEMAS.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="text-xs font-medium text-gray-600">UF</label>
            <Input
              className="mt-1 w-24 uppercase"
              maxLength={2}
              value={batchUf}
              onChange={(e) => setBatchUf(e.target.value)}
            />
          </div>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={batchSoAnuncios}
              onChange={(e) => setBatchSoAnuncios(e.target.checked)}
            />
            Só cidades com anúncios ativos
          </label>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={batchReprocess}
              onChange={(e) => setBatchReprocess(e.target.checked)}
            />
            Reprocessar existentes
          </label>
          <Button
            className="bg-[#FC1EAD] text-white hover:bg-[#e01a9a]"
            disabled={batchLoading}
            onClick={() => void runBatch()}
          >
            {batchLoading ? "Processando…" : "Executar lote"}
          </Button>
        </div>
      </Card>

      <Card className="mb-6 p-4">
        <div className="flex flex-wrap gap-4">
          <div>
            <label className="text-xs text-gray-600">Tema</label>
            <Input
              placeholder="ex.: acompanhantes"
              value={filtroTema}
              onChange={(e) => setFiltroTema(e.target.value)}
            />
          </div>
          <div>
            <label className="text-xs text-gray-600">UF</label>
            <Input
              placeholder="GO"
              className="w-24 uppercase"
              maxLength={2}
              value={filtroUf}
              onChange={(e) => setFiltroUf(e.target.value)}
            />
          </div>
          <div>
            <label className="text-xs text-gray-600">Publicado</label>
            <select
              className="mt-1 block rounded-md border border-gray-300 px-3 py-2 text-sm"
              value={filtroPub === "" ? "" : filtroPub ? "true" : "false"}
              onChange={(e) => {
                const v = e.target.value
                setFiltroPub(v === "" ? "" : v === "true")
              }}
            >
              <option value="">Todos</option>
              <option value="true">Sim</option>
              <option value="false">Não</option>
            </select>
          </div>
          <Button variant="secondary" className="self-end" onClick={() => void load()}>
            Aplicar filtros
          </Button>
        </div>
      </Card>

      {loading ? (
        <div className="py-16 text-center text-gray-500">Carregando…</div>
      ) : (
        <div className="overflow-x-auto rounded-xl border border-gray-100">
          <table className="min-w-full divide-y divide-gray-200 text-sm">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-4 py-3 text-left font-semibold text-gray-700">Cidade</th>
                <th className="px-4 py-3 text-left font-semibold text-gray-700">Tema</th>
                <th className="px-4 py-3 text-left font-semibold text-gray-700">Título</th>
                <th className="px-4 py-3 text-left font-semibold text-gray-700">Status</th>
                <th className="px-4 py-3 text-right font-semibold text-gray-700">Ações</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 bg-white">
              {rows.map((r) => (
                <tr key={r.id}>
                  <td className="px-4 py-3">
                    {r.cidadeNome}{" "}
                    <span className="text-gray-400">
                      ({r.estadoUf}) · {r.cidadeSlug}
                    </span>
                  </td>
                  <td className="px-4 py-3 font-mono text-xs">{r.tema}</td>
                  <td className="max-w-md truncate px-4 py-3">{r.title}</td>
                  <td className="px-4 py-3">
                    <div className="flex flex-wrap gap-1">
                      {r.published ? (
                        <Badge className="bg-green-100 text-green-800">Publicado</Badge>
                      ) : (
                        <Badge variant="outline">Rascunho</Badge>
                      )}
                      {r.contentQualityOk ? (
                        <Badge className="bg-blue-50 text-blue-800">Qualidade OK</Badge>
                      ) : (
                        <Badge className="bg-amber-50 text-amber-800">Conteúdo curto</Badge>
                      )}
                      {r.indexed ? (
                        <Badge className="bg-emerald-50 text-emerald-800">Index</Badge>
                      ) : (
                        <Badge className="bg-gray-100 text-gray-700">Noindex</Badge>
                      )}
                    </div>
                  </td>
                  <td className="px-4 py-3 text-right">
                    <div className="flex justify-end gap-2">
                      <Button
                        size="sm"
                        variant="outline"
                        title="Ver público (se publicado)"
                        asChild
                      >
                        <Link
                          href={`/blog/cidade/${encodeURIComponent(r.tema)}/${encodeURIComponent(r.cidadeSlug)}?uf=${encodeURIComponent(r.estadoUf)}`}
                          target="_blank"
                        >
                          <EyeIcon className="h-4 w-4" />
                        </Link>
                      </Button>
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => void togglePublish(r.id, r.published)}
                      >
                        <GlobeAltIcon className="mr-1 h-4 w-4" />
                        {r.published ? "Despublicar" : "Publicar"}
                      </Button>
                      <Button size="sm" variant="outline" onClick={() => void reprocess(r.id)}>
                        <ArrowPathIcon className="h-4 w-4" />
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {!loading && rows.length === 0 && (
        <div className="py-12 text-center text-gray-500">Nenhuma página programática ainda. Use o lote acima.</div>
      )}
    </div>
  )
}
