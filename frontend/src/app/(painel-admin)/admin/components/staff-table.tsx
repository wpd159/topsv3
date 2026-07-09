'use client'

import { useEffect, useState } from "react"
import { useRouter } from "next/navigation"
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
  PowerIcon,
  EyeIcon,
  PencilSquareIcon,
  TrashIcon,
  CheckCircleIcon,
  XCircleIcon,
} from "@heroicons/react/24/solid"
import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination"
import { toast } from "sonner"
import { normalizarStaff } from "@/utils/normalizer"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog"

type Staff = {
  id: number
  nomeCompleto: string
  email: string
  cargo: string
  status: string
}

export default function GerenciarStaffTable({ busca = "" }: { busca?: string }) {
  const router = useRouter()
  const [staff, setStaff] = useState<Staff[]>([])
  const [loading, setLoading] = useState(true)
  const [paginaAtual, setPaginaAtual] = useState(1)
  const itensPorPagina = 5
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [confirmLoading, setConfirmLoading] = useState(false)
  const [alvoExcluir, setAlvoExcluir] = useState<Staff | null>(null)

  // 🔹 Carregar lista real da API
  const carregarStaff = async () => {
    try {
      setLoading(true)
      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/staff`, {
        credentials: "include",
      })
      if (!res.ok) throw new Error("Falha ao carregar staff")
      const data = await res.json()
      setStaff(data)
    } catch (err) {
      toast.error("Erro ao carregar membros da equipe.")
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    carregarStaff()
  }, [])

  // Atualizar quando um novo staff for criado (evento global)
  useEffect(() => {
    const onRefresh = () => {
      setPaginaAtual(1)
      carregarStaff()
    }
    window.addEventListener("admin:staff:refresh", onRefresh as EventListener)
    return () => {
      window.removeEventListener("admin:staff:refresh", onRefresh as EventListener)
    }
  }, [])

  // 🔍 Filtragem
  const staffFiltrado = staff.filter((s) =>
    [s.nomeCompleto, s.email, s.cargo]
      .some((campo) => campo?.toLowerCase().includes(busca.toLowerCase()))
  )

  // Paginação
  const totalPaginas = Math.ceil(staffFiltrado.length / itensPorPagina)
  const staffPagina = staffFiltrado.slice(
    (paginaAtual - 1) * itensPorPagina,
    paginaAtual * itensPorPagina
  )

  // Alternar status
  const alternarStatus = async (membro: Staff) => {
    try {
      const rota =
        membro.status === "ATIVO"
          ? `/staff/${membro.id}/inativar`
          : `/staff/${membro.id}/ativar`

      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}${rota}`, {
        method: "PUT",
        credentials: "include",
      })
      if (!res.ok) throw new Error("Falha ao atualizar status")

      setStaff((prev) =>
        prev.map((m) =>
          m.id === membro.id
            ? { ...m, status: membro.status === "ATIVO" ? "INATIVO" : "ATIVO" }
            : m
        )
      )
      toast.success("Status atualizado com sucesso.")
    } catch (e) {
      toast.error("Erro ao atualizar status do membro.")
    }
  }

  // Excluir membro
  const excluirMembro = async (id: number) => {
    try {
      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/staff/${id}`, {
        method: "DELETE",
        credentials: "include",
      })
      if (!res.ok) throw new Error("Erro ao excluir membro")
      setStaff((prev) => prev.filter((m) => m.id !== id))
      toast.success("Membro excluído com sucesso.")
    } catch (e) {
      toast.error("Erro ao excluir membro.")
    }
  }

  if (loading)
    return (
      <div className="text-center py-10 text-gray-500">
        Carregando membros da equipe...
      </div>
    )

  return (
    <div className="bg-white border border-gray-100 rounded-xl shadow-sm mt-10 overflow-hidden">
      {/* Header */}
      <div className="px-5 py-4 border-b bg-gradient-to-r from-[#FC1EAD]/10 to-transparent">
        <h3 className="text-base font-semibold text-gray-800">Gerenciar Staff</h3>
        <p className="text-xs text-gray-500 mt-1">
          Visualize, edite e gerencie os membros do staff.
        </p>
      </div>

      {/* Tabela */}
      <div className="hidden md:block overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow className="bg-gray-50/60 border-b text-gray-500 uppercase text-[11px] tracking-wider">
              <TableHead className="py-3 px-6 font-semibold">Nome</TableHead>
              <TableHead className="py-3 px-6 font-semibold">Email</TableHead>
              <TableHead className="py-3 px-6 font-semibold">Cargo</TableHead>
              <TableHead className="py-3 px-6 font-semibold text-center">
                Status
              </TableHead>
              <TableHead className="py-3 px-6 font-semibold text-right w-[160px]">
                Ações
              </TableHead>
            </TableRow>
          </TableHeader>

          <TableBody>
            {staffPagina.length === 0 ? (
              <TableRow>
                <TableCell
                  colSpan={5}
                  className="text-center py-8 text-gray-500 text-sm"
                >
                  Nenhum membro encontrado.
                </TableCell>
              </TableRow>
            ) : (
              staffPagina.map((m, i) => (
                <TableRow
                  key={m.id}
                  className={`transition-all ${
                    i % 2 === 0 ? "bg-white" : "bg-gray-50/40"
                  } hover:bg-[#FC1EAD]/5`}
                >
                  <TableCell className="py-4 px-6 font-medium text-gray-800">
                    {m.nomeCompleto}
                  </TableCell>
                  <TableCell className="px-6 text-gray-600">{m.email}</TableCell>
                  <TableCell className="px-6 text-gray-800">{normalizarStaff(m.cargo)}</TableCell>

                  <TableCell className="px-6 text-center">
                    <Badge
                      variant="outline"
                      className={`inline-flex items-center justify-center text-[11px] font-medium px-2 py-1 rounded-md border ${
                        m.status === "ATIVO"
                          ? "bg-green-100 text-green-700 border-green-300"
                          : "bg-red-100 text-red-700 border-red-300"
                      }`}
                    >
                      {m.status === "ATIVO" ? (
                        <CheckCircleIcon className="w-3.5 h-3.5 mr-1" />
                      ) : (
                        <XCircleIcon className="w-3.5 h-3.5 mr-1" />
                      )}
                      {m.status === "ATIVO" ? "Ativo" : "Inativo"}
                    </Badge>
                  </TableCell>

                  {/* Ações */}
                  <TableCell className="px-6 text-right">
                    <div className="flex items-center justify-end gap-2 min-w-[140px]">
                      <Button
                        size="icon"
                        variant="ghost"
                        onClick={() => alternarStatus(m)}
                        className="border rounded-md p-2 hover:scale-105 transition-all"
                        title={
                          m.status === "ATIVO"
                            ? "Desativar membro"
                            : "Ativar membro"
                        }
                      >
                        <PowerIcon className="w-4 h-4 text-gray-500" />
                      </Button>

                      <Button
                        size="icon"
                        variant="outline"
                        className="text-blue-600 border-blue-300 hover:bg-blue-50"
                        onClick={() => router.push(`/admin/staff/${m.id}/editar`)}
                        title="Editar"
                      >
                        <PencilSquareIcon className="w-4 h-4" />
                      </Button>

                      <Button
                        size="icon"
                        variant="outline"
                        className="text-[#C41E73] border-[#C41E73]/40 hover:bg-[#FC1EAD]/10"
                        onClick={() => router.push(`/admin/staff/${m.id}`)}
                        title="Ver detalhes"
                      >
                        <EyeIcon className="w-4 h-4" />
                      </Button>

                      <Button
                        size="icon"
                        variant="outline"
                        className="text-red-600 border-red-300 hover:bg-red-50"
                        onClick={() => {
                          setAlvoExcluir(m)
                          setConfirmOpen(true)
                        }}
                        title="Excluir membro"
                      >
                        <TrashIcon className="w-4 h-4" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))
            )}
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
                    paginaAtual === totalPaginas ? "opacity-40 pointer-events-none" : ""
                  }
                />
              </PaginationItem>
            </PaginationContent>
          </Pagination>
        </div>
      )}

      <Dialog
        open={confirmOpen}
        onOpenChange={(v) => {
          setConfirmOpen(v)
          if (!v) {
            setAlvoExcluir(null)
            setConfirmLoading(false)
          }
        }}
      >
        <DialogContent className="sm:max-w-md rounded-xl">
          <DialogHeader>
            <DialogTitle>Confirmar exclusão</DialogTitle>
            <DialogDescription>
              Tem certeza que deseja excluir{" "}
              <span className="font-semibold text-gray-800">
                {alvoExcluir?.nomeCompleto || "este membro"}
              </span>
              ? Esta ação não pode ser desfeita.
            </DialogDescription>
          </DialogHeader>
          <div className="flex justify-end gap-3">
            <Button
              variant="outline"
              onClick={() => setConfirmOpen(false)}
              disabled={confirmLoading}
            >
              Cancelar
            </Button>
            <Button
              className="bg-red-600 hover:bg-red-700 text-white"
              disabled={confirmLoading || !alvoExcluir}
              onClick={async () => {
                if (!alvoExcluir) return
                try {
                  setConfirmLoading(true)
                  await excluirMembro(alvoExcluir.id)
                  setConfirmOpen(false)
                  setAlvoExcluir(null)
                } finally {
                  setConfirmLoading(false)
                }
              }}
            >
              {confirmLoading ? "Excluindo..." : "Excluir"}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}
