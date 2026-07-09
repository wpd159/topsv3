import { Suspense } from 'react'
import { ModeracaoV2List } from '@/features/moderation-v2/components/moderacao-v2-list'

export default function ModeracaoV2Page() {
  return (
    <section className="px-1 md:px-0">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Moderação v2</h1>
        <p className="mt-1 text-sm text-gray-600">
          Fila operacional e detalhe focado em decisão.
        </p>
      </div>
      <Suspense fallback={<div className="py-12 text-center text-sm text-gray-500">Carregando lista…</div>}>
        <ModeracaoV2List />
      </Suspense>
    </section>
  )
}
