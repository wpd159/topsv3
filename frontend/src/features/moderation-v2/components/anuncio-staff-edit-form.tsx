'use client'

import { useRouter } from 'next/navigation'

import { AdminAnuncioDadosInlineEditor } from '@/app/(painel-admin)/admin/components/anuncios/admin-anuncio-dados-inline-editor'

type AnuncioStaffEditFormProps = {
  anuncioId: string | number
  embedded?: boolean
  onSaved?: () => void
}

export function AnuncioStaffEditForm({ anuncioId, embedded = false, onSaved }: AnuncioStaffEditFormProps) {
  const router = useRouter()

  return (
    <section className={embedded ? '' : 'mx-auto max-w-4xl py-8'}>
      <AdminAnuncioDadosInlineEditor
        anuncioId={String(anuncioId)}
        open
        onCancel={() => {
          if (embedded) return
          router.push(`/admin/moderacao-v2/${encodeURIComponent(String(anuncioId))}`)
        }}
        onSaved={onSaved || (() => undefined)}
      />
    </section>
  )
}
