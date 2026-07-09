'use client'

import { useParams } from 'next/navigation'
import { AnuncioStaffEditForm } from '@/features/moderation-v2/components/anuncio-staff-edit-form'

export default function EditarAnuncioPage() {
  const { id } = useParams() as { id: string }
  const num = Number(id)
  if (!Number.isFinite(num) || num <= 0) {
    return <div className="p-10 text-gray-500">ID inválido.</div>
  }
  return <AnuncioStaffEditForm anuncioId={num} embedded={false} />
}
