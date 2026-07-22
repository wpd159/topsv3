import Link from 'next/link'

import { AdminAnuncioModeracao } from '@/features/admin-anuncios/admin-anuncio-moderacao'

export default async function AdminAnuncioDetailPage({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>
  searchParams: Promise<Record<string, string | string[] | undefined>>
}) {
  const { id } = await params
  const values = await searchParams
  const query = new URLSearchParams()
  Object.entries(values).forEach(([name, value]) => {
    if (typeof value === 'string') query.set(name, value)
  })
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

  return <AdminAnuncioModeracao anuncioId={id} initialQuery={query.toString()} />
}
