import Link from 'next/link'
import { ModeracaoV2Detail } from '@/features/moderation-v2/components/moderacao-v2-detail'

export default async function ModeracaoV2DetalhePage({
  params,
}: {
  params: Promise<{ anuncioId: string }>
}) {
  const { anuncioId } = await params
  if (!anuncioId.trim()) {
    return (
      <div className="py-16 text-center text-gray-500">
        ID inválido. <Link href="/admin/moderacao-v2" className="text-[#f0198f] underline">Voltar</Link>
      </div>
    )
  }
  return <ModeracaoV2Detail anuncioId={anuncioId} />
}
