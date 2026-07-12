'use client'

import { useParams } from 'next/navigation'
import AnuncioWizard from '@/features/anuncio-wizard/anuncio-wizard'

export default function EditarAnuncioPage() {
  const params = useParams<{ slug: string }>()
  const slug = typeof params?.slug === 'string' ? params.slug : ''
  return <AnuncioWizard mode="edit" slug={slug} />
}
