'use client'

import { useRouter } from "next/navigation"
import { useCallback, useEffect, useMemo, useState } from "react"
import {
  ArrowTrendingUpIcon,
  ChatBubbleOvalLeftEllipsisIcon,
  EyeIcon,
  TrophyIcon,
} from "@heroicons/react/24/outline"
import { DashboardStatCard } from "./dashboard-stats-cards"
import { Input } from "@/components/ui/input"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { ContractState } from "@/components/feedback/contract-state"
import {
  fetchAdminPerformanceAnuncios,
  type AdminPerformanceResponse as PerformanceResponse,
} from "@/lib/admin-estatisticas-api"

function formatarNumero(value?: number) {
  return Number(value || 0).toLocaleString("pt-BR")
}

function formatarPercentual(value?: number) {
  return `${Number(value || 0).toLocaleString("pt-BR", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}%`
}

export default function PerformanceAnunciosSection() {
  const router = useRouter()
  const [data, setData] = useState<PerformanceResponse>({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [modalAberto, setModalAberto] = useState<"cliques" | "conversao" | null>(null)
  const [buscaAnuncio, setBuscaAnuncio] = useState("")

  const fetchPerformance = useCallback(async (silent = false) => {
    if (!silent) {
      setLoading(true)
    }

    try {
      setError(null)
      setData(await fetchAdminPerformanceAnuncios())
    } catch (loadError) {
      setError(loadError)
    } finally {
      if (!silent) {
        setLoading(false)
      }
    }
  }, [])

  useEffect(() => {
    void fetchPerformance()
  }, [fetchPerformance])

  useEffect(() => {
    if (modalAberto) {
      fetchPerformance(true)
    }
  }, [modalAberto, fetchPerformance])

  const rankingPorCliques = useMemo(() => (data.rankingPorCliques ?? []).slice(0, 10), [data.rankingPorCliques])
  const topPorConversao = useMemo(() => (data.topPorConversao ?? []).slice(0, 10), [data.topPorConversao])
  const rankingCompletoPorCliques = data.rankingPorCliques ?? []
  const rankingCompletoPorConversao = data.topPorConversao ?? []

  const cards = [
    {
      label: "Visualizações totais",
      value: loading ? "..." : formatarNumero(data.totalVisualizacoes),
      icon: <EyeIcon className="h-6 w-6 text-blue-600" />,
      color: "blue" as const,
    },
    {
      label: "Cliques no WhatsApp",
      value: loading ? "..." : formatarNumero(data.totalCliquesWhatsapp),
      icon: <ChatBubbleOvalLeftEllipsisIcon className="h-6 w-6 text-green-600" />,
      color: "green" as const,
      onClick: () => setModalAberto("cliques"),
    },
    {
      label: "Top por cliques",
      value: loading ? "..." : formatarNumero(rankingPorCliques[0]?.cliquesWhatsapp),
      icon: <TrophyIcon className="h-6 w-6 text-[#C41E73]" />,
      color: "pink" as const,
      onClick: () => setModalAberto("cliques"),
    },
    {
      label: "Top por conversão",
      value: loading ? "..." : formatarPercentual(topPorConversao[0]?.taxaConversao),
      icon: <ArrowTrendingUpIcon className="h-6 w-6 text-yellow-600" />,
      color: "yellow" as const,
      onClick: () => setModalAberto("conversao"),
    },
  ]

  const itensModal = modalAberto === "cliques" ? rankingCompletoPorCliques : rankingCompletoPorConversao
  const itensModalFiltrados = itensModal.filter((item) =>
    (item.titulo || `Anúncio #${item.id}`).toLowerCase().includes(buscaAnuncio.trim().toLowerCase())
  )

  const abrirAnuncio = (id: string | number) => {
    router.push(`/admin/moderacao-v2/${id}`)
    setModalAberto(null)
    setBuscaAnuncio("")
  }

  const tituloModal = modalAberto === "cliques" ? "Lista completa por cliques" : "Lista completa por conversão"
  const descricaoModal = modalAberto === "cliques"
    ? "Todos os anúncios ordenados por cliques no WhatsApp, com visualizações e conversão."
    : "Todos os anúncios ordenados por taxa de conversão, com cliques e visualizações."

  if (error) {
    return (
      <div className="mt-10 space-y-3">
        <h2 className="text-lg font-semibold text-gray-900">Performance dos anuncios</h2>
        <ContractState error={error} onRetry={() => void fetchPerformance()} />
      </div>
    )
  }

  return (
    <div className="mt-10 space-y-6">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {cards.map((card) => (
          <button
            key={card.label}
            type="button"
            onClick={card.onClick}
            disabled={!card.onClick}
            className="text-left disabled:cursor-default"
          >
            <DashboardStatCard
              label={card.label}
              value={card.value}
              icon={card.icon}
              color={card.color}
              className={card.onClick ? "cursor-pointer" : undefined}
            />
          </button>
        ))}
      </div>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
        <div className="overflow-hidden rounded-xl border border-gray-100 bg-white shadow-sm">
          <div className="border-b bg-gradient-to-r from-[#FC1EAD]/10 to-transparent px-5 py-4">
            <h3 className="text-base font-semibold text-gray-800">Ranking de anúncios</h3>
            <p className="mt-1 text-xs text-gray-500">
              Ordenado por cliques no WhatsApp, com visualizações e conversão.
            </p>
          </div>

          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Ranking</TableHead>
                <TableHead>Anúncio</TableHead>
                <TableHead className="text-right">Visualizações</TableHead>
                <TableHead className="text-right">Cliques</TableHead>
                <TableHead className="text-right">Conversão</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading ? (
                <TableRow>
                  <TableCell colSpan={5} className="py-6 text-center text-gray-500">
                    Carregando performance...
                  </TableCell>
                </TableRow>
              ) : rankingPorCliques.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={5} className="py-6 text-center text-gray-500">
                    Nenhum anúncio com dados de performance.
                  </TableCell>
                </TableRow>
              ) : (
                rankingPorCliques.map((item) => (
                  <TableRow key={item.id}>
                    <TableCell className="font-semibold text-gray-800">#{item.posicaoRanking || 0}</TableCell>
                    <TableCell className="max-w-[260px]">
                      <button
                        type="button"
                        className="truncate text-left font-medium text-gray-800 hover:text-[#C41E73]"
                        onClick={() => abrirAnuncio(item.id)}
                        title="Abrir anúncio"
                      >
                        {item.titulo || `Anúncio #${item.id}`}
                      </button>
                    </TableCell>
                    <TableCell className="text-right">{formatarNumero(item.visualizacoes)}</TableCell>
                    <TableCell className="text-right">{formatarNumero(item.cliquesWhatsapp)}</TableCell>
                    <TableCell className="text-right">{formatarPercentual(item.taxaConversao)}</TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>

          <div className="border-t bg-gray-50/70 px-6 py-3 text-right text-xs text-gray-500">
            {loading ? "Carregando..." : `Exibindo top ${rankingPorCliques.length} anúncios por cliques. Clique em "Top por cliques" ou "Cliques no WhatsApp" para ver todos.`}
          </div>
        </div>

        <div className="overflow-hidden rounded-xl border border-gray-100 bg-white shadow-sm">
          <div className="border-b bg-gradient-to-r from-yellow-100 to-transparent px-5 py-4">
            <h3 className="text-base font-semibold text-gray-800">Top anúncios por conversão</h3>
            <p className="mt-1 text-xs text-gray-500">
              Relação entre cliques no WhatsApp e visualizações por anúncio.
            </p>
          </div>

          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Posição</TableHead>
                <TableHead>Anúncio</TableHead>
                <TableHead className="text-right">Cliques</TableHead>
                <TableHead className="text-right">Visualizações</TableHead>
                <TableHead className="text-right">Conversão</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading ? (
                <TableRow>
                  <TableCell colSpan={5} className="py-6 text-center text-gray-500">
                    Carregando performance...
                  </TableCell>
                </TableRow>
              ) : topPorConversao.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={5} className="py-6 text-center text-gray-500">
                    Nenhum anúncio com dados de conversão.
                  </TableCell>
                </TableRow>
              ) : (
                topPorConversao.map((item, index) => (
                  <TableRow key={`${item.id}-${index}`}>
                    <TableCell className="font-semibold text-gray-800">#{index + 1}</TableCell>
                    <TableCell className="max-w-[260px]">
                      <button
                        type="button"
                        className="truncate text-left font-medium text-gray-800 hover:text-[#C41E73]"
                        onClick={() => abrirAnuncio(item.id)}
                        title="Abrir anúncio"
                      >
                        {item.titulo || `Anúncio #${item.id}`}
                      </button>
                    </TableCell>
                    <TableCell className="text-right">{formatarNumero(item.cliquesWhatsapp)}</TableCell>
                    <TableCell className="text-right">{formatarNumero(item.visualizacoes)}</TableCell>
                    <TableCell className="text-right">{formatarPercentual(item.taxaConversao)}</TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>

          <div className="border-t bg-gray-50/70 px-6 py-3 text-right text-xs text-gray-500">
            {loading ? "Carregando..." : `Exibindo top ${topPorConversao.length} anúncios por conversão. Clique em "Top por conversão" para ver todos.`}
          </div>
        </div>
      </div>

      <Dialog
        open={modalAberto !== null}
        onOpenChange={(open) => {
          if (!open) {
            setModalAberto(null)
            setBuscaAnuncio("")
          }
        }}
      >
        <DialogContent className="w-[calc(100vw-2rem)] max-w-[min(1280px,calc(100vw-2rem))] overflow-hidden p-0">
          <DialogHeader className="px-6 pt-6">
            <DialogTitle>{tituloModal}</DialogTitle>
            <DialogDescription>{descricaoModal}</DialogDescription>
          </DialogHeader>

          <div className="max-h-[70vh] overflow-y-auto px-6 pb-6">
            <div className="mb-4">
              <Input
                value={buscaAnuncio}
                onChange={(event) => setBuscaAnuncio(event.target.value)}
                placeholder="Buscar anúncio..."
                className="w-full max-w-xl"
              />
            </div>

            <div className="overflow-x-auto rounded-xl border border-gray-100">
              <table className="w-full table-fixed text-[13px] text-gray-700">
                <colgroup>
                  <col style={{ width: "70px" }} />
                  <col />
                  <col style={{ width: "110px" }} />
                  <col style={{ width: "90px" }} />
                  <col style={{ width: "110px" }} />
                </colgroup>
                <thead className="border-b bg-gray-50 text-[11px] uppercase tracking-wider text-gray-500">
                  <tr>
                    <th className="px-3 py-3 text-left align-middle font-semibold">Ranking</th>
                    <th className="px-3 py-3 text-left align-middle font-semibold">Anúncio</th>
                    <th className="px-3 py-3 text-center align-middle font-semibold whitespace-nowrap">Visualizações</th>
                    <th className="px-3 py-3 text-center align-middle font-semibold whitespace-nowrap">Cliques</th>
                    <th className="px-3 py-3 text-center align-middle font-semibold whitespace-nowrap">Conversão</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {itensModalFiltrados.length === 0 ? (
                    <tr>
                      <td colSpan={5} className="px-3 py-6 text-center text-gray-500 align-middle">
                        Nenhum anúncio com dados de performance.
                      </td>
                    </tr>
                  ) : (
                    itensModalFiltrados.map((item, index) => (
                      <tr key={`${modalAberto}-${item.id}-${index}`} className="hover:bg-gray-50">
                        <td className="px-3 py-3 align-middle font-semibold text-gray-800">
                          #{modalAberto === "cliques" ? (item.posicaoRanking || index + 1) : index + 1}
                        </td>
                        <td className="px-3 py-3 align-middle">
                          <button
                            type="button"
                            className="block w-full min-w-0 overflow-hidden text-ellipsis whitespace-nowrap text-left font-medium text-gray-800 hover:text-[#C41E73]"
                            onClick={() => abrirAnuncio(item.id)}
                            title={item.titulo || `Anúncio #${item.id}`}
                          >
                            {item.titulo || `Anúncio #${item.id}`}
                          </button>
                        </td>
                        <td className="px-3 py-3 text-center align-middle whitespace-nowrap">
                          {formatarNumero(item.visualizacoes)}
                        </td>
                        <td className="px-3 py-3 text-center align-middle whitespace-nowrap">
                          {formatarNumero(item.cliquesWhatsapp)}
                        </td>
                        <td className="px-3 py-3 text-center align-middle whitespace-nowrap">
                          {formatarPercentual(item.taxaConversao)}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}
