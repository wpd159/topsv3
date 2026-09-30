import { Suspense } from 'react'
import { AdminRegistroDetail } from '@/features/admin-registros/admin-registro-detail'

export default function AdminRegistroDetailPage() {
  return <Suspense fallback={<p role="status">Carregando detalhe privado...</p>}><AdminRegistroDetail /></Suspense>
}
