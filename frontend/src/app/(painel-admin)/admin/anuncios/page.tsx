import { AdminAnunciosList } from '@/features/admin-anuncios/admin-anuncios-list'

export default async function AdminAnunciosPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>
}) {
  const values = await searchParams
  const query = new URLSearchParams()
  Object.entries(values).forEach(([name, value]) => {
    if (typeof value === 'string') query.set(name, value)
  })
  return <AdminAnunciosList initialQuery={query.toString()} />
}
