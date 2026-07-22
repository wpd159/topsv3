import Link from 'next/link'

import { AdminAnuncioModeracao } from '@/features/admin-anuncios/admin-anuncio-moderacao'

export default async function AdminAnuncioDetailPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = await params
  if (!id.trim()) {
    return (
      <div className="py-16 text-center text-gray-500">
        Identificador invalido.{' '}
        <Link href="/admin/anuncios" className="text-[#f0198f] underline">
          Voltar
        </Link>
      </div>
    )
  }

  return <AdminAnuncioModeracao anuncioId={id} />
}
