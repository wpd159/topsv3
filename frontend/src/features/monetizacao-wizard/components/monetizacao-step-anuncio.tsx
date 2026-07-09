'use client'

import Link from 'next/link'
import { CheckCircle2, ImageIcon, MapPin, ShieldCheck, WalletCards } from 'lucide-react'
import { PreviewTag } from '@/features/anuncio-wizard/components/wizard-ui'
import type { AnuncioMeuResumo } from '../types'

function formatStatus(status?: string | null) {
  const normalized = String(status || '').toUpperCase()
  const map: Record<string, string> = {
    ATIVO: 'Ativo',
    EM_MODERACAO: 'Em moderação',
    PENDENTE: 'Pendente',
    REJEITADO: 'Rejeitado',
    PAUSADO: 'Pausado',
    INATIVO: 'Inativo',
  }
  return map[normalized] || status || 'Status não informado'
}

function featureLabel(codigo: string) {
  const map: Record<string, string> = {
    ANUNCIO_TOPO: 'Topo da lista',
    FOTOS_EXTRA_5: 'Mais fotos',
    VIDEO_1: 'Vídeo',
    WHATSAPP_CARD: 'WhatsApp destacado',
    CARROSSEL_FOTOS: 'Carrossel',
    OCULTAR_IDADE: 'Ocultar idade',
  }
  return map[codigo] || codigo.replaceAll('_', ' ')
}

function buildActiveBenefits(anuncio: AnuncioMeuResumo) {
  const benefits: Array<{ codigo: string; label: string }> = []

  if (anuncio.impulsionado) {
    benefits.push({ codigo: 'ANUNCIO_TOPO', label: 'Topo da lista' })
  }

  for (const feature of anuncio.featuresAtivas || []) {
    if (!feature.codigo) continue
    if (feature.codigo === 'STORIES') continue
    benefits.push({ codigo: feature.codigo, label: feature.nome || featureLabel(feature.codigo) })
  }

  return benefits
}

export function MonetizacaoStepAnuncio({
  anuncio,
  saldoCreditos,
}: {
  anuncio: AnuncioMeuResumo
  saldoCreditos: number
}) {
  const activeBenefits = buildActiveBenefits(anuncio)

  return (
    <div className="rounded-[28px] border border-zinc-200 bg-white p-4 shadow-sm sm:p-5">
      <div className="grid gap-5 md:grid-cols-[168px_minmax(0,1fr)]">
        <div className="overflow-hidden rounded-[24px] border border-zinc-200 bg-zinc-50">
          <div className="flex aspect-[4/3] items-center justify-center bg-zinc-100">
            {anuncio.fotoCapa ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={anuncio.fotoCapa} alt="" className="h-full w-full object-cover" />
            ) : (
              <ImageIcon className="h-8 w-8 text-zinc-400" />
            )}
          </div>
        </div>

        <div className="min-w-0">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div className="min-w-0">
              <h3 className="text-2xl font-semibold tracking-tight text-zinc-950">{anuncio.titulo}</h3>
              <div className="mt-2 flex items-center gap-2 text-sm text-zinc-600">
                <MapPin className="h-4 w-4 text-[#FC1EAD]" />
                <span>{anuncio.localizacao || 'Cidade não informada'}</span>
              </div>
            </div>
            <span className="rounded-full border border-zinc-200 bg-zinc-50 px-4 py-2 text-sm font-semibold text-zinc-700">
              {formatStatus(anuncio.status)}
            </span>
          </div>

          <div className="mt-5 grid gap-3 sm:grid-cols-2">
            <div className="rounded-[22px] border border-zinc-200 bg-zinc-50 px-4 py-4">
              <div className="flex items-center gap-2 text-sm font-semibold text-zinc-900">
                <WalletCards className="h-4 w-4 text-[#FC1EAD]" />
                Saldo de créditos
              </div>
              <p className="mt-2 text-2xl font-semibold text-zinc-950">{saldoCreditos}</p>
              <Link
                href="/creditos"
                className="mt-3 inline-flex h-10 items-center justify-center rounded-2xl bg-[#FC1EAD] px-4 text-sm font-semibold text-white transition hover:bg-[#e01a9a]"
              >
                Comprar créditos
              </Link>
            </div>

            <div className="rounded-[22px] border border-zinc-200 bg-zinc-50 px-4 py-4">
              <div className="flex items-center gap-2 text-sm font-semibold text-zinc-900">
                <ShieldCheck className="h-4 w-4 text-[#FC1EAD]" />
                Benefícios ativos
              </div>
              {activeBenefits.length > 0 ? (
                <div className="mt-3 flex flex-wrap gap-2">
                  {activeBenefits.map((benefit) => (
                    <PreviewTag
                      key={benefit.codigo}
                      icon={<CheckCircle2 className="h-3.5 w-3.5" />}
                      label={benefit.label}
                      tone="light"
                    />
                  ))}
                </div>
              ) : (
                <p className="mt-2 text-sm text-zinc-500">Nenhum benefício premium ativo agora.</p>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
