import { AdminAnuncioEditForm } from '@/features/admin-anuncios/admin-anuncio-edit-form'

export default async function EditarAnuncioPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params
  return <AdminAnuncioEditForm anuncioId={id} />
}
