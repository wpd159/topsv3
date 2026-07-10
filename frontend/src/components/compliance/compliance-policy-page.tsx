'use client'

import { useEffect, useState } from 'react'

type CompliancePolicyPageProps = {
  title: string
  description: string
  field:
    | 'adultPrivacyPolicy'
    | 'restrictedContentTerms'
    | 'legalAccessNotice'
}

type PoliciesResponse = {
  adultPrivacyPolicy?: string
  restrictedContentTerms?: string
  legalAccessNotice?: string
}

const FALLBACK_POLICIES: Required<PoliciesResponse> = {
  adultPrivacyPolicy:
    'O acesso a conteudo adulto restrito gera registros minimos de seguranca e auditoria, com IP mascarado, hash de user agent e identificador pseudonimo de sessao.',
  restrictedContentTerms:
    'Fotos restritas, videos e stories exigem confirmacao valida de idade. O uso indevido pode gerar bloqueio e revogacao de acesso.',
  legalAccessNotice:
    'Este ambiente contem material sensivel destinado exclusivamente a maiores de 18 anos devidamente verificados.',
}

export function CompliancePolicyPage({
  title,
  description,
  field,
}: CompliancePolicyPageProps) {
  const API = process.env.NEXT_PUBLIC_API_URL
  const [content, setContent] = useState(FALLBACK_POLICIES[field])

  useEffect(() => {
    if (!API) return
    fetch(`${API}/compliance/policies`, { cache: 'no-store', credentials: 'include' })
      .then(async (res) => {
        if (!res.ok) throw new Error()
        return res.json()
      })
      .then((data: PoliciesResponse) => {
        setContent(data?.[field] || FALLBACK_POLICIES[field])
      })
      .catch(() => {
        setContent(FALLBACK_POLICIES[field])
      })
  }, [API, field])

  return (
    <section className="mx-auto max-w-[980px] px-4 py-10">
      <div className="mb-6">
        <h1 className="text-3xl font-bold text-gray-900">{title}</h1>
        <p className="mt-2 text-sm text-gray-500">{description}</p>
      </div>

      <div className="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm">
        <div className="prose prose-sm max-w-none whitespace-pre-line text-gray-700">
          {content}
        </div>
      </div>
    </section>
  )
}

export default CompliancePolicyPage
