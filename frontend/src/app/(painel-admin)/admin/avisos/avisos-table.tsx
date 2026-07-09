'use client'

import { useEffect, useState } from "react"
import { PowerIcon } from "@heroicons/react/24/solid"
import { toast } from "sonner"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"

type AvisoStatus = "ATIVO" | "INATIVO"

type Aviso = {
  id: number
  titulo: string
  criadoPorNome: string
  status: AvisoStatus
  localExibicao?: string | null
  frequenciaExibicao?: string | null
  ativoDe?: string | null
  ativoAte?: string | null
  permiteDispensar?: boolean | null
}

type Props = {
  busca?: string
  status?: string
  localExibicao?: string
}

function apiUrl(path: string) {
  const base = (process.env.NEXT_PUBLIC_API_URL || "").replace(/\/$/, "")
  return `${base}${path.startsWith("/") ? "" : "/"}${path}`
}

export default function GerenciarAvisosTable({
  busca = "",
  status = "TODOS",
  localExibicao = "TODOS",
}: Props) {
  const [avisos, setAvisos] = useState<Aviso[]>([])
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const size = 6

  useEffect(() => {
    setPage(0)
  }, [busca, status, localExibicao])

  useEffect(() => {
    async function fetchAvisos() {
      try {
        setLoading(true)
        const params = new URLSearchParams()
        if (busca.trim()) params.set("busca", busca.trim())
        params.set("status", status)
        params.set("localExibicao", localExibicao)
        params.set("page", String(page))
        params.set("size", String(size))

        const res = await fetch(apiUrl(`/avisos?${params.toString()}`), {
          credentials: "include",
          cache: "no-store",
        })

        if (!res.ok) {
          throw new Error("Falha ao carregar avisos.")
        }

        const data = await res.json()
        setAvisos(Array.isArray(data?.content) ? data.content : [])
        setTotalPages(Number(data?.totalPages || 0))
      } catch (error: any) {
        toast.error(error?.message || "Falha ao conectar com o servidor.")
        setAvisos([])
        setTotalPages(0)
      } finally {
        setLoading(false)
      }
    }

    fetchAvisos()
  }, [busca, localExibicao, page, status])

  const alternarStatus = async (id: number, ativo: boolean) => {
    try {
      const endpoint = ativo ? "inativar" : "ativar"
      const res = await fetch(apiUrl(`/avisos/${id}/${endpoint}`), {
        method: "PUT",
        credentials: "include",
      })

      if (!res.ok) {
        throw new Error("Erro ao alterar status do aviso.")
      }

      setAvisos((prev) =>
        prev.map((item) => (item.id === id ? { ...item, status: ativo ? "INATIVO" : "ATIVO" } : item))
      )
      toast.success(`Aviso ${ativo ? "inativado" : "ativado"} com sucesso.`)
    } catch (error: any) {
      toast.error(error?.message || "Erro ao alterar status do aviso.")
    }
  }

  if (loading) {
    return <div className="py-10 text-center text-gray-500">Carregando avisos...</div>
  }

  if (!avisos.length) {
    return <div className="py-10 text-center text-gray-400">Nenhum aviso encontrado.</div>
  }

  return (
    <div className="mt-10 overflow-hidden rounded-xl border border-gray-100 bg-white shadow-sm">
      <div className="bg-gradient-to-r from-[#FC1EAD]/10 to-transparent px-5 py-4 border-b">
        <h3 className="text-base font-semibold text-gray-800">Avisos cadastrados</h3>
        <p className="mt-1 text-xs text-gray-500">Local, janela de exibição, frequência e status operacional.</p>
      </div>

      <Table>
        <TableHeader>
          <TableRow className="bg-gray-50 text-[11px] uppercase">
            <TableHead className="px-6 py-3">Título</TableHead>
            <TableHead className="px-6 py-3">Criado por</TableHead>
            <TableHead className="px-6 py-3">Local</TableHead>
            <TableHead className="px-6 py-3">Janela</TableHead>
            <TableHead className="px-6 py-3 text-center">Status</TableHead>
            <TableHead className="px-6 py-3 text-right">Ações</TableHead>
          </TableRow>
        </TableHeader>

        <TableBody>
          {avisos.map((aviso) => {
            const ativo = aviso.status === "ATIVO"
            return (
              <TableRow key={aviso.id} className="hover:bg-[#FC1EAD]/5">
                <TableCell className="px-6 align-top">
                  <div className="font-medium text-gray-800">{aviso.titulo}</div>
                  <div className="mt-1 text-xs text-gray-500">
                    {aviso.frequenciaExibicao || "SEMPRE"}
                    {aviso.permiteDispensar === false ? " • fixo" : " • dispensável"}
                  </div>
                </TableCell>
                <TableCell className="px-6">{aviso.criadoPorNome}</TableCell>
                <TableCell className="px-6">{aviso.localExibicao || "SITE"}</TableCell>
                <TableCell className="px-6 text-xs text-gray-600">
                  <div>{aviso.ativoDe ? new Date(aviso.ativoDe).toLocaleString("pt-BR") : "imediato"}</div>
                  <div>{aviso.ativoAte ? new Date(aviso.ativoAte).toLocaleString("pt-BR") : "sem expiração"}</div>
                </TableCell>
                <TableCell className="px-6 text-center">
                  <Badge
                    variant="outline"
                    className={
                      ativo
                        ? "border-green-300 bg-green-100 text-green-700"
                        : "border-red-300 bg-red-100 text-red-700"
                    }
                  >
                    {ativo ? "Ativo" : "Inativo"}
                  </Badge>
                </TableCell>
                <TableCell className="px-6 text-right">
                  <Button
                    size="icon"
                    variant="ghost"
                    onClick={() => alternarStatus(aviso.id, ativo)}
                    className="rounded-md border border-gray-200 text-gray-600 hover:bg-gray-100"
                    title={ativo ? "Inativar aviso" : "Ativar aviso"}
                  >
                    <PowerIcon className="h-4 w-4" />
                  </Button>
                </TableCell>
              </TableRow>
            )
          })}
        </TableBody>
      </Table>

      {totalPages > 1 && (
        <div className="flex justify-center border-t bg-gray-50/70 px-4 py-3">
          <Pagination>
            <PaginationContent>
              <PaginationItem>
                <PaginationPrevious
                  onClick={() => setPage((value) => Math.max(value - 1, 0))}
                  className={page === 0 ? "pointer-events-none opacity-40" : ""}
                />
              </PaginationItem>

              {Array.from({ length: totalPages }).map((_, index) => (
                <PaginationItem key={index}>
                  <PaginationLink isActive={page === index} onClick={() => setPage(index)}>
                    {index + 1}
                  </PaginationLink>
                </PaginationItem>
              ))}

              <PaginationItem>
                <PaginationNext
                  onClick={() => setPage((value) => Math.min(value + 1, totalPages - 1))}
                  className={page === totalPages - 1 ? "pointer-events-none opacity-40" : ""}
                />
              </PaginationItem>
            </PaginationContent>
          </Pagination>
        </div>
      )}
    </div>
  )
}
