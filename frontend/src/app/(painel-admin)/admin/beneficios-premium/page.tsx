import { redirect } from 'next/navigation'
import { anuncioIdLegadoSeguro } from '@/lib/admin-monetizacao-navigation'

type LegacySearchParams = Promise<Record<string, string | string[] | undefined>>

export default async function AdminBeneficiosPremiumPage({
  searchParams,
}: {
  searchParams: LegacySearchParams
}) {
  const params = await searchParams
  const destino = new URLSearchParams({ aba: 'beneficios' })
  const anuncioId = anuncioIdLegadoSeguro(params.anuncioId)
  if (anuncioId) destino.set('anuncioId', anuncioId)
  redirect(`/admin/creditos?${destino.toString()}`)
}