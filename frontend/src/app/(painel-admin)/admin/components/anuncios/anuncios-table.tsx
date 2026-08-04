'use client'

import { useEffect, useMemo, useState } from "react"
import { useRouter } from "next/navigation"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  ArrowRightIcon,
  CheckCircleIcon,
  ClockIcon,
  ExclamationCircleIcon,
  SparklesIcon,
} from "@heroicons/react/24/solid"
import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination"
import { ContractState } from "@/components/feedback/contract-state"
import { fetchStaffAnunciosList } from "@/features/moderation-v2/api/client"
import type { ModerationStaffListItem as Anuncio } from '@/features/moderation-v2/api/types'

interface Props {
  busca?: string
  status?: string
}

export default function GerenciarAnunciosTable({ busca = "", status = "TODOS" }: Props) {
  const router = useRouter()
  const [anuncios, setAnuncios] = useState<Anuncio[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [paginaAtual, setPaginaAtual] = useState(1)
  const [reloadMarker, setReloadMarker] = useState(0)
  const itensPorPagina = 5

  useEffect(() => {
    const fetchAnuncios = async () => {
      setLoading(true)
      setError(null)
      try {
        setAnuncios(await fetchStaffAnunciosList())
      } catch (fetchError) {
        setError(fetchError)
      } finally {
        setLoading(false)
      }
    }

    fetchAnuncios()
  }, [reloadMarker])

  useEffect(() => {
    const triggerReload = () => setReloadMarker((prev) => prev + 1)
    const handleVisibility = () => {
      if (document.visibilityState === "visible") {
        triggerReload()
      }
    }

    window.addEventListener("focus", triggerReload)
    window.addEventListener("admin-revisions-updated", triggerReload as EventListener)
    document.addEventListener("visibilitychange", handleVisibility)

    return () => {
      window.removeEventListener("focus", triggerReload)
      window.removeEventListener("admin-revisions-updated", triggerReload as EventListener)
      document.removeEventListener("visibilitychange", handleVisibility)
    }
  }, [])

  useEffect(() => {
    setPaginaAtual(1)
  }, [busca, status])

  const anunciosFiltrados = useMemo(() => {
    const termo = (busca ?? "").trim().toLowerCase()

    return anuncios.filter((anuncio) => {
      const correspondeBusca =
        (anuncio.titulo ?? "").toLowerCase().includes(termo) ||
        (anuncio.usernameAnunciante ?? "").toLowerCase().includes(termo) ||
        (anuncio.status ?? "").toLowerCase().includes(termo)

      const correspondeStatus =
        status === "TODOS" ||
        anuncio.status === status ||
        (status === "REVISAO_PENDENTE" && Boolean(anuncio.pendingRevision))

      return correspondeBusca && correspondeStatus
    })
  }, [anuncios, busca, status])

  const totalPaginas = Math.max(1, Math.ceil(anunciosFiltrados.length / itensPorPagina))

  useEffect(() => {
    setPaginaAtual((pagina) => Math.min(Math.max(pagina, 1), totalPaginas))
  }, [totalPaginas])

  const anunciosPagina = useMemo(() => {
    const start = (paginaAtual - 1) * itensPorPagina
    const end = paginaAtual * itensPorPagina
    return anunciosFiltrados.slice(start, end)
  }, [anunciosFiltrados, paginaAtual])

  const getBadgeColor = (statusValue: string) => {
    switch (statusValue) {
      case "ATIVO":
        return "bg-green-100 text-green-700 border-green-300"
      case "PENDENTE":
        return "bg-yellow-100 text-yellow-700 border-yellow-300"
      case "REJEITADO":
        return "bg-red-100 text-red-700 border-red-300"
      default:
        return "bg-gray-100 text-gray-600 border-gray-300"
    }
  }

  const getStatusIcon = (statusValue: string) => {
    switch (statusValue) {
      case "ATIVO":
        return <CheckCircleIcon className="mr-1 h-3.5 w-3.5" />
      case "PENDENTE":
        return <ClockIcon className="mr-1 h-3.5 w-3.5" />
      case "REJEITADO":
        return <ExclamationCircleIcon className="mr-1 h-3.5 w-3.5" />
      default:
        return <ClockIcon className="mr-1 h-3.5 w-3.5" />
    }
  }

  const formatarData = (dataIso: string) => {
    const data = new Date(dataIso)
    return data.toLocaleDateString("pt-BR", {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
    })
  }

  if (loading) {
    return <div className="py-10 text-center text-gray-500">Carregando anúncios...</div>
  }

  if (error) {
    return <ContractState error={error} onRetry={() => setReloadMarker((value) => value + 1)} />
  }

  if (anunciosFiltrados.length === 0) {
    return <div className="py-10 text-center text-gray-400">Nenhum anúncio encontrado.</div>
  }

  const renderPaginasCompactas = () => {
    const pages: React.ReactNode[] = []
    let start = Math.max(1, paginaAtual - 2)
    let end = Math.min(totalPaginas, paginaAtual + 2)

    if (paginaAtual <= 3) end = Math.min(5, totalPaginas)
    if (paginaAtual >= totalPaginas - 2) start = Math.max(totalPaginas - 4, 1)

    if (start > 1) {
      pages.push(
        <PaginationItem key={1}>
          <PaginationLink onClick={() => setPaginaAtual(1)} isActive={paginaAtual === 1}>
            1
          </PaginationLink>
        </PaginationItem>
      )
      if (start > 2) pages.push(<span key="start-ellipsis" className="px-2">…</span>)
    }

    for (let page = start; page <= end; page++) {
      pages.push(
        <PaginationItem key={page}>
          <PaginationLink onClick={() => setPaginaAtual(page)} isActive={paginaAtual === page}>
            {page}
          </PaginationLink>
        </PaginationItem>
      )
    }

    if (end < totalPaginas) {
      if (end < totalPaginas - 1) pages.push(<span key="end-ellipsis" className="px-2">…</span>)
      pages.push(
        <PaginationItem key={totalPaginas}>
          <PaginationLink onClick={() => setPaginaAtual(totalPaginas)} isActive={paginaAtual === totalPaginas}>
            {totalPaginas}
          </PaginationLink>
        </PaginationItem>
      )
    }

    return pages
  }

  return (
    <div className="mt-10 overflow-hidden rounded-xl border border-gray-100 bg-white shadow-sm">
      <div className="border-b bg-gradient-to-r from-[#FC1EAD]/10 to-transparent px-5 py-4">
        <h3 className="text-base font-semibold text-gray-800">Gerenciar anúncios</h3>
        <p className="mt-1 text-xs text-gray-500">
          Acompanhe, aprove ou rejeite os anúncios cadastrados.
        </p>
      </div>

      <div className="flex flex-col gap-3 p-4 md:hidden">
        {anunciosPagina.map((anuncio) => (
          <div
            key={anuncio.id}
            className="flex flex-col gap-2 rounded-lg border border-gray-100 bg-white p-3 shadow-xs"
          >
            <div className="flex items-start justify-between gap-3">
              <div className="flex-1">
                <p className="text-sm font-semibold leading-tight text-gray-900">{anuncio.titulo}</p>
                <p className="text-xs text-gray-500">@{anuncio.usernameAnunciante}</p>
                {anuncio.pendingRevision ? (
                  <p className="mt-1 text-[11px] font-medium text-amber-600">Revisão pendente</p>
                ) : null}
              </div>

              <Badge
                variant="outline"
                className={`inline-flex items-center rounded-md border px-2 py-[3px] text-[10px] font-medium ${getBadgeColor(
                  anuncio.status
                )}`}
              >
                {getStatusIcon(anuncio.status)}
                {anuncio.status}
              </Badge>
            </div>

            <p className="text-[11px] text-gray-400">{formatarData(anuncio.dataCriacao)}</p>

            <div className="flex gap-2">
              <Button
                size="sm"
                variant="outline"
                className="flex-1 text-xs"
                onClick={() => router.push(`/admin/anuncios/${anuncio.id}`)}
              >
                Ver detalhes
                <ArrowRightIcon className="ml-1 h-4 w-4" />
              </Button>
              <Button
                size="sm"
                variant="outline"
                className="flex-1 border-amber-200 text-xs text-amber-700 hover:bg-amber-50"
                onClick={() => router.push(`/admin/creditos?aba=beneficios&anuncioId=${anuncio.id}`)}
              >
                Benefícios
                <SparklesIcon className="ml-1 h-4 w-4" />
              </Button>
            </div>
          </div>
        ))}
      </div>

      <div className="hidden overflow-x-auto md:block">
        <Table>
          <TableHeader>
            <TableRow className="border-b bg-gray-50/60 text-[11px] uppercase tracking-wider text-gray-500">
              <TableHead className="w-1/3 px-6 py-3 font-semibold">Título</TableHead>
              <TableHead className="px-6 py-3 font-semibold">Usuário</TableHead>
              <TableHead className="px-6 py-3 text-center font-semibold">Status</TableHead>
              <TableHead className="px-6 py-3 text-right font-semibold">Data</TableHead>
              <TableHead className="px-6 py-3 text-right font-semibold">Ações</TableHead>
            </TableRow>
          </TableHeader>

          <TableBody>
            {anunciosPagina.map((anuncio, index) => (
              <TableRow
                key={anuncio.id}
                className={`transition-all ${
                  index % 2 === 0 ? "bg-white" : "bg-gray-50/40"
                } hover:bg-[#FC1EAD]/5`}
              >
                <TableCell className="px-6 py-4 font-medium text-gray-800">
                  <div className="flex flex-col">
                    <span>{anuncio.titulo}</span>
                    {anuncio.pendingRevision ? (
                      <span className="mt-1 text-[11px] font-medium text-amber-600">Revisão pendente</span>
                    ) : null}
                  </div>
                </TableCell>
                <TableCell className="px-6 text-gray-600">{anuncio.usernameAnunciante}</TableCell>
                <TableCell className="px-6 text-center">
                  <Badge
                    variant="outline"
                    className={`inline-flex items-center justify-center rounded-md border px-2 py-1 text-[11px] font-medium ${getBadgeColor(
                      anuncio.status
                    )}`}
                  >
                    {getStatusIcon(anuncio.status)}
                    {anuncio.status}
                  </Badge>
                </TableCell>
                <TableCell className="px-6 text-right text-gray-500">{formatarData(anuncio.dataCriacao)}</TableCell>
                <TableCell className="px-6 text-right">
                  <Button
                    size="icon"
                    variant="ghost"
                    className="rounded-md border border-gray-200 p-2 text-gray-500 transition-all hover:bg-gray-100"
                    title="Ver anúncio"
                    onClick={() => router.push(`/admin/anuncios/${anuncio.id}`)}
                  >
                    <ArrowRightIcon className="h-4 w-4" />
                  </Button>
                  <Button
                    size="icon"
                    variant="ghost"
                    className="ml-2 rounded-md border border-amber-200 p-2 text-amber-700 transition-all hover:bg-amber-50"
                    title="Gerenciar benefícios"
                    onClick={() => router.push(`/admin/creditos?aba=beneficios&anuncioId=${anuncio.id}`)}
                  >
                    <SparklesIcon className="h-4 w-4" />
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      {totalPaginas > 1 ? (
        <div className="flex justify-center border-t bg-gray-50/70 px-4 py-3">
          <Pagination>
            <PaginationContent>
              <PaginationItem>
                <PaginationPrevious
                  onClick={() => setPaginaAtual((page) => Math.max(page - 1, 1))}
                  className={paginaAtual === 1 ? "pointer-events-none opacity-40" : ""}
                />
              </PaginationItem>

              {renderPaginasCompactas()}

              <PaginationItem>
                <PaginationNext
                  onClick={() => setPaginaAtual((page) => Math.min(page + 1, totalPaginas))}
                  className={paginaAtual === totalPaginas ? "pointer-events-none opacity-40" : ""}
                />
              </PaginationItem>
            </PaginationContent>
          </Pagination>
        </div>
      ) : null}
    </div>
  )
}
