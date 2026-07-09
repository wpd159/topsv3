'use client'

import React, { useEffect, useMemo, useState } from "react"
import { useSearchParams } from "next/navigation"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { FunnelIcon } from "@heroicons/react/24/outline"
import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination"
import LogsTable from "../components/registros/registros-table"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"

type Log = {
  id: number
  usuario: string
  acao: string
  modulo: string
  dataHora: string
}

export default function AdminLogsPage() {
  const searchParams = useSearchParams()
  const [logsRaw, setLogsRaw] = useState<Log[]>([])
  const [loading, setLoading] = useState(true)

  const [paginaAtual, setPaginaAtual] = useState(1)

  const [filtroAcao, setFiltroAcao] = useState("TODOS")
  const [busca, setBusca] = useState(searchParams.get("busca") || "")
  const [periodo, setPeriodo] = useState({ inicio: "", fim: "" })

  const itensPorPagina = 10
  const API_URL = process.env.NEXT_PUBLIC_API_URL

  useEffect(() => {
    const buscaInicial = searchParams.get("busca") || ""
    setBusca(buscaInicial)
  }, [searchParams])

  // ✅ parse: trata dataHora como horário BR (UTC-3) mesmo vindo sem timezone
  // Ex: "2026-02-06T00:45:12" -> cria Date equivalente ao instante UTC = 03:45Z (subtrai 3h)
  const parseBrasilToMs = (iso?: string) => {
    if (!iso) return 0

    // se vier com Z ou offset, deixa o browser resolver (já tá timezone-aware)
    if (/[zZ]$/.test(iso) || /[+-]\d{2}:\d{2}$/.test(iso)) {
      const ms = Date.parse(iso)
      return Number.isFinite(ms) ? ms : 0
    }

    // tenta parse manual "YYYY-MM-DDTHH:mm:ss(.SSS)?"
    const m = iso.match(
      /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?(?:\.(\d{1,3}))?$/
    )
    if (!m) {
      const ms = Date.parse(iso)
      return Number.isFinite(ms) ? ms : 0
    }

    const year = Number(m[1])
    const month = Number(m[2]) - 1
    const day = Number(m[3])
    const hour = Number(m[4])
    const minute = Number(m[5])
    const second = Number(m[6] ?? "0")
    const milli = Number((m[7] ?? "0").padEnd(3, "0"))

    // iso é "hora local do Brasil" (UTC-3).
    // Para gerar o instante UTC correto: soma 3h no UTC ou, equivalente:
    // Date.UTC(..., hour + 3)
    const ms = Date.UTC(year, month, day, hour + 3, minute, second, milli)
    return Number.isFinite(ms) ? ms : 0
  }

  // ✅ reset pagina ao mudar filtros
  useEffect(() => {
    setPaginaAtual(1)
  }, [filtroAcao, busca, periodo.inicio, periodo.fim])

  useEffect(() => {
    async function fetchLogs() {
      if (!API_URL) return
      setLoading(true)

      try {
        const params = new URLSearchParams({
          page: "0",
          size: "500",
        })

        const res = await fetch(`${API_URL}/logs?${params.toString()}`, {
          credentials: "include",
          headers: { "Content-Type": "application/json" },
        })

        if (!res.ok) throw new Error("Falha ao carregar logs")

        const data = await res.json()
        const content = Array.isArray(data) ? data : (data.content ?? [])

        setLogsRaw(Array.isArray(content) ? content : [])
      } catch (err) {
        setLogsRaw([])
      } finally {
        setLoading(false)
      }
    }

    fetchLogs()
  }, [API_URL])

  const inPeriodo = (log: Log) => {
    const ms = parseBrasilToMs(log.dataHora)
    if (!ms) return false

    // período.inicio/fim são datas (sem hora) no fuso local do usuário (BR)
    // vamos comparar por data local (BR) também.
    if (periodo.inicio) {
      const startMs = Date.parse(`${periodo.inicio}T00:00:00`)
      // startMs aqui é local do browser; como o browser é BR, ok.
      if (Number.isFinite(startMs) && ms < startMs) return false
    }

    if (periodo.fim) {
      const endMs = Date.parse(`${periodo.fim}T23:59:59`)
      if (Number.isFinite(endMs) && ms > endMs) return false
    }

    return true
  }

  // ✅ filtros + ordenação (mais recente primeiro)
  const logsFiltrados = useMemo(() => {
    const termo = (busca ?? "").trim().toLowerCase()

    return logsRaw
      .filter((l) => {
        const okBusca =
          !termo ||
          (l.usuario ?? "").toLowerCase().includes(termo) ||
          (l.modulo ?? "").toLowerCase().includes(termo) ||
          (l.acao ?? "").toLowerCase().includes(termo)

        const okAcao =
          filtroAcao === "TODOS" || (l.acao ?? "").toUpperCase() === filtroAcao

        const okPeriodo =
          (!periodo.inicio && !periodo.fim) ? true : inPeriodo(l)

        return okBusca && okAcao && okPeriodo
      })
      .sort((a, b) => parseBrasilToMs(b.dataHora) - parseBrasilToMs(a.dataHora))
  }, [logsRaw, busca, filtroAcao, periodo.inicio, periodo.fim])

  const totalPaginas = useMemo(() => {
    return Math.max(1, Math.ceil(logsFiltrados.length / itensPorPagina))
  }, [logsFiltrados.length])

  // ✅ clamp pagina
  useEffect(() => {
    setPaginaAtual((p) => Math.min(Math.max(p, 1), totalPaginas))
  }, [totalPaginas])

  const logsPagina = useMemo(() => {
    const start = (paginaAtual - 1) * itensPorPagina
    const end = paginaAtual * itensPorPagina
    return logsFiltrados.slice(start, end)
  }, [logsFiltrados, paginaAtual])

  // ✅ paginação compacta (igual Usuários)
  const renderPaginasCompactas = useMemo(() => {
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

    for (let p = start; p <= end; p++) {
      pages.push(
        <PaginationItem key={p}>
          <PaginationLink onClick={() => setPaginaAtual(p)} isActive={paginaAtual === p}>
            {p}
          </PaginationLink>
        </PaginationItem>
      )
    }

    if (end < totalPaginas) {
      if (end < totalPaginas - 1) pages.push(<span key="end-ellipsis" className="px-2">…</span>)
      pages.push(
        <PaginationItem key={totalPaginas}>
          <PaginationLink
            onClick={() => setPaginaAtual(totalPaginas)}
            isActive={paginaAtual === totalPaginas}
          >
            {totalPaginas}
          </PaginationLink>
        </PaginationItem>
      )
    }

    return pages
  }, [paginaAtual, totalPaginas])

  return (
    <section>
      {/* Cabeçalho */}
      <div className="flex flex-col gap-1 mb-6">
        <h1 className="text-2xl font-bold text-gray-800">Logs do Sistema</h1>
        <p className="text-sm text-gray-500">
          Acompanhe todas as ações realizadas pelos usuários no sistema.
        </p>
      </div>

      {/* Card de filtros */}
      <div className="bg-white border border-gray-100 rounded-xl shadow-sm overflow-hidden mb-8">
        <div className="px-5 py-4 border-b bg-gradient-to-r from-[#FC1EAD]/10 to-transparent">
          <h3 className="text-base font-semibold text-gray-800">Filtros de Pesquisa</h3>
          <p className="text-xs text-gray-500 mt-1">
            Refine os registros exibidos utilizando os filtros abaixo.
          </p>
        </div>

        <div className="p-5">
          <div className="flex flex-col md:flex-row md:items-end md:justify-between gap-4">
            {/* Busca + Ação */}
            <div className="flex flex-col sm:flex-row gap-3 w-full md:w-auto">
              <Input
                placeholder="Buscar por usuário, ação ou módulo..."
                value={busca}
                onChange={(e) => setBusca(e.target.value)}
                className="w-full sm:w-72"
              />

              <Select value={filtroAcao} onValueChange={setFiltroAcao}>
                <SelectTrigger className="w-full sm:w-56">
                  <SelectValue placeholder="Ação" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="TODOS">Todas as ações.</SelectItem>
                  <SelectItem value="CRIAR">Criação</SelectItem>
                  <SelectItem value="ATUALIZAR">Atualização</SelectItem>
                  <SelectItem value="DELETAR">Exclusão</SelectItem>
                  <SelectItem value="LOGIN">Login</SelectItem>
                </SelectContent>
              </Select>
            </div>

            {/* Datas e limpar */}
            <div className="flex flex-col sm:flex-row gap-3 w-full md:w-auto">
              <Input
                type="date"
                value={periodo.inicio}
                onChange={(e) => setPeriodo((p) => ({ ...p, inicio: e.target.value }))}
                className="text-sm"
              />
              <Input
                type="date"
                value={periodo.fim}
                onChange={(e) => setPeriodo((p) => ({ ...p, fim: e.target.value }))}
                className="text-sm"
              />

              <Button
                variant="outline"
                className="border-gray-300 text-gray-700 hover:bg-gray-100 flex items-center gap-2"
                onClick={() => {
                  setPeriodo({ inicio: "", fim: "" })
                  setBusca("")
                  setFiltroAcao("TODOS")
                  setPaginaAtual(1)
                }}
              >
                <FunnelIcon className="w-4 h-4" /> Limpar
              </Button>
            </div>
          </div>
        </div>
      </div>

      {/* Tabela */}
      <LogsTable logs={logsPagina} loading={loading} />

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

              {renderPaginasCompactas}

              <PaginationItem>
                <PaginationNext
                  onClick={() => setPaginaAtual((p) => Math.min(p + 1, totalPaginas))}
                  className={paginaAtual === totalPaginas ? "opacity-40 pointer-events-none" : ""}
                />
              </PaginationItem>
            </PaginationContent>
          </Pagination>
        </div>
      )}
    </section>
  )
}
