"use client"

import { ChangeEvent, useEffect, useMemo, useState } from "react"
import { useParams, useRouter } from "next/navigation"
import Image from "next/image"
import { toast } from "sonner"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  ArrowLeftIcon,
  CalendarIcon,
  CheckCircleIcon,
  CreditCardIcon,
  DocumentTextIcon,
  EnvelopeIcon,
  EyeIcon,
  PencilSquareIcon,
  PhoneIcon,
  PlusIcon,
  TrashIcon,
  UserIcon,
  XCircleIcon,
} from "@heroicons/react/24/solid"
import { maskCPF, maskPhoneBR, normalizeCPF, normalizePhoneBR } from "@/utils/mask"

type UsuarioEdit = {
  id: number | string
  nomeCompleto?: string
  username?: string
  email?: string
  telefone?: string
  cpf?: string
  dataNascimento?: string
  criadoEm?: string
  status?: "ATIVO" | "INATIVO"
  documentosUrls?: string[]
}

type DocItem = { id: number; url: string }

function cleanUrls(input: unknown): string[] {
  const arr: string[] = Array.isArray(input) ? (input as string[]) : []
  const seen = new Set<string>()
  const out: string[] = []
  for (const value of arr) {
    const url = String(value ?? "").trim()
    if (!url || seen.has(url)) continue
    seen.add(url)
    out.push(url)
  }
  return out
}

function dedupDocsKeepFirst(docs: DocItem[]): DocItem[] {
  const seen = new Set<string>()
  return docs.filter((doc) => {
    const url = (doc.url ?? "").trim()
    if (!url) return true
    if (seen.has(url)) return false
    seen.add(url)
    return true
  })
}

function isPdf(url?: string) {
  return /\.pdf($|\?)/i.test(url || "")
}

