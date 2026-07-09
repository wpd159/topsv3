'use client'

import { useParams } from 'next/navigation'
import MonetizacaoWizard from '@/features/monetizacao-wizard/monetizacao-wizard'

export default function MonetizacaoWizardPage() {
  const params = useParams<{ slug: string }>()

  const slug =
    typeof params?.slug === 'string'
      ? params.slug
      : Array.isArray(params?.slug)
        ? params.slug[0]
        : ''

  return <MonetizacaoWizard slug={slug} />
}
