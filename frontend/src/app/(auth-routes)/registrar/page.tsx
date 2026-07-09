'use client'

import { Suspense } from 'react'
import RegistrarPageInner from './registrar-inner'

export default function RegistrarPage() {
  return (
    <Suspense fallback={null}>
      <RegistrarPageInner />
    </Suspense>
  )
}
