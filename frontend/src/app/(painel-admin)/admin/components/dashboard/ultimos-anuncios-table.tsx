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
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  ArrowRightIcon,
  CheckCircleIcon,
  ClockIcon,
  ExclamationCircleIcon,
} from "@heroicons/react/24/solid"

type Anuncio = {
  id: number
  titulo: string
  usernameAnunciante: string
  status: string
  dataCriacao: string
}

export default function UltimosAnunciosPendentesTable() {
  const router = useRouter()
  const [anuncios, setAnuncios] = useState<Anuncio[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    async function fetchAnuncios() {
      try {
        const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/anuncios/ultimos/pendentes`, {
          credentials: "include",
          headers: { "Content-Type": "application/json" },
        })

        if (!res.ok) throw new Error("Falha ao buscar anúncios pendentes")

        const data: Anuncio[] = await res.json()
        setAnuncios(data)
      } catch (err) {
      } finally {
        setLoading(false)
      }
    }

    fetchAnuncios()
  }, [])

  const getBadgeColor = (status: string) => {
    switch (status) {
      case "ATIVO":
        return "bg-green-100 text-green-700 border-green-300"
      case "PENDENTE":
        return "bg-yellow-100 text-yellow-700 border-yellow-300"
      default:
        return "bg-gray-100 text-gray-600 border-gray-300"
    }
  }

  const getStatusIcon = (status: string) => {
    switch (status) {
      case "ATIVO":
        return <CheckCircleIcon className="w-3.5 h-3.5 mr-1" />
      case "PENDENTE":
        return <ClockIcon className="w-3.5 h-3.5 mr-1" />
      default:
        return <ExclamationCircleIcon className="w-3.5 h-3.5 mr-1" />
    }
  }

  const formatarData = (dataISO: string) => {
    const d = new Date(dataISO)
    return d.toLocaleDateString("pt-BR", {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
    })
  }

  return (
    <div className="bg-white border border-gray-100 rounded-xl shadow-sm mt-10 mb-8 overflow-hidden">
      {/* Header */}
      <div className="px-5 py-4 border-b bg-gradient-to-r from-[#FC1EAD]/10 to-transparent">
        <h3 className="text-base font-semibold text-gray-800">
          Últimos Anúncios Pendentes
        </h3>
        <p className="text-xs text-gray-500 mt-1">
          Anúncios aguardando revisão e aprovação no sistema.
        </p>
      </div>

      {/* 🔹 Tabela Desktop */}
      <div className="hidden md:block overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow className="bg-gray-50/60 border-b text-gray-500 uppercase text-[11px] tracking-wider">
              <TableHead className="w-1/3 py-3 px-6 font-semibold">Título</TableHead>
              <TableHead className="py-3 px-6 font-semibold">Usuário</TableHead>
              <TableHead className="py-3 px-6 font-semibold text-center">Status</TableHead>
              <TableHead className="py-3 px-6 font-semibold text-right">Data</TableHead>
              <TableHead className="py-3 px-6 font-semibold text-right">Ações</TableHead>
            </TableRow>
          </TableHeader>

          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={5} className="text-center py-6 text-gray-500">
                  Carregando anúncios...
                </TableCell>
              </TableRow>
            ) : anuncios.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} className="text-center py-6 text-gray-500">
                  Nenhum anúncio pendente encontrado.
                </TableCell>
              </TableRow>
            ) : (
              anuncios.map((a, i) => (
                <TableRow
                  key={a.id}
                  className={`transition-all ${
                    i % 2 === 0 ? "bg-white" : "bg-gray-50/40"
                  } hover:bg-[#FC1EAD]/5`}
                >
                  <TableCell className="py-4 px-6 font-medium text-gray-800">
                    {a.titulo}
                  </TableCell>
                  <TableCell className="px-6 text-gray-600">
                    {a.usernameAnunciante || "—"}
                  </TableCell>
                  <TableCell className="px-6 text-center">
                    <Badge
                      variant="outline"
                      className={`inline-flex items-center justify-center text-[11px] font-medium border px-2 py-1 rounded-md ${getBadgeColor(
                        a.status
                      )}`}
                    >
                      {getStatusIcon(a.status)}
                      {a.status}
                    </Badge>
                  </TableCell>
                  <TableCell className="px-6 text-gray-500 text-right">
                    {formatarData(a.dataCriacao)}
                  </TableCell>
                  <TableCell className="px-6 text-right">
                    <Button
                      size="icon"
                      variant="ghost"
                      className="border border-gray-200 text-gray-500 hover:bg-gray-100 transition-all rounded-md p-2"
                      title="Ver anúncio"
                      onClick={() => router.push(`/admin/moderacao-v2/${a.id}`)}
                    >
                      <ArrowRightIcon className="w-4 h-4" />
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
          : `Exibindo ${anuncios.length} anúncios pendentes`}
      </div>
    </div>
  )
}
