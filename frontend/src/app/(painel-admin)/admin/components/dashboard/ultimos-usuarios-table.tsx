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
  Cog8ToothIcon,
  CheckCircleIcon,
  XCircleIcon,
} from "@heroicons/react/24/solid"

type Usuario = {
  id: number
  username: string
  email: string
  status: string
  criadoEm: string
}

type Props = {
  /** Tabela compacta para o fim do dashboard estratégico (menos destaque visual, hora explícita). */
  variant?: 'default' | 'compact'
}

const COMPACT_LIMIT = 12

export default function UltimosUsuariosTable({ variant = 'default' }: Props) {
  const router = useRouter()
  const [usuarios, setUsuarios] = useState<Usuario[]>([])
  const [loading, setLoading] = useState(true)
  const [processing, setProcessing] = useState<number | null>(null)

  const API_URL = process.env.NEXT_PUBLIC_API_URL

  useEffect(() => {
    async function fetchUsuarios() {
      try {
        const res = await fetch(`${API_URL}/usuarios/ultimos`, {
          credentials: "include",
          headers: { "Content-Type": "application/json" },
        })
        if (!res.ok) throw new Error("Falha ao buscar usuários")
        const data: Usuario[] = await res.json()
        setUsuarios(variant === 'compact' ? data.slice(0, COMPACT_LIMIT) : data)
      } catch (err) {
      } finally {
        setLoading(false)
      }
    }
    fetchUsuarios()
  }, [API_URL, variant])

  const alternarStatus = async (usuario: Usuario) => {
    const novoStatus = usuario.status === "ATIVO" ? "INATIVO" : "ATIVO"
    const rota =
      usuario.status === "ATIVO"
        ? `${API_URL}/usuarios/${usuario.id}/inativar`
        : `${API_URL}/usuarios/${usuario.id}/ativar`

    try {
      setProcessing(usuario.id)
      const res = await fetch(rota, {
        method: "PUT",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
      })
      if (!res.ok) throw new Error("Erro ao alterar status do usuário")

      setUsuarios((prev) =>
        prev.map((u) =>
          u.id === usuario.id ? { ...u, status: novoStatus } : u
        )
      )
    } catch (err) {
      alert("Erro ao alterar status do usuário.")
    } finally {
      setProcessing(null)
    }
  }

  const irParaGerenciar = (id: number) => {
    router.push(`/admin/usuarios/${id}`)
  }

  const formatarData = (dataIso: string) => {
    const d = new Date(dataIso)
    return d.toLocaleDateString("pt-BR", {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
    })
  }

  const formatarHora = (dataIso: string) => {
    const d = new Date(dataIso)
    if (Number.isNaN(d.getTime())) return "—"
    return d.toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit", second: "2-digit" })
  }

  const compact = variant === "compact"

  return (
    <div
      className={[
        "bg-white border border-gray-200/80 rounded-2xl shadow-sm overflow-hidden",
        compact ? "" : "mt-10",
      ].join(" ")}
    >
      {/* Header */}
      <div
        className={[
          "px-5 py-4 border-b",
          compact ? "bg-gray-50/80" : "bg-gradient-to-r from-[#FC1EAD]/10 to-transparent",
        ].join(" ")}
      >
        <h3 className="text-base font-semibold text-gray-800">
          Últimos usuários cadastrados
        </h3>
        <p className="text-xs text-gray-500 mt-1">
          Ordene por mais recentes — útil para validar cadastros automáticos (ex.: bot).
          {compact ? ` Até ${COMPACT_LIMIT} registros.` : ""}
        </p>
      </div>

      {/* Tabela */}
      <div className="hidden md:block overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow className="bg-gray-50/60 border-b text-gray-500 uppercase text-[11px] tracking-wider">
              <TableHead className="w-1/4 py-3 px-6 font-semibold">Nome</TableHead>
              <TableHead className="py-3 px-6 font-semibold">Email</TableHead>
              <TableHead className="py-3 px-6 font-semibold text-center">Status</TableHead>
              <TableHead className="py-3 px-6 font-semibold text-right">Data</TableHead>
              {compact ? (
                <TableHead className="py-3 px-6 font-semibold text-right">Hora</TableHead>
              ) : null}
              <TableHead className="py-3 px-6 font-semibold text-right">Ações</TableHead>
            </TableRow>
          </TableHeader>

          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={compact ? 6 : 5} className="text-center py-6 text-gray-500">
                  Carregando usuários...
                </TableCell>
              </TableRow>
            ) : usuarios.length === 0 ? (
              <TableRow>
                <TableCell colSpan={compact ? 6 : 5} className="text-center py-6 text-gray-500">
                  Nenhum usuário encontrado.
                </TableCell>
              </TableRow>
            ) : (
              usuarios.map((u, i) => (
                <TableRow
                  key={u.id}
                  className={`transition-all ${
                    i % 2 === 0 ? "bg-white" : "bg-gray-50/40"
                  } hover:bg-[#FC1EAD]/5`}
                >
                  <TableCell className="py-4 px-6 font-medium text-gray-800">
                    {u.username || "—"}
                  </TableCell>
                  <TableCell className="px-6 text-gray-600">{u.email}</TableCell>
                  <TableCell className="px-6 text-center">
                    <Badge
                      variant="outline"
                      className={`inline-flex items-center justify-center text-[11px] font-medium px-2 py-1 rounded-md border ${
                        u.status === "ATIVO"
                          ? "bg-green-100 text-green-700 border-green-300"
                          : "bg-red-100 text-red-700 border-red-300"
                      }`}
                    >
                      {u.status === "ATIVO" ? (
                        <CheckCircleIcon className="w-3.5 h-3.5 mr-1" />
                      ) : (
                        <XCircleIcon className="w-3.5 h-3.5 mr-1" />
                      )}
                      {u.status === "ATIVO" ? "Ativo" : "Inativo"}
                    </Badge>
                  </TableCell>
                  <TableCell className="px-6 text-gray-500 text-right">
                    {formatarData(u.criadoEm)}
                  </TableCell>
                  {compact ? (
                    <TableCell className="px-6 text-gray-600 text-right font-mono text-xs">
                      {formatarHora(u.criadoEm)}
                    </TableCell>
                  ) : null}
                  <TableCell className="px-6 text-right flex items-center justify-end gap-2">
                    <Button
                      size="icon"
                      variant="ghost"
                      disabled={processing === u.id}
                      onClick={() => alternarStatus(u)}
                      className={`transition-all border rounded-md p-2 hover:scale-105 ${
                        u.status === "ATIVO"
                          ? "border-gray-200 text-gray-500 hover:bg-gray-100"
                          : "border-gray-200 text-gray-400 hover:bg-gray-100"
                      }`}
                      title={
                        u.status === "ATIVO"
                          ? "Inativar usuário"
                          : "Ativar usuário"
                      }
                    >
                      <PowerIcon
                        className={`w-4 h-4 ${
                          processing === u.id ? "animate-spin text-gray-400" : ""
                        }`}
                      />
                    </Button>

                    <Button
                      size="sm"
                      variant="outline"
                      className="text-[#C41E73] border-[#C41E73]/40 hover:bg-[#FC1EAD]/10 transition-all font-medium text-[13px]"
                      onClick={() => irParaGerenciar(u.id)}
                    >
                      <Cog8ToothIcon className="w-4 h-4 mr-1" />
                      Gerenciar
                    </Button>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {/* Rodapé */}
      <div className="px-6 py-3 border-t bg-gray-50/70 text-xs text-gray-500 text-right">
        {loading
          ? "Carregando..."
          : `Exibindo ${usuarios.length} usuários recentes`}
      </div>
    </div>
  )
}
