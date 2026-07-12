'use client'

import { useParams } from 'next/navigation'
import { MeuAnuncioDetalheView } from '@/components/anuncios/meu-anuncio-detalhe-view'

export default function MeuAnuncioDetalhePage() {
  const params = useParams<{ slug: string }>()
  const slug = typeof params?.slug === 'string' ? params.slug : ''
  return <MeuAnuncioDetalheView slug={slug} />
}
