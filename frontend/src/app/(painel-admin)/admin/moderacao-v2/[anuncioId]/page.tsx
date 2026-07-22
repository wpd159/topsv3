import { redirect } from 'next/navigation'

export default async function ModeracaoV2DetalhePage({
  params,
}: {
  params: Promise<{ anuncioId: string }>
}) {
  const { anuncioId } = await params
  redirect(anuncioId.trim() ? `/admin/anuncios/${encodeURIComponent(anuncioId)}` : '/admin/anuncios')
}
