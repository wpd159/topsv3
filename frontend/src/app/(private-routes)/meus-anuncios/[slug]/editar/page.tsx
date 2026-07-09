'use client'

import { useParams } from 'next/navigation'
import EditarAnuncioWizard from '@/features/anuncio-wizard/editar-anuncio-wizard'

export default function EditarAnuncioPage() {
  const params = useParams<{ slug: string }>()

  const slug =
    typeof params?.slug === 'string'
      ? params.slug
      : Array.isArray(params?.slug)
        ? params.slug[0]
        : ''

  return <EditarAnuncioWizard slug={slug} />
}
