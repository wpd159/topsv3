import { AdminStoriesManagement } from '@/components/stories/admin-stories-management'

export default function AdminStoriesPage() {
  return (
    <section className="mx-auto w-full max-w-6xl space-y-6">
      <header>
        <h1 className="text-2xl font-bold text-gray-900">Gestão de Stories</h1>
        <p className="mt-1 text-sm text-gray-600">
          Consulte as publicações das anunciantes e remova um Story somente com motivo administrativo.
        </p>
      </header>
      <AdminStoriesManagement />
    </section>
  )
}
