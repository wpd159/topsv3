'use client'

import Link from 'next/link'
import { CheckCircle2, ChevronLeft } from 'lucide-react'
import { Button } from '@/components/ui/button'
import type { MonetizacaoActivationResult } from '../types'

function formatDateTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Data indisponível'
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(date)
}

function inefficacyLabel(value?: string | null) {
  if (!value) return null
  if (value === 'ANUNCIO_NAO_PUBLICADO') {
    return 'O efeito público começará quando o anúncio voltar a ficar publicado.'
  }
  if (value === 'ATIVACAO_NAO_VIGENTE') {
    return 'A ativação ainda não está vigente.'
  }
  return 'O efeito público ainda não está disponível.'
}

export function MonetizacaoStepSucesso({ result }: { result: MonetizacaoActivationResult | null }) {
  return (
    <div className="rounded-[32px] border border-emerald-200 bg-emerald-50 px-6 py-8 shadow-sm">
      <div className="flex items-start gap-4">
        <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-emerald-600 text-white">
          <CheckCircle2 className="h-7 w-7" />
        </div>
        <div className="min-w-0">
          <p className="text-sm font-semibold uppercase tracking-[0.16em] text-emerald-700">
            Ativação concluída
          </p>
          <h3 className="mt-2 text-3xl font-semibold tracking-tight text-emerald-950">
            Benefícios ativados com sucesso
          </h3>
          <p className="mt-3 max-w-2xl text-sm leading-6 text-emerald-900/80">
            A operação foi concluída usando o fluxo atual de créditos e benefícios do sistema.
          </p>
        </div>
      </div>

      {result?.itens.length ? (
        <div className="mt-6 space-y-3">
          {result.itens.map((item) => (
            <div
              key={item.id}
              className="rounded-[24px] border border-emerald-200 bg-white px-4 py-4"
            >
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <p className="text-sm font-semibold text-zinc-950">{item.beneficioNome}</p>
                  <p className="mt-1 text-xs text-zinc-500">
                    {item.duracaoDias === 1 ? '1 dia' : `${item.duracaoDias} dias`} ·{' '}
                    {formatDateTime(item.inicioEm)} até {formatDateTime(item.fimEm)}
                  </p>
                  <p className="mt-2 text-xs text-zinc-700">{item.efeitoPublico}</p>
                  {inefficacyLabel(item.motivoIneficacia) ? (
                    <p className="mt-1 text-xs font-medium text-amber-700">
                      {inefficacyLabel(item.motivoIneficacia)}
                    </p>
                  ) : null}
                </div>
                <span className="rounded-full bg-emerald-100 px-3 py-1 text-xs font-semibold text-emerald-800">
                  {item.custoCreditos} créditos · {item.status}
                </span>
              </div>
            </div>
          ))}
        </div>
      ) : null}

      {result ? (
        <div className="mt-5 rounded-[24px] border border-emerald-200 bg-white px-4 py-4">
          <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-emerald-700">
            Total utilizado
          </p>
          <p className="mt-2 text-lg font-semibold text-zinc-950">
            {result.totalCreditos} créditos
          </p>
          <p className="mt-1 text-sm text-zinc-600">
            Saldo: {result.saldoAnterior} → {result.saldoPosterior}
          </p>
        </div>
      ) : null}

      <div className="mt-8 flex flex-col gap-3 sm:flex-row">
        <Button asChild className="h-12 rounded-2xl bg-zinc-950 text-white hover:bg-zinc-800">
          <Link href="/meus-anuncios">
            <ChevronLeft className="mr-2 h-4 w-4" />
            Voltar para meus anúncios
          </Link>
        </Button>
      </div>
    </div>
  )
}
