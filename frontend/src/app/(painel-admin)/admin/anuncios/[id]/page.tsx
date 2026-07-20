import Link from 'next/link'

import { ModeracaoV2Detail } from '@/features/moderation-v2/components/moderacao-v2-detail'

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
        <Link href="/admin/moderacao-v2" className="text-[#f0198f] underline">
          Voltar
        </Link>
      </div>
    )
  }

  return <ModeracaoV2Detail anuncioId={id} />
}