export default function EditarUsuarioPage() {
  const { id } = useParams()
  const router = useRouter()

  const [carregando, setCarregando] = useState(true)
  const [salvando, setSalvando] = useState(false)
  const [uploadingDocId, setUploadingDocId] = useState<number | null>(null)
  const [removendoDocId, setRemovendoDocId] = useState<number | null>(null)
  const [usuario, setUsuario] = useState<UsuarioEdit>({
    id: String(id),
    nomeCompleto: "",
    username: "",
    email: "",
    telefone: "",
    cpf: "",
    dataNascimento: "",
    criadoEm: "",
    status: "ATIVO",
    documentosUrls: [],
  })
  const [documentos, setDocumentos] = useState<DocItem[]>([])

  useEffect(() => {
    if (!id) return
    ;(async () => {
      try {
        setCarregando(true)
        const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/usuarios/${id}`, {
          credentials: "include",
        })

        if (!res.ok) throw new Error(`Erro ${res.status}`)

        const data = await res.json()
        const urls = cleanUrls(data.documentosUrls)

        setUsuario({
          id: data.id,
          nomeCompleto: data.nomeCompleto ?? "",
          username: data.username ?? "",
          email: data.email ?? "",
          telefone: maskPhoneBR(data.telefone ?? ""),
          cpf: maskCPF(data.cpf ?? ""),
          dataNascimento: data.dataNascimento ?? "",
          criadoEm: data.criadoEm ?? data.dataCadastro ?? "",
          status: (data.status as "ATIVO" | "INATIVO") ?? "ATIVO",
          documentosUrls: urls,
        })

        setDocumentos(urls.map((url: string, index: number) => ({ id: index + 1, url })))
      } catch {
        toast.error("Falha ao carregar usuário.")
      } finally {
        setCarregando(false)
      }
    })()
  }, [id])

  const handleChange = (field: keyof UsuarioEdit, value: string) => {
    if (field === "cpf") value = maskCPF(value)
    if (field === "telefone") value = maskPhoneBR(value)
    setUsuario((prev) => ({ ...prev, [field]: value }))
  }

  const documentosUrls = useMemo(
    () => documentos.map((doc) => doc.url).filter(Boolean),
    [documentos]
  )

  const adicionarNovoDocumento = () => {
    setDocumentos((prev) => [...prev, { id: Date.now(), url: "" }])
  }

  const removerDocumento = async (docId: number) => {
    const doc = documentos.find((item) => item.id === docId)
    if (!doc) return

    if (!doc.url) {
      setDocumentos((prev) => prev.filter((item) => item.id !== docId))
      return
    }

    if (!window.confirm("Remover este documento definitivamente?")) return

    try {
      setRemovendoDocId(docId)
      const params = new URLSearchParams({ url: doc.url })
      const res = await fetch(
        `${process.env.NEXT_PUBLIC_API_URL}/usuarios/${id}/documentos?${params.toString()}`,
        { method: "DELETE", credentials: "include" }
      )

      if (!res.ok) {
        const message = await res.text()
        throw new Error(message || "Erro ao remover documento")
      }

      setDocumentos((prev) => prev.filter((item) => item.id !== docId))
      setUsuario((prev) => ({
        ...prev,
        documentosUrls: cleanUrls((prev.documentosUrls || []).filter((url) => url !== doc.url)),
      }))

      toast.success("Documento removido com sucesso.")
    } catch (error: any) {
      toast.error(error?.message || "Falha ao remover documento.")
    } finally {
      setRemovendoDocId(null)
    }
  }

  const handleImagemChange = async (event: ChangeEvent<HTMLInputElement>, docId: number) => {
    const file = event.target.files?.[0]
    if (!file) return

    const current = documentos.find((item) => item.id === docId)
    const oldUrl = (current?.url ?? "").trim()
    const hasOldUrl = Boolean(oldUrl)

    const formData = new FormData()
    formData.append("file", file)
    if (hasOldUrl) formData.append("oldUrl", oldUrl)

    try {
      setUploadingDocId(docId)

      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/usuarios/${id}/documentos`, {
        method: hasOldUrl ? "PUT" : "POST",
        credentials: "include",
        body: formData,
      })

      if (!res.ok) {
        const message = await res.text()
        throw new Error(message || "Erro ao enviar documento")
      }

      const data = await res.json()
      const serverUrls = cleanUrls(data.documentosUrls)
      const newUrl = (serverUrls.at(-1) ?? "").trim()

      if (!newUrl) throw new Error("API não retornou a URL do documento.")

      setDocumentos((prev) => {
        let next = prev.map((item) => (item.id === docId ? { ...item, url: newUrl } : item))
        if (hasOldUrl) {
          next = next.filter((item) => item.id === docId || item.url !== oldUrl)
        }
        return dedupDocsKeepFirst(next)
      })

      setUsuario((prev) => {
        const prevUrls = cleanUrls(prev.documentosUrls)
        const nextUrls = hasOldUrl
          ? prevUrls.map((url) => (url === oldUrl ? newUrl : url))
          : [...prevUrls, newUrl]

        return { ...prev, documentosUrls: cleanUrls(nextUrls) }
      })

      toast.success(hasOldUrl ? "Documento atualizado com sucesso." : "Documento enviado com sucesso.")
    } catch (error: any) {
      toast.error(error?.message || "Falha ao enviar documento.")
    } finally {
      setUploadingDocId(null)
      event.target.value = ""
    }
  }

  const handleCancel = () => router.push(`/admin/usuarios/${id}`)

  const handleSave = async () => {
    try {
      setSalvando(true)

      const payload = {
        nomeCompleto: usuario.nomeCompleto?.trim() || null,
        email: usuario.email?.trim() || null,
        username: usuario.username?.trim() || null,
        telefone: normalizePhoneBR(usuario.telefone || "") || null,
        cpf: normalizeCPF(usuario.cpf || "") || null,
        dataNascimento: usuario.dataNascimento || null,
        status: usuario.status,
        documentosUrls,
      }

      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/usuarios/${id}/editar-admin`, {
        method: "PUT",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      })

      if (!res.ok) {
        const message = await res.text()
        throw new Error(message || "Erro ao salvar alterações")
      }

      toast.success("Usuário atualizado com sucesso.")
    } catch (error: any) {
      toast.error(error?.message || "Falha ao salvar usuário.")
    } finally {
      setSalvando(false)
    }
  }

  if (carregando) {
    return <div className="py-10 text-center text-gray-500">Carregando...</div>
  }

  return (
    <section className="pb-8">
      <div className="mb-6 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Button variant="ghost" size="icon" onClick={handleCancel} className="text-gray-600 hover:bg-gray-100">
            <ArrowLeftIcon className="h-5 w-5" />
          </Button>
          <div>
            <h1 className="text-2xl font-bold text-gray-800">Editar usuário</h1>
            <p className="text-sm text-gray-500">Ajuste os dados da conta com uma edição rápida e organizada.</p>
          </div>
        </div>

        <div className="flex gap-2">
          <Button variant="outline" onClick={handleCancel} className="border-gray-300 text-gray-600 hover:bg-gray-100">
            Cancelar
          </Button>
          <Button onClick={handleSave} disabled={salvando} className="bg-[#C41E73] text-white hover:bg-[#a51861]">
            {salvando ? "Salvando..." : "Salvar alterações"}
          </Button>
        </div>
      </div>

      <div className="space-y-4">
        <div className="rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
          <h2 className="mb-4 flex items-center gap-2 text-base font-semibold text-gray-800">
            <UserIcon className="h-5 w-5 text-[#C41E73]" />
            Dados do usuário
          </h2>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <div>
              <Label className="text-sm text-gray-700">Nome completo</Label>
              <Input
                value={usuario.nomeCompleto || ""}
                onChange={(e) => handleChange("nomeCompleto", e.target.value)}
                className="mt-1 h-10"
              />
            </div>

            <div>
              <Label className="text-sm text-gray-700">Nome de usuário</Label>
              <Input
                value={usuario.username || ""}
                onChange={(e) => handleChange("username", e.target.value)}
                className="mt-1 h-10"
              />
            </div>

            <div>
              <Label className="flex items-center gap-1 text-sm text-gray-700">
                <EnvelopeIcon className="h-4 w-4 text-[#C41E73]" /> Email
              </Label>
              <Input
                type="email"
                value={usuario.email || ""}
                onChange={(e) => handleChange("email", e.target.value)}
                className="mt-1 h-10"
              />
            </div>

            <div>
              <Label className="flex items-center gap-1 text-sm text-gray-700">
                <PhoneIcon className="h-4 w-4 text-[#C41E73]" /> Telefone
              </Label>
              <Input
                value={usuario.telefone || ""}
                onChange={(e) => handleChange("telefone", e.target.value)}
                className="mt-1 h-10"
                inputMode="numeric"
                placeholder="(11) 99999-9999"
              />
              <p className="mt-1 text-xs text-gray-500">Máscara somente visual.</p>
            </div>

            <div>
              <Label className="flex items-center gap-1 text-sm text-gray-700">
                <CreditCardIcon className="h-4 w-4 text-[#C41E73]" /> CPF
              </Label>
              <Input
                value={usuario.cpf || ""}
                onChange={(e) => handleChange("cpf", e.target.value)}
                className="mt-1 h-10"
                inputMode="numeric"
                placeholder="000.000.000-00"
              />
            </div>

            <div>
              <Label className="flex items-center gap-1 text-sm text-gray-700">
                <CalendarIcon className="h-4 w-4 text-[#C41E73]" /> Data de nascimento
              </Label>
              <Input
                type="date"
                value={usuario.dataNascimento || ""}
                onChange={(e) => handleChange("dataNascimento", e.target.value)}
                className="mt-1 h-10"
              />
            </div>
          </div>
        </div>

        <div className="rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
          <h2 className="mb-4 flex items-center gap-2 text-base font-semibold text-gray-800">
            <CalendarIcon className="h-5 w-5 text-[#C41E73]" />
            Conta
          </h2>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <div>
              <p className="text-sm text-gray-700">Status</p>
              <div className="mt-2">
                <Badge
                  className={`flex w-fit items-center gap-1 rounded-md border px-2 py-1 text-[12px] font-medium ${
                    usuario.status === "ATIVO"
                      ? "border-green-300 bg-green-100 text-green-700"
                      : "border-red-300 bg-red-100 text-red-700"
                  }`}
                >
                  {usuario.status === "ATIVO" ? <CheckCircleIcon className="h-3.5 w-3.5" /> : <XCircleIcon className="h-3.5 w-3.5" />}
                  {usuario.status === "ATIVO" ? "Ativo" : "Inativo"}
                </Badge>
              </div>
            </div>

            <div>
              <p className="text-sm text-gray-700">Data de cadastro</p>
              <div className="mt-1 flex h-10 items-center rounded-md border border-gray-200 bg-gray-50 px-3 text-sm text-gray-700">
                {usuario.criadoEm ? new Date(usuario.criadoEm).toLocaleString("pt-BR") : "-"}
              </div>
            </div>
          </div>
        </div>

        <div id="documentos" className="rounded-xl border border-gray-100 bg-white shadow-sm">
          <div className="flex items-center justify-between border-b bg-gradient-to-r from-[#FC1EAD]/10 to-transparent px-5 py-4">
            <div>
              <h3 className="flex items-center gap-2 text-base font-semibold text-gray-800">
                <DocumentTextIcon className="h-5 w-5 text-[#C41E73]" />
                Documentos
              </h3>
              <p className="mt-1 text-xs text-gray-500">Gerencie os documentos enviados para este usuário.</p>
            </div>

            <Button onClick={adicionarNovoDocumento} className="bg-[#C41E73] text-sm text-white hover:bg-[#a51861]">
              <PlusIcon className="mr-1 h-4 w-4" />
              Novo documento
            </Button>
          </div>

          <div className="grid grid-cols-1 gap-4 p-5 sm:grid-cols-2 lg:grid-cols-3">
            {documentos.map((doc) => (
              <div key={doc.id} className="overflow-hidden rounded-lg border border-gray-100 shadow-sm transition hover:shadow-md">
                {doc.url ? (
                  isPdf(doc.url) ? (
                    <div className="flex h-40 w-full flex-col items-center justify-center gap-2 bg-gray-100 text-sm text-gray-500">
                      <DocumentTextIcon className="h-8 w-8 text-[#C41E73]" />
                      Documento PDF
                    </div>
                  ) : (
                    <Image src={doc.url} alt="Documento" width={400} height={250} className="h-40 w-full object-cover" />
                  )
                ) : (
                  <div className="flex h-40 w-full items-center justify-center bg-gray-100 text-sm text-gray-400">Sem arquivo</div>
                )}

                <div className="space-y-2 p-4">
                  <label
                    htmlFor={`file-${doc.id}`}
                    className="flex cursor-pointer items-center justify-center gap-1 rounded-md border border-[#C41E73]/40 py-2 text-xs text-[#C41E73] transition hover:bg-[#FC1EAD]/10"
                  >
                    <PencilSquareIcon className="h-4 w-4" />
                    {uploadingDocId === doc.id ? "Enviando..." : doc.url ? "Trocar documento" : "Enviar documento"}
                  </label>

                  <input
                    type="file"
                    id={`file-${doc.id}`}
                    accept="image/*,application/pdf"
                    onChange={(e) => handleImagemChange(e, doc.id)}
                    className="hidden"
                  />

                  <div className="flex flex-col gap-2">
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => doc.url && window.open(doc.url, "_blank")}
                      disabled={!doc.url}
                      className="w-full border-[#C41E73]/40 text-[#C41E73] hover:bg-[#FC1EAD]/10"
                    >
                      <EyeIcon className="mr-1 h-4 w-4" />
                      Ver
                    </Button>

                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => removerDocumento(doc.id)}
                      disabled={removendoDocId === doc.id}
                      className="w-full border-red-300 text-red-600 hover:bg-red-50"
                    >
                      <TrashIcon className="mr-1 h-4 w-4" />
                      {removendoDocId === doc.id ? "Removendo..." : "Remover"}
                    </Button>
                  </div>
                </div>
              </div>
            ))}

            {documentos.length === 0 ? (
              <div className="col-span-full py-4 text-center text-sm text-gray-500">
                Nenhum documento. Adicione acima.
              </div>
            ) : null}
          </div>
        </div>
      </div>
    </section>
  )
}
