import { redirect } from 'next/navigation'

import { ADMIN_DASHBOARD_PATH } from '@/lib/admin-navigation'

export default function AdminRootPage() {
  redirect(ADMIN_DASHBOARD_PATH)
}
