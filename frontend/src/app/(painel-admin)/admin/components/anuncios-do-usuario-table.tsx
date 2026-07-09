'use client'

import { useMemo, useState } from 'react'
import { useRouter } from 'next/navigation'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { CheckCircleIcon, EyeIcon, XCircleIcon } from '@heroicons/react/24/solid'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { formatarCodigoBeneficio } from './admin-usuarios-utils'

type Anuncio = {
  id: number
  titulo: string
  data: string
  status: string
  beneficiosAtivosCodigos?: string[] | null
}

interface AnunciosDoUsuarioTableProps {
  anuncios: Anuncio[]
  statusFilter?: string | null
  beneficioFilter?: string | null
  onClearQuickFilter?: () => void
}

function normalizeStatus(status: string) {
  return (status || '').trim().toUpperCase()
}

function formatStatusLabel(status: string) {
  const normalized = normalizeStatus(status)
  const labels: Record<string, string> = {
    ATIVO: 'Ativo',
    PENDENTE: 'Pendente',
    BLOQUEADO: 'Bloqueado',
    INATIVO: 'Inativo',
    REJEITADO: 'Rejeitado',
    PAUSADO: 'Pausado',
  }

  return labels[normalized] || status || '—'
}

function getBadgeStyle(status: string) {
  switch (normalizeStatus(status)) {
    case 'ATIVO':
      return 'bg-green-100 text-green-700 border-green-300'
    case 'PENDENTE':
      return 'bg-yellow-100 text-yellow-700 border-yellow-300'
    case 'BLOQUEADO':
    case 'INATIVO':
    case 'REJEITADO':
    case 'PAUSADO':
      return 'bg-red-100 text-red-700 border-red-300'
    default:
      return 'bg-gray-100 text-gray-600 border-gray-300'
  }
}

