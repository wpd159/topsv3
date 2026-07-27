import { AdminUsuarioEditForm } from '@/features/admin-usuarios/admin-usuario-edit-form'

export default async function AdminUserEditPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params
  return <AdminUsuarioEditForm usuarioId={id} />
}
