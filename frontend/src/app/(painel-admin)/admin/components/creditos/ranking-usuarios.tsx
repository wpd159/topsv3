'use client'

import { useEffect, useState } from 'react'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { TrophyIcon } from '@heroicons/react/24/solid'

export default function RankingUsuarios() {
  const [ranking, setRanking] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const API_URL = process.env.NEXT_PUBLIC_API_URL

  useEffect(() => {
    const fetchRanking = async () => {
      try {
        const res = await fetch(`${API_URL}/creditos/ranking`, {
          credentials: 'include',
        })
        if (!res.ok) throw new Error('Erro ao carregar ranking')
        const data = await res.json()
        setRanking(data)
      } catch (error) {
      } finally {
        setLoading(false)
      }
    }

    fetchRanking()
  }, [])

  const getBadgeColor = (pos: number) => {
    switch (pos) {
      case 1:
        return 'bg-yellow-100 text-yellow-700 border-yellow-300'
      case 2:
        return 'bg-gray-200 text-gray-700 border-gray-300'
      case 3:
        return 'bg-amber-200 text-amber-800 border-amber-300'
      default:
        return 'bg-gray-100 text-gray-600 border-gray-300'
    }
  }

  if (loading)
    return <p className="text-gray-500 mt-4">Carregando ranking...</p>

  return (
    <Card className="bg-white border border-gray-100 rounded-xl shadow-sm mt-10 overflow-hidden">
      <div className="px-5 py-4 border-b bg-gradient-to-r from-[#FC1EAD]/10 to-transparent flex items-center gap-2">
        <TrophyIcon className="w-5 h-5 text-[#FC1EAD]" />
        <div>
          <h3 className="text-base font-semibold text-gray-800">
            Top usuários com mais créditos
          </h3>
          <p className="text-xs text-gray-500 mt-1">
            Nome do usuário e saldo atual de créditos.
          </p>
        </div>
      </div>

      <div className="overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow className="bg-gray-50/60 border-b text-gray-500 uppercase text-[11px] tracking-wider">
              <TableHead className="py-3 px-6 font-semibold w-[90px] text-left">
                #
              </TableHead>
              <TableHead className="py-3 px-6 font-semibold">Usuário</TableHead>
              <TableHead className="py-3 px-6 font-semibold text-right">
                Créditos
              </TableHead>
            </TableRow>
          </TableHeader>

          <TableBody>
            {ranking.map((u, i) => (
              <TableRow key={u.usuarioId}>
                <TableCell>
                  <Badge
                    variant="outline"
                    className={`font-medium text-[11px] border px-2 py-1 rounded-md ${getBadgeColor(
                      i + 1
                    )}`}
                  >
                    #{i + 1}
                  </Badge>
                </TableCell>
                <TableCell>{u.nome}</TableCell>
                <TableCell className="text-right font-semibold text-[#C41E73]">
                  {u.saldo}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </Card>
  )
}
