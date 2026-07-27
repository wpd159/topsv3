import { Suspense } from 'react'

import { AdminUsuariosList } from '@/features/admin-usuarios/admin-usuarios-list'

export default function AdminUsersPage() {
  return (
    <Suspense fallback={<p className="py-16 text-center text-sm text-zinc-500">Carregando usuários...</p>}>
      <AdminUsuariosList />
    </Suspense>
  )
}