export default function AnunciosDoUsuarioTable({
  anuncios,
  statusFilter = null,
  beneficioFilter = null,
  onClearQuickFilter,
}: AnunciosDoUsuarioTableProps) {
  const router = useRouter()
  const [busca, setBusca] = useState('')

  const handleVerAnuncio = (id: number) => {
    router.push(`/admin/moderacao-v2/${id}`)
  }

  const anunciosFiltrados = useMemo(() => {
    const termo = busca.trim().toLowerCase()

    return anuncios.filter((anuncio) => {
      const matchBusca = !termo || (anuncio.titulo || '').toLowerCase().includes(termo)
      const matchStatus = !statusFilter || normalizeStatus(anuncio.status) === normalizeStatus(statusFilter)
      const beneficios = anuncio.beneficiosAtivosCodigos || []
      const matchBeneficio = !beneficioFilter || beneficios.includes(beneficioFilter)
      return matchBusca && matchStatus && matchBeneficio
    })
  }, [anuncios, beneficioFilter, busca, statusFilter])

  const quickFiltersAtivos = Boolean(statusFilter || beneficioFilter)

  return (
    <div className="mt-10 overflow-hidden rounded-xl border border-gray-100 bg-white shadow-sm">
      <div className="flex flex-col gap-3 border-b bg-gradient-to-r from-[#FC1EAD]/10 to-transparent px-5 py-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h3 className="text-base font-semibold text-gray-800">Anúncios do usuário</h3>
            <p className="mt-1 text-xs text-gray-500">
              Listagem dos anúncios vinculados a este usuário, com filtro rápido por status e benefício.
            </p>
          </div>

          <Input
            value={busca}
            onChange={(event) => setBusca(event.target.value)}
            placeholder="Buscar anúncio..."
            className="w-full sm:max-w-xs"
          />
        </div>

        {quickFiltersAtivos ? (
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-xs font-medium uppercase tracking-wide text-gray-500">Filtro rápido:</span>
            {statusFilter ? (
              <Badge variant="outline" className="border-amber-300 bg-amber-50 text-amber-700">
                Status: {formatStatusLabel(statusFilter)}
              </Badge>
            ) : null}
            {beneficioFilter ? (
              <Badge variant="outline" className="border-emerald-300 bg-emerald-50 text-emerald-700">
                Benefício: {formatarCodigoBeneficio(beneficioFilter)}
              </Badge>
            ) : null}
            <Button variant="ghost" size="sm" className="h-7 px-2 text-xs" onClick={onClearQuickFilter}>
              Limpar filtro
            </Button>
          </div>
        ) : null}
      </div>

      <div className="hidden overflow-x-auto md:block">
        <Table>
          <TableHeader>
            <TableRow className="border-b bg-gray-50/60 text-[11px] uppercase tracking-wider text-gray-500">
              <TableHead className="w-[38%] px-6 py-3 font-semibold">Título</TableHead>
              <TableHead className="px-6 py-3 text-center font-semibold">Status</TableHead>
              <TableHead className="px-6 py-3 font-semibold">Benefícios</TableHead>
              <TableHead className="px-6 py-3 text-right font-semibold">Data</TableHead>
              <TableHead className="px-6 py-3 text-right font-semibold">Ações</TableHead>
            </TableRow>
          </TableHeader>

          <TableBody>
            {anunciosFiltrados.map((anuncio, index) => {
              const normalizedStatus = normalizeStatus(anuncio.status)
              const beneficios = anuncio.beneficiosAtivosCodigos || []

              return (
                <TableRow
                  key={anuncio.id}
                  className={`${index % 2 === 0 ? 'bg-white' : 'bg-gray-50/40'} transition-all hover:bg-[#FC1EAD]/5`}
                >
                  <TableCell className="px-6 py-4">
                    <button
                      type="button"
                      className="font-medium text-gray-800 hover:text-[#C41E73]"
                      onClick={() => handleVerAnuncio(anuncio.id)}
                    >
                      {anuncio.titulo}
                    </button>
                  </TableCell>

                  <TableCell className="px-6 text-center">
                    <Badge
                      variant="outline"
                      className={`inline-flex items-center justify-center rounded-md border px-2 py-1 text-[11px] font-medium ${getBadgeStyle(anuncio.status)}`}
                    >
                      {normalizedStatus === 'ATIVO' ? (
                        <CheckCircleIcon className="mr-1 h-3.5 w-3.5" />
                      ) : (
                        <XCircleIcon
                          className={`mr-1 h-3.5 w-3.5 ${
                            normalizedStatus === 'PENDENTE' ? 'text-yellow-600' : 'text-red-600'
                          }`}
                        />
                      )}
                      {formatStatusLabel(anuncio.status)}
                    </Badge>
                  </TableCell>

                  <TableCell className="px-6">
                    {beneficios.length > 0 ? (
                      <div className="flex flex-wrap gap-1.5">
                        {beneficios.map((codigo) => (
                          <Badge
                            key={`${anuncio.id}-${codigo}`}
                            variant="outline"
                            className="border-emerald-300 bg-emerald-50 text-[10px] font-medium text-emerald-700"
                          >
                            {formatarCodigoBeneficio(codigo)}
                          </Badge>
                        ))}
                      </div>
                    ) : (
                      <span className="text-xs text-gray-400">Sem benefício ativo</span>
                    )}
                  </TableCell>

                  <TableCell className="px-6 text-right text-gray-500">{anuncio.data}</TableCell>

                  <TableCell className="px-6 text-right">
                    <Button
                      size="sm"
                      variant="outline"
                      className="border-[#C41E73]/40 text-[13px] font-medium text-[#C41E73] transition-all hover:bg-[#FC1EAD]/10"
                      onClick={() => handleVerAnuncio(anuncio.id)}
                    >
                      <EyeIcon className="mr-1 h-4 w-4" />
                      Ver
                    </Button>
                  </TableCell>
                </TableRow>
              )
            })}

            {anunciosFiltrados.length === 0 && (
              <TableRow>
                <TableCell colSpan={5} className="py-6 text-center text-gray-500">
                  Nenhum anúncio encontrado para o filtro atual.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      <div className="block divide-y divide-gray-200 md:hidden">
        {anunciosFiltrados.map((anuncio) => {
          const normalizedStatus = normalizeStatus(anuncio.status)
          const beneficios = anuncio.beneficiosAtivosCodigos || []

          return (
            <div
              key={anuncio.id}
              className="flex flex-col gap-3 bg-white p-4 transition hover:bg-[#FC1EAD]/5"
            >
              <div className="flex items-center justify-between gap-3">
                <button
                  type="button"
                  className="text-left text-sm font-semibold text-gray-800 hover:text-[#C41E73]"
                  onClick={() => handleVerAnuncio(anuncio.id)}
                >
                  {anuncio.titulo}
                </button>
                <Badge
                  className={`inline-flex items-center rounded-md border px-2 py-1 text-[11px] font-medium ${getBadgeStyle(anuncio.status)}`}
                >
                  {normalizedStatus === 'ATIVO' ? (
                    <CheckCircleIcon className="mr-1 h-3.5 w-3.5" />
                  ) : (
                    <XCircleIcon
                      className={`mr-1 h-3.5 w-3.5 ${
                        normalizedStatus === 'PENDENTE' ? 'text-yellow-600' : 'text-red-600'
                      }`}
                    />
                  )}
                  {formatStatusLabel(anuncio.status)}
                </Badge>
              </div>

              <div className="flex flex-wrap gap-1.5">
                {beneficios.length > 0 ? (
                  beneficios.map((codigo) => (
                    <Badge
                      key={`${anuncio.id}-${codigo}`}
                      variant="outline"
                      className="border-emerald-300 bg-emerald-50 text-[10px] font-medium text-emerald-700"
                    >
                      {formatarCodigoBeneficio(codigo)}
                    </Badge>
                  ))
                ) : (
                  <span className="text-xs text-gray-400">Sem benefício ativo</span>
                )}
              </div>

              <div className="mt-1 flex items-center justify-between">
                <span className="text-xs text-gray-500">{anuncio.data}</span>

                <Button
                  size="icon"
                  variant="outline"
                  className="border-[#C41E73]/40 text-[#C41E73] transition-all hover:bg-[#FC1EAD]/10"
                  onClick={() => handleVerAnuncio(anuncio.id)}
                  title="Ver anúncio"
                >
                  <EyeIcon className="h-4 w-4" />
                </Button>
              </div>
            </div>
          )
        })}

        {anunciosFiltrados.length === 0 && (
          <div className="p-6 text-center text-sm text-gray-500">
            Nenhum anúncio encontrado para o filtro atual.
          </div>
        )}
      </div>
    </div>
  )
}
