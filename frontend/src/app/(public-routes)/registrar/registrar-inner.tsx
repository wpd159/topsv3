"use client"

import { useSearchParams, useRouter } from "next/navigation"
import { RegisterFormSteps } from "@/components/auth/register-form-steps"

function getSafeNext(value: string | null) {
  if (!value) return null
  if (!value.startsWith("/") || value.startsWith("//")) return null
  return value
}

export default function RegistrarPageInner() {
  const params = useSearchParams()
  const router = useRouter()

  const ref = params.get("ref")
  const next = getSafeNext(params.get("next"))

  const handleSuccess = () => {
    router.push(next || "/")
  }

  return (
    <section data-register-page className="min-h-screen bg-white px-4 py-6 sm:px-6 sm:py-10">
      <div className="mx-auto w-full max-w-lg">
        <RegisterFormSteps
          refId={ref ? Number(ref) : null}
          submitSource="CADASTRO_PAGINA"
          onSuccess={handleSuccess}
        />
      </div>
    </section>
  )
}
