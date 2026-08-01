'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { ArrowRight, Lightbulb, RefreshCw } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { ApiContractError, normalizeApiError } from '@/lib/api-contract'
import {
  fetchRecomendacoesAnunciante,
  type RecomendacoesAnunciante,
} from '@/lib/recomendacoes-api'

export function RecommendationsPanel() {
  const router = useRouter()
  const [dados, setDados] = useState<RecomendacoesAnunciante | null>(null)
  const [erro, setErro] = useState<ApiContractError | null>(null)
  const [tentativa, setTentativa] = useState(0)
  const [carregando, setCarregando] = useState(true)

  useEffect(() => {
    const controller = new AbortController()
    setCarregando(true)
    setErro(null)
    fetchRecomendacoesAnunciante(controller.signal)
      .then(setDados)
      .catch((cause) => {
        if (controller.signal.aborted) return
        const normalized = normalizeApiError(cause)
        setDados(null)
        setErro(normalized)
        if (normalized.kind === 'SESSION_REQUIRED') {
          router.replace(`/?login=1&next=${encodeURIComponent('/painel#recomendacoes')}`)
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setCarregando(false)
      })
    return () => controller.abort()
  }, [router, tentativa])

  if (carregando) {
    return (
      <div role="status" aria-live="polite" className="rounded-lg border border-slate-200 bg-white p-5 text-sm text-slate-600">
        Carregando recomendacoes...
      </div>
    )
  }

  if (erro) {
    return (
      <div role="alert" className="flex flex-col items-start gap-3 rounded-lg border border-red-200 bg-red-50 p-5 text-sm text-red-900 sm:flex-row sm:items-center sm:justify-between">
        <p>Nao foi possivel carregar as recomendacoes.</p>
        <Button type="button" variant="outline" size="sm" onClick={() => setTentativa((value) => value + 1)}>
          <RefreshCw className="mr-2 h-4 w-4" />
          Tentar novamente
        </Button>
      </div>
    )
  }

  if (dados && !dados.habilitado) {
    return (
      <div className="rounded-lg border border-slate-200 bg-slate-50 p-5 text-sm text-slate-600">
        As recomendacoes estao indisponiveis por configuracao.
      </div>
    )
  }

  if (!dados?.itens.length) {
    return (
      <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-5 text-sm text-emerald-900">
        Nenhuma recomendacao disponivel no momento.
      </div>
    )
  }

  return (
    <div className="grid gap-3 sm:grid-cols-2" aria-live="polite">
      {dados.itens.map((item) => (
        <article key={item.id} className="flex min-w-0 flex-col rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex min-w-0 items-start gap-3">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-pink-50 text-[#C51683]">
              <Lightbulb className="h-5 w-5" />
            </span>
            <div className="min-w-0">
              <p className="truncate text-xs font-semibold uppercase text-slate-500">{item.anuncioTitulo}</p>
              <h3 className="mt-1 text-base font-bold text-slate-900">{item.titulo}</h3>
            </div>
          </div>
          <p className="mt-3 flex-1 break-words text-sm leading-6 text-slate-600">{item.descricao}</p>
          <Button asChild variant="outline" size="sm" className="mt-4 w-full justify-between sm:w-auto">
            <Link href={item.acaoHref}>
              {item.acaoRotulo}
              <ArrowRight className="ml-2 h-4 w-4" />
            </Link>
          </Button>
        </article>
      ))}
    </div>
  )
}
