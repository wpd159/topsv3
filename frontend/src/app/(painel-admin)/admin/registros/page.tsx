import { Suspense } from 'react'
import { AdminRegistrosList } from '@/features/admin-registros/admin-registros-list'

export default function AdminRegistrosPage() {
  return <Suspense fallback={<p role="status">Carregando consulta de registros...</p>}><AdminRegistrosList /></Suspense>
}
