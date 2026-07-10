import { Suspense } from 'react'
import { RegisterPageForm } from '@/features/auth/register/register-page-form'

export default function RegistrarPage() {
  return (
    <Suspense fallback={null}>
      <RegisterPageForm />
    </Suspense>
  )
}
