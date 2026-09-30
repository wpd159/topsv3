import { Suspense } from 'react'
import { AdminRegistroReport } from '@/features/admin-registros/admin-registro-report'

export default function AdminRegistroReportPage() {
  return <Suspense fallback={<p role="status">Carregando escopo do relatório...</p>}><AdminRegistroReport /></Suspense>
}
