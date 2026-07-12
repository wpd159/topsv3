'use client'

import { useParams } from 'next/navigation'
import { MeuAnuncioEditor } from '@/components/anuncios/meu-anuncio-editor'

export default function EditarAnuncioPage() {
  const params = useParams<{ slug: string }>()
  const slug = typeof params?.slug === 'string' ? params.slug : ''
  return <MeuAnuncioEditor slug={slug} />
}
