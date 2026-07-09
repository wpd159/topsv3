'use client'

import { useParams, useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  ArrowLeftIcon,
  PencilSquareIcon,
  XCircleIcon,
  CheckCircleIcon,
  UserIcon,
  EnvelopeIcon,
  IdentificationIcon,
  BriefcaseIcon,
  CalendarIcon,
  AtSymbolIcon,
} from '@heroicons/react/24/solid'
import { Card } from '@/components/ui/card'
import { toast } from 'sonner'
import { normalizarStaff } from '@/utils/normalizer'
import { formatCPF } from '@/utils/formatter'

export default function DetalhesStaffPage() {
  const { id } = useParams()
  const router = useRouter()
  const [loading, setLoading] = useState(true)

  const [staff, setStaff] = useState<any | null>(null)

  // 🔹 Buscar staff por ID
  useEffect(() => {
    const fetchStaff = async () => {
      try {
        const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/staff/${id}`, {
          credentials: 'include',
        })

        if (!res.ok) {
          throw new Error('Falha ao buscar informações do staff.')
        }

        const data = await res.json()
        setStaff(data)
      } catch (err: any) {
        toast.error(err.message || 'Erro ao carregar informações do staff.')
      } finally {
        setLoading(false)
      }
    }

    if (id) fetchStaff()
  }, [id])

  const getBadgeColor = (status: string) => {
    switch (status?.toUpperCase()) {
      case 'ATIVO':
        return 'bg-green-100 text-green-700 border-green-300'
      case 'INATIVO':
        return 'bg-gray-100 text-gray-600 border-gray-300'
      default:
        return 'bg-gray-50 text-gray-700 border-gray-200'
    }
  }

  const alternarStatus = async () => {
    if (!staff) return
    try {
      const rota =
        staff.status === 'ATIVO'
          ? `${process.env.NEXT_PUBLIC_API_URL}/staff/${id}/inativar`
          : `${process.env.NEXT_PUBLIC_API_URL}/staff/${id}/ativar`

      const res = await fetch(rota, {
        method: 'PUT',
        credentials: 'include',
      })

      if (!res.ok) throw new Error('Falha ao alterar status.')

      const atualizado = await res.json()
      setStaff(atualizado)
      toast.success(
        atualizado.status === 'ATIVO'
          ? 'Staff reativado com sucesso.'
          : 'Staff inativado com sucesso.'
      )
    } catch (err: any) {
      toast.error(err.message || 'Erro ao atualizar status.')
    }
  }

  if (loading)
    return (
      <div className="flex items-center justify-center py-20 text-gray-500">
        Carregando informações...
      </div>
    )

  if (!staff)
    return (
      <div className="flex flex-col items-center justify-center py-20 text-gray-600">
        <p className="text-lg font-medium">Staff não encontrado.</p>
        <Button
          className="mt-4"
          onClick={() => router.push('/admin/staff')}
        >
          Voltar
        </Button>
      </div>
    )

  return (
    <section className="pb-10">
      {/* Cabeçalho */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between mb-8 gap-4">
        <div className="flex items-center gap-3">
          <Button
            variant="ghost"
            size="icon"
            onClick={() => router.push('/admin/staff')}
            className="text-gray-600 hover:bg-gray-100"
          >
            <ArrowLeftIcon className="w-5 h-5" />
          </Button>

          <div>
            <h1 className="text-2xl font-bold text-gray-800">Detalhes do Staff</h1>
            <p className="text-sm text-gray-500">
              Informações completas do membro da equipe #{id}.
            </p>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <Button
            variant="outline"
            onClick={() => router.push(`/admin/staff/${id}/editar`)}
            className="flex items-center gap-1 text-[#C41E73] border-[#C41E73]/40 hover:bg-[#FC1EAD]/10"
          >
            <PencilSquareIcon className="w-4 h-4" />
            Editar
          </Button>

          <Button
            variant="outline"
            onClick={alternarStatus}
            className={`flex items-center gap-1 ${
              staff.status === 'ATIVO'
                ? 'border-red-300 text-red-600 hover:bg-red-50'
                : 'border-green-300 text-green-600 hover:bg-green-50'
            }`}
          >
            {staff.status === 'ATIVO' ? (
              <>
                <XCircleIcon className="w-4 h-4" />
                Inativar
              </>
            ) : (
              <>
                <CheckCircleIcon className="w-4 h-4" />
                Reativar
              </>
            )}
          </Button>
        </div>
      </div>

      {/* Informações gerais */}
      <Card className="p-6 border border-gray-100 shadow-sm rounded-xl">
        <h2 className="text-lg font-semibold text-gray-800 mb-6 flex items-center gap-2">
          <UserIcon className="w-5 h-5 text-[#C41E73]" />
          Informações do Staff
        </h2>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-x-10 gap-y-4 text-sm">
          <p className="flex items-center gap-2">
            <UserIcon className="w-4 h-4 text-[#C41E73]" />
            <span className="font-semibold text-gray-700">Nome:</span>{' '}
            {staff.nomeCompleto}
          </p>

          <p className="flex items-center gap-2">
            <AtSymbolIcon className="w-4 h-4 text-[#C41E73]" />
            <span className="font-semibold text-gray-700">Nome de usuário:</span>{' '}
            {staff.username}
          </p>

          <p className="flex items-center gap-2">
            <EnvelopeIcon className="w-4 h-4 text-[#C41E73]" />
            <span className="font-semibold text-gray-700">E-mail:</span>{' '}
            {staff.email}
          </p>

          <p className="flex items-center gap-2">
            <IdentificationIcon className="w-4 h-4 text-[#C41E73]" />
            <span className="font-semibold text-gray-700">CPF:</span>{' '}
            {formatCPF(staff.cpf)}
          </p>

          <p className="flex items-center gap-2">
            <BriefcaseIcon className="w-4 h-4 text-[#C41E73]" />
            <span className="font-semibold text-gray-700">Cargo:</span>{' '}
            {normalizarStaff(staff.cargo)}
          </p>

          <p className="flex items-center gap-2">
            <CalendarIcon className="w-4 h-4 text-[#C41E73]" />
            <span className="font-semibold text-gray-700">Data de criação:</span>{' '}
            {staff.dataCriacao || '—'}
          </p>

          <p className="flex items-center gap-2">
            <CalendarIcon className="w-4 h-4 text-[#C41E73]" />
            <span className="font-semibold text-gray-700">Data Nascimento:</span>{' '}
            {staff.dataNascimento || '—'}
          </p>

          <div className="flex items-center gap-2">
            <span className="font-semibold text-gray-700">Status:</span>
            <Badge
              className={`text-[11px] font-medium border px-2 py-1 rounded-md ${getBadgeColor(
                staff.status
              )}`}
            >
              {staff.status}
            </Badge>
          </div>
        </div>
      </Card>
    </section>
  )
}
