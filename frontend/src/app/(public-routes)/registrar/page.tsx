import { redirect } from 'next/navigation'

type RegistrarPageProps = {
  searchParams: Promise<Record<string, string | string[] | undefined>>
}

function firstValue(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value
}

export default async function RegistrarPage({ searchParams }: RegistrarPageProps) {
  const source = await searchParams
  const target = new URLSearchParams({ register: '1' })
  const next = firstValue(source.next)
  const ref = firstValue(source.ref)

  if (next?.startsWith('/') && !next.startsWith('//')) target.set('next', next)
  if (ref && /^\d+$/.test(ref)) target.set('ref', ref)

  redirect(`/?${target.toString()}`)
}
