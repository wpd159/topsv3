"use client"

import { useEffect, useState } from "react"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import {
  EyeIcon,
  CheckCircleIcon,
  XCircleIcon,
  PencilSquareIcon,
} from "@heroicons/react/24/solid"
import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination"
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog"
import { toast } from "sonner"

interface Sugestao {
  id: number
  titulo: string
  descricao: string
  tipo: string
  status: "NOVO" | "EM_ANALISE" | "RESOLVIDO" | "RECUSADO"
  usuarioNome?: string | null
  usuarioEmail?: string | null
  emailOpcional?: string | null
  criadoEm: string
}

export default function SugestoesTable({
  busca = "",
  status = "TODAS",
}: {
  busca?: string
  status?: string
}) {
  const [lista, setLista] = useState<Sugestao[]>([])
  const [loading, setLoading] = useState(true)
  const [paginaAtual, setPaginaAtual] = useState(1)
  const itensPorPagina = 5

  // 🚀 Modal
  const [modalAberto, setModalAberto] = useState(false)
  const [selecionada, setSelecionada] = useState<Sugestao | null>(null)

  const carregar = async () => {
    setLoading(true)
    try {
      const res = await fetch(
        `${process.env.NEXT_PUBLIC_API_URL}/sugestoes/admin?pagina=0&tamanho=200`,
        { credentials: "include" }
      )

      const json = await res.json()
      setLista(json.content || [])
    } catch {
      toast.error("Erro ao carregar sugestões.")
    } finally {
      setLoading(false)
    }
  }

  // 👉 BUSCAR POR ID E ABRIR MODAL
  const visualizar = async (id: number) => {
    try {
      const res = await fetch(
        `${process.env.NEXT_PUBLIC_API_URL}/sugestoes/admin/${id}`,
        { credentials: "include" }
      )

      if (!res.ok) throw new Error()

      const data = await res.json()
      setSelecionada(data)
      setModalAberto(true)
    } catch {
      toast.error("Erro ao carregar detalhes.")
    }
  }

  const atualizarStatus = async (
    id: number,
    novoStatus: Sugestao["status"]
  ) => {
    try {
      const res = await fetch(
        `${process.env.NEXT_PUBLIC_API_URL}/sugestoes/admin/${id}/status?status=${novoStatus}`,
        { method: "PATCH", credentials: "include" }
      )

      if (!res.ok) throw new Error()

      toast.success("Status atualizado!")
      carregar()
    } catch {
      toast.error("Erro ao atualizar status.")
    }
  }

  useEffect(() => {
    carregar()
  }, [])

  // 🔍 Filtro
  const filtradas = lista.filter((s) => {
    const termo = busca.toLowerCase()

    const matchBusca =
      s.titulo.toLowerCase().includes(termo) ||
      s.descricao.toLowerCase().includes(termo) ||
      (s.usuarioNome || "").toLowerCase().includes(termo)

    const matchStatus = status === "TODAS" || s.status === status

    return matchBusca && matchStatus
  })

  const totalPaginas = Math.ceil(filtradas.length / itensPorPagina)
  const pagina = filtradas.slice(
    (paginaAtual - 1) * itensPorPagina,
    paginaAtual * itensPorPagina
  )

  if (loading)
    return (
      <div className="text-center py-10 text-gray-500">
        Carregando sugestões...
      </div>
    )

  if (filtradas.length === 0)
    return (
      <div className="text-center py-10 text-gray-400">
        Nenhuma sugestão encontrada.
      </div>
    )

  const badgeStatus = (status: Sugestao["status"]) => {
    switch (status) {
      case "NOVO":
        return "bg-blue-100 text-blue-700 border-blue-300"
      case "EM_ANALISE":
        return "bg-yellow-100 text-yellow-700 border-yellow-300"
      case "RESOLVIDO":
        return "bg-green-100 text-green-700 border-green-300"
      case "RECUSADO":
        return "bg-red-100 text-red-700 border-red-300"
      default:
        return ""
    }
  }

  return (
    <>
      <div className="bg-white border border-gray-100 rounded-xl shadow-sm mt-10 overflow-hidden">
        {/* Header */}
        <div className="px-5 py-4 border-b bg-gradient-to-r from-[#FC1EAD]/10 to-transparent">
          <h3 className="text-base font-semibold text-gray-800">
            Sugestões Recebidas
          </h3>
          <p className="text-xs text-gray-500 mt-1">
            Veja todas as sugestões de melhorias e relatos de bugs.
          </p>
        </div>

        {/* Tabela */}
        <div className="hidden md:block overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow className="bg-gray-50/60 border-b text-gray-500 uppercase text-[11px] tracking-wider">
                <TableHead className="py-3 px-6 font-semibold">Título</TableHead>
                <TableHead className="py-3 px-6 font-semibold">Tipo</TableHead>
                <TableHead className="py-3 px-6 font-semibold text-center">Status</TableHead>
                <TableHead className="py-3 px-6 font-semibold">Data</TableHead>
                <TableHead className="py-3 px-6 font-semibold text-right">Ações</TableHead>
              </TableRow>
            </TableHeader>

            <TableBody>
              {pagina.map((s, i) => (
                <TableRow
                  key={s.id}
                  className={`transition-all ${i % 2 === 0 ? "bg-white" : "bg-gray-50/40"} hover:bg-[#FC1EAD]/5`}
                >
                  <TableCell className="py-4 px-6 font-medium text-gray-800">
                    {s.titulo}
                  </TableCell>

                  <TableCell className="px-6 text-gray-600">{s.tipo}</TableCell>

                  <TableCell className="px-6 text-center">
                    <Badge
                      variant="outline"
                      className={`inline-flex items-center text-[11px] px-2 py-1 rounded-md border ${badgeStatus(s.status)}`}
                    >
                      {s.status}
                    </Badge>
                  </TableCell>

                  <TableCell className="px-6 text-gray-600">
                    {new Date(s.criadoEm).toLocaleDateString()}
                  </TableCell>

                  <TableCell className="px-6 text-right flex items-center justify-end gap-2">
                    {/* Ver detalhes */}
                    <Button
                      size="sm"
                      variant="outline"
                      className="text-[#C41E73] border-[#C41E73]/40 hover:bg-[#FC1EAD]/10"
                      onClick={() => visualizar(s.id)}
                    >
                      <EyeIcon className="w-4 h-4 mr-1" />
                      Ver
                    </Button>

                    {/* RESOLVIDO */}
                    <Button
                      size="icon"
                      variant="outline"
                      className="hover:bg-green-100 border-green-300 text-green-700"
                      onClick={() => atualizarStatus(s.id, "RESOLVIDO")}
                    >
                      <CheckCircleIcon className="w-4 h-4" />
                    </Button>

                    {/* RECUSADO */}
                    <Button
                      size="icon"
                      variant="outline"
                      className="hover:bg-red-100 border-red-300 text-red-700"
                      onClick={() => atualizarStatus(s.id, "RECUSADO")}
                    >
                      <XCircleIcon className="w-4 h-4" />
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>

        {/* Paginação */}
        {totalPaginas > 1 && (
          <div className="border-t bg-gray-50/70 px-4 py-3 flex justify-center">
            <Pagination>
              <PaginationContent>
                <PaginationItem>
                  <PaginationPrevious
                    onClick={() => setPaginaAtual((p) => Math.max(p - 1, 1))}
                    className={paginaAtual === 1 ? "opacity-40 pointer-events-none" : ""}
                  />
                </PaginationItem>

                {Array.from({ length: totalPaginas }, (_, i) => (
                  <PaginationItem key={i}>
                    <PaginationLink
                      onClick={() => setPaginaAtual(i + 1)}
                      isActive={paginaAtual === i + 1}
                    >
                      {i + 1}
                    </PaginationLink>
                  </PaginationItem>
                ))}

                <PaginationItem>
                  <PaginationNext
                    onClick={() => setPaginaAtual((p) => Math.min(p + 1, totalPaginas))}
                    className={
                      paginaAtual === totalPaginas
                        ? "opacity-40 pointer-events-none"
                        : ""
                    }
                  />
                </PaginationItem>
              </PaginationContent>
            </Pagination>
          </div>
        )}
      </div>

    <Dialog open={modalAberto} onOpenChange={setModalAberto}>
      <DialogContent className="max-w-lg p-0 overflow-hidden rounded-xl shadow-lg">

        {/* HEADER */}
        <div className="px-6 py-4 border-b bg-gray-50">
          <DialogHeader>
            <DialogTitle className="text-lg font-semibold text-gray-800">
              {selecionada?.titulo}
            </DialogTitle>
            <DialogDescription className="text-sm text-gray-500">
              Informações completas da sugestão enviada.
            </DialogDescription>
          </DialogHeader>
        </div>

        {/* CONTENT */}
        <div className="px-6 py-5 space-y-5">

          {/* Tipo */}
          <div className="flex flex-col gap-1">
            <label className="text-sm font-semibold text-gray-700">Tipo</label>
            <input
              readOnly
              value={selecionada?.tipo || ""}
              className="w-full border border-gray-300 bg-gray-100 rounded-md px-3 py-2 text-gray-700 text-sm"
            />
          </div>

          {/* Descrição */}
          <div className="flex flex-col gap-1">
            <label className="text-sm font-semibold text-gray-700">Descrição</label>
            <textarea
              readOnly
              value={selecionada?.descricao || ""}
              className="w-full border border-gray-300 bg-gray-100 rounded-md px-3 py-2 text-gray-700 text-sm h-32 resize-none"
            />
          </div>

          {/* Status */}
          <div className="flex flex-col gap-1">
            <label className="text-sm font-semibold text-gray-700">Status</label>
            <input
              readOnly
              value={selecionada?.status || ""}
              className="w-full border border-gray-300 bg-gray-100 rounded-md px-3 py-2 text-gray-700 text-sm"
            />
          </div>

          {/* Data */}
          <div className="flex flex-col gap-1">
            <label className="text-sm font-semibold text-gray-700">Data</label>
            <input
              readOnly
              value={
                selecionada
                  ? new Date(selecionada.criadoEm).toLocaleString()
                  : ""
              }
              className="w-full border border-gray-300 bg-gray-100 rounded-md px-3 py-2 text-gray-700 text-sm"
            />
          </div>

        </div>

        {/* FOOTER */}
        <div className="px-6 py-4 border-t bg-gray-50 flex justify-end">
          <Button
            onClick={() => setModalAberto(false)}
            variant="outline"
            className="border-gray-300 text-gray-700 hover:bg-gray-100"
          >
            Fechar
          </Button>
        </div>

      </DialogContent>
    </Dialog>
    </>
  )
}
