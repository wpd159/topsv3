'use client'

import { useEffect, useState } from "react"
import { DashboardStatCard } from "../components/dashboard/dashboard-stats-cards"
import {
  SparklesIcon,
  UserGroupIcon,
  BanknotesIcon,
  PencilSquareIcon,
} from "@heroicons/react/24/solid"

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { toast } from "sonner"

type RankingItem = {
  nomeCompleto: string
  username: string
  totalIndicacoes: number
}

export default function IndicacoesAdminPage() {
  const [openModal, setOpenModal] = useState(false)
  const [creditosPorIndicacao, setCreditosPorIndicacao] = useState<number>(0)
  const [totalIndicacoes, setTotalIndicacoes] = useState<number>(0)
  const [totalCreditosGerados, setTotalCreditosGerados] = useState<number>(0)
  const [ranking, setRanking] = useState<RankingItem[]>([])
  const [loadingRanking, setLoadingRanking] = useState(true)
  const [novoValor, setNovoValor] = useState<number>(0)

  useEffect(() => {
    fetchResumo()
    fetchRanking()
  }, [])

  async function fetchResumo() {
    try {
      const res = await fetch(
        `${process.env.NEXT_PUBLIC_API_URL}/indicacoes/resumo`,
        { credentials: "include" }
      )

      if (!res.ok) throw new Error("Erro ao carregar resumo")

      const data = await res.json()

      setCreditosPorIndicacao(data.creditosPorIndicacao)
      setTotalIndicacoes(data.totalIndicacoes)
      setTotalCreditosGerados(data.totalCreditosGerados)
      setNovoValor(data.creditosPorIndicacao)
    } catch (e) {
      toast.error("Erro ao carregar resumo de indicações.")
    }
  }

  async function fetchRanking() {
    try {
      const res = await fetch(
        `${process.env.NEXT_PUBLIC_API_URL}/indicacoes/ranking`,
        { credentials: "include" }
      )

      // 🔥 Se vier 204, não tentar fazer res.json()
      if (res.status === 204) {
        setRanking([])
        return
      }

      if (!res.ok) throw new Error("Erro ao carregar ranking")

      const data = await res.json()
      setRanking(data)
    } catch (e) {
      toast.error("Erro ao carregar ranking de indicações.")
    } finally {
      setLoadingRanking(false)
    }
  }

  async function handleSalvar() {
    try {
      const res = await fetch(
        `${process.env.NEXT_PUBLIC_API_URL}/indicacoes/config`,
        {
          method: "PUT",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ novoValor }),
        }
      )

      if (!res.ok) throw new Error("Erro ao salvar valor")

      toast.success("Valor atualizado com sucesso!")
      setCreditosPorIndicacao(novoValor)
      setOpenModal(false)
    } catch (e) {
      toast.error("Erro ao atualizar créditos por indicação.")
    }
  }

  return (
    <div className="space-y-10">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-800">Indicações</h1>
          <p className="text-sm text-gray-500 mt-1">
            Controle de indicações, créditos e ranking dos usuários.
          </p>
        </div>

        <Button
          className="bg-[#FC1EAD] hover:bg-[#e01a9a] text-white px-6 py-5 rounded-lg shadow flex items-center"
          onClick={() => setOpenModal(true)}
        >
          <PencilSquareIcon className="w-5 h-5 mr-2" />
          Editar Créditos por Indicação
        </Button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">

        <DashboardStatCard
          label="Créditos por Indicação"
          value={`${creditosPorIndicacao} créditos`}
          icon={<SparklesIcon className="w-6 h-6 text-[#FC1EAD]" />}
        />

        <DashboardStatCard
          label="Total de Indicações no Sistema"
          value={totalIndicacoes}
          icon={<UserGroupIcon className="w-6 h-6 text-[#FC1EAD]" />}
        />

        <DashboardStatCard
          label="Créditos Gerados por Indicação"
          value={totalCreditosGerados}
          icon={<BanknotesIcon className="w-6 h-6 text-[#FC1EAD]" />}
        />
      </div>

      <div className="bg-white border border-gray-100 rounded-xl shadow-sm mt-6 overflow-hidden">

        <div className="px-5 py-4 border-b bg-gradient-to-r from-[#FC1EAD]/10 to-transparent">
          <h3 className="text-base font-semibold text-gray-800">
            Ranking de Indicadores
          </h3>
          <p className="text-xs text-gray-500 mt-1">
            Usuários que mais trouxeram novos membros para a plataforma.
          </p>
        </div>

        <div className="hidden md:block overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow className="bg-gray-50/60 border-b text-gray-500 uppercase text-[11px] tracking-wider">
                <TableHead className="py-3 px-6 font-semibold">Nome Completo</TableHead>
                <TableHead className="py-3 px-6 font-semibold">Username</TableHead>
                <TableHead className="py-3 px-6 font-semibold text-center">
                  Indicações
                </TableHead>
              </TableRow>
            </TableHeader>

            <TableBody>
              {loadingRanking ? (
                <TableRow>
                  <TableCell colSpan={4} className="text-center py-6 text-gray-500">
                    Carregando...
                  </TableCell>
                </TableRow>
              ) : ranking.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={4} className="text-center py-6 text-gray-500">
                    Nenhum usuário encontrado.
                  </TableCell>
                </TableRow>
              ) : (
                ranking.map((u, i) => (
                  <TableRow
                    key={i}
                    className={`transition-all ${
                      i % 2 === 0 ? "bg-white" : "bg-gray-50/40"
                    } hover:bg-[#FC1EAD]/5`}
                  >
                    <TableCell className="py-4 px-6 font-medium text-gray-800">
                      {u.nomeCompleto}
                    </TableCell>

                    <TableCell className="px-6 text-gray-600">
                      @{u.username}
                    </TableCell>

                    <TableCell className="px-6 text-center font-semibold text-gray-800">
                      {u.totalIndicacoes}
                    </TableCell>

                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </div>

        <div className="px-6 py-3 border-t bg-gray-50/70 text-xs text-gray-500 text-right">
          Exibindo top {ranking.length}
        </div>
      </div>

      <Dialog open={openModal} onOpenChange={setOpenModal}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Editar Créditos por Indicação</DialogTitle>
          </DialogHeader>

          <div className="mt-4 space-y-3">
            <label className="text-sm font-medium text-gray-700">
              Novo valor (em créditos)
            </label>
            <Input
              type="number"
              min={1}
              value={novoValor}
              onChange={(e) => setNovoValor(Number(e.target.value))}
              className="py-5"
            />
          </div>

          <DialogFooter className="mt-6">
            <Button
              onClick={() => setOpenModal(false)}
              variant="outline"
              className="px-6"
            >
              Cancelar
            </Button>

            <Button
              onClick={handleSalvar}
              className="bg-[#FC1EAD] hover:bg-[#e01a9a] text-white px-6"
            >
              Salvar
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
