import { Suspense } from 'react'

import { AdminUsuarioDetail } from '@/features/admin-usuarios/admin-usuario-detail'

export default function AdminUserDetailPage() {
  return (
    <Suspense fallback={<p className="py-16 text-center text-sm text-zinc-500">Carregando usuário...</p>}>
      <AdminUsuarioDetail />
    </Suspense>
  )
}
