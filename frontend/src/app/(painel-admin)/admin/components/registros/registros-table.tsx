"use client"

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { Badge } from "@/components/ui/badge"
import {
  ClockIcon,
  UserIcon,
  ClipboardDocumentListIcon,
  CheckCircleIcon,
  XCircleIcon,
  ArrowUpCircleIcon,
  ArrowDownCircleIcon,
} from "@heroicons/react/24/solid"
import { cn } from "@/lib/utils"

type Log = {
  id: number
  usuario: string
  acao: string
  modulo: string
  dataHora: string
}

interface Props {
  logs: Log[]
  loading: boolean
}

export default function LogsTable({ logs, loading }: Props) {
  const formatarData = (dataIso: string) => {
    if (!dataIso) return "-"
    const d = new Date(dataIso)
    return d.toLocaleString("pt-BR", {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    })
  }

  const getBadge = (acao: string) => {
    switch (acao) {
      case "CRIAR":
        return {
          icon: <ArrowUpCircleIcon className="w-3.5 h-3.5 mr-1" />,
          color: "bg-green-50 text-green-700 border-green-200",
          label: "Criação",
        }
      case "ATUALIZAR":
        return {
          icon: <ArrowDownCircleIcon className="w-3.5 h-3.5 mr-1 text-yellow-600" />,
          color: "bg-yellow-50 text-yellow-700 border-yellow-200",
          label: "Atualização",
        }
      case "DELETAR":
        return {
          icon: <XCircleIcon className="w-3.5 h-3.5 mr-1 text-red-600" />,
          color: "bg-red-50 text-red-700 border-red-200",
          label: "Exclusão",
        }
      case "LOGIN":
        return {
          icon: <CheckCircleIcon className="w-3.5 h-3.5 mr-1 text-blue-600" />,
          color: "bg-blue-50 text-blue-700 border-blue-200",
          label: "Login",
        }
      default:
        return {
          icon: null,
          color: "bg-gray-50 text-gray-600 border-gray-200",
          label: acao,
        }
    }
  }

  return (
    <div className="bg-white border border-gray-200 rounded-xl shadow-sm mt-8 overflow-hidden">
      {/* Cabeçalho */}
      <div className="px-6 py-4 border-b bg-gradient-to-r from-[#FC1EAD]/10 to-transparent">
        <h3 className="text-base font-semibold text-gray-800">
          Histórico de Ações do Sistema
        </h3>
        <p className="text-xs text-gray-500 mt-1">
          Registro de operações realizadas por usuários — criação, atualização, exclusão e login.
        </p>
      </div>

      {/* Tabela */}
      <div className="overflow-x-auto">
        <Table className="w-full border-t border-gray-100">
          <TableHeader>
            <TableRow className="bg-gray-50 text-gray-600 text-[12px] uppercase tracking-wider">
              <TableHead className="px-6 py-3 font-semibold border-b border-gray-200">
                Usuário
              </TableHead>
              <TableHead className="px-6 py-3 font-semibold border-b border-gray-200">
                Ação
              </TableHead>
              <TableHead className="px-6 py-3 font-semibold border-b border-gray-200">
                Módulo
              </TableHead>
              <TableHead className="px-6 py-3 font-semibold border-b border-gray-200 text-right">
                Data / Hora
              </TableHead>
            </TableRow>
          </TableHeader>

          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={4} className="text-center py-6 text-gray-500">
                  Carregando logs...
                </TableCell>
              </TableRow>
            ) : logs.length === 0 ? (
              <TableRow>
                <TableCell colSpan={4} className="text-center py-6 text-gray-400">
                  Nenhum log encontrado.
                </TableCell>
              </TableRow>
            ) : (
              logs.map((log, i) => {
                const badge = getBadge(log.acao)
                return (
                  <TableRow
                    key={log.id}
                    className={cn(
                      "border-b border-gray-100 hover:bg-[#FC1EAD]/5 transition-colors"
                    )}
                  >
                    <TableCell className="px-6 py-3 flex items-center gap-2 text-gray-800 font-medium">
                      <UserIcon className="w-4 h-4 text-gray-400" />
                      {log.usuario}
                    </TableCell>

                    <TableCell className="px-6 py-3">
                      <Badge
                        variant="outline"
                        className={cn(
                          "inline-flex items-center text-[11px] font-medium px-2.5 py-1 rounded-md border",
                          badge.color
                        )}
                      >
                        {badge.icon}
                        {badge.label}
                      </Badge>
                    </TableCell>

                    <TableCell className="px-6 py-3 flex items-center gap-2 text-gray-600">
                      <ClipboardDocumentListIcon className="w-4 h-4 text-gray-400" />
                      {log.modulo}
                    </TableCell>

                    <TableCell className="px-6 py-3 text-right text-gray-500">
                      <div className="flex items-center justify-end gap-2">
                        <ClockIcon className="w-4 h-4 text-gray-400" />
                        {formatarData(log.dataHora)}
                      </div>
                    </TableCell>
                  </TableRow>
                )
              })
            )}
          </TableBody>
        </Table>
      </div>

      {/* Rodapé */}
      <div className="px-6 py-3 border-t bg-gray-50 text-xs text-gray-500 text-right">
        {loading
          ? "Carregando..."
          : `Exibindo ${logs.length} ${logs.length === 1 ? "registro" : "registros"} recentes`}
      </div>
    </div>
  )
}
