'use client'

import {
  MapPinIcon,
  LinkIcon,
  ClockIcon,
  ArrowTopRightOnSquareIcon,
} from '@heroicons/react/24/solid'
import { useEffect, useMemo, useRef, useState } from 'react'
import { corrigirTextoCorrompido } from '@/lib/text/encoding'

interface MainContentProps {
  anuncio: any
}

const LABEL_LOCAL: Record<string, string> = {
  A_COMBINAR: 'A combinar',
  HOTEL_MOTEL: 'Hotel/Motel',
  MEU_LOCAL: 'Meu local',
}

const LABEL_SERVICO: Record<string, string> = {
  ANAL: 'Anal',
  ATRIZ_PORNO: 'Atriz pornô',
  FETICHES: 'Fetiches',
  MASSAGEM_TANTRICA: 'Massagem tântrica',
  ATIVO: 'Ativo',
  BDSM: 'BDSM',
  JOGOS_DE_INTERPRETACAO: 'Roleplay',
  ORAL: 'Oral',
  ATOR_PORNO: 'Ator pornô',
  EJACULACAO_CORPORAL: 'Ejaculação corporal',
  MASSAGEM_EROTICA: 'Massagem erótica',
  PASSIVO: 'Passivo',
  NAMORADAS: 'Namorados',
  TRIO: 'Trio',
  VIDEOCHAMADA: 'Sexo virtual',
}

function normalizarLink(url: string) {
  if (!url) return ''
  const clean = url.trim()
  if (clean.startsWith('http://') || clean.startsWith('https://')) return clean
  return 'https://' + clean
}

function clean(v?: string | null) {
  const s = corrigirTextoCorrompido(v ?? '').trim()
  if (!s) return ''
  const low = s.toLowerCase()
  if (low === 'não informado' || low === 'nao informado') return ''
  return s
}

function dedupeStrings(values: unknown): string[] {
  if (!Array.isArray(values)) return []

  const seen = new Set<string>()
  const result: string[] = []

  for (const value of values) {
    if (typeof value !== 'string') continue
    const normalized = value.trim()
    if (!normalized || seen.has(normalized)) continue
    seen.add(normalized)
    result.push(normalized)
  }

  return result
}

export default function MainContent({ anuncio }: MainContentProps) {
  const mapaContainerRef = useRef<HTMLDivElement | null>(null)
  const [mapaCarregado, setMapaCarregado] = useState(false)
  const localizacaoLabel = useMemo(() => {
    const uf = clean(anuncio.estadoUf)
    const cidade = clean(anuncio.cidadeNome)
    const bairro = clean(anuncio.bairroNome)
    const ponto = clean(anuncio.pontoReferenciaTexto)

    const parts = [uf, cidade, bairro].filter(Boolean)
    if (parts.length) return ponto ? `${parts.join(' - ')} | ${ponto}` : parts.join(' - ')

    const fallback = clean(anuncio.localizacao) || clean(anuncio.cidade)
    return ponto || fallback || 'Cidade não informada'
  }, [
    anuncio.estadoUf,
    anuncio.cidadeNome,
    anuncio.bairroNome,
    anuncio.pontoReferenciaTexto,
    anuncio.localizacao,
    anuncio.cidade,
  ])

  const hasLocal = localizacaoLabel !== 'Cidade não informada'
  const mapaConsulta = [clean(anuncio.pontoReferenciaTexto), clean(anuncio.bairroNome), clean(anuncio.cidadeNome), clean(anuncio.estadoUf)]
    .filter(Boolean)
    .join(', ') || localizacaoLabel
  const mapaUrl = hasLocal ? `https://www.google.com/maps?q=${encodeURIComponent(mapaConsulta)}&output=embed` : ''
  const mapaLink = hasLocal ? `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(mapaConsulta)}` : ''

  useEffect(() => {
    if (!hasLocal || mapaCarregado) return
    const container = mapaContainerRef.current
    if (!container) return

    if (typeof IntersectionObserver === 'undefined') {
      setMapaCarregado(true)
      return
    }

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (!entry?.isIntersecting) return
        setMapaCarregado(true)
        observer.disconnect()
      },
      { rootMargin: '240px 0px' }
    )

    observer.observe(container)
    return () => observer.disconnect()
  }, [hasLocal, mapaCarregado])

  const servicos = useMemo(() => dedupeStrings(anuncio.servicos), [anuncio.servicos])
  const locaisAtendimento = useMemo(
    () => dedupeStrings(anuncio.locaisAtendimento),
    [anuncio.locaisAtendimento]
  )

  const linkConteudoRaw: string | null = anuncio.linkConteudo ?? null
  const linkConteudo = linkConteudoRaw ? normalizarLink(linkConteudoRaw) : null
  const horario: string | null = anuncio.horario ?? null

  return (
    <div className="space-y-8">
      <div className="scroll-mt-24 rounded-xl border border-gray-200 bg-white p-5">
        <h2 className="mb-2 text-lg font-semibold text-gray-900">Sobre o anunciante</h2>
        <p className="leading-relaxed text-gray-700">
          {corrigirTextoCorrompido(
            anuncio.descricaoAnunciante ??
              'Profissional experiente e dedicado(a), com foco em proporcionar um atendimento de qualidade, conforto e discrição.'
          )}
        </p>

        {horario && (
          <div className="mt-4 inline-flex items-center gap-2 rounded-full bg-pink-50 px-4 py-1 text-sm text-pink-700">
            <ClockIcon className="h-4 w-4" />
            Atendimento: {corrigirTextoCorrompido(horario).replace(/_/g, ' ').toLowerCase()}
          </div>
        )}
      </div>

      <div className="scroll-mt-24 space-y-4 rounded-xl border border-gray-200 bg-white p-5">
        <h2 className="mb-2 text-lg font-semibold text-gray-900">Descrição do anúncio</h2>
        <p className="whitespace-pre-line leading-relaxed text-gray-700">
          {corrigirTextoCorrompido(
            anuncio.descricaoAnuncio ??
              anuncio.descricao ??
              'Anúncio criado para divulgar serviços de alto padrão, com atendimento personalizado e total discrição.'
          )}
        </p>

        <div className="pt-3">
          <h3 className="mb-2 text-sm font-semibold text-gray-900">Serviços</h3>
          {servicos.length > 0 ? (
            <div className="flex flex-wrap gap-3">
              {servicos.map((s: string) => (
                <span
                  key={s}
                  className="inline-flex items-center gap-1.5 rounded-full border border-pink-200 bg-pink-50 px-4 py-2 text-sm text-pink-700 shadow-[0_0_12px_rgba(252,30,173,0.12)]"
                >
                  <span className="h-1.5 w-1.5 rounded-full bg-pink-500" />
                  {LABEL_SERVICO[s] ?? corrigirTextoCorrompido(s.replace(/_/g, ' '))}
                </span>
              ))}
            </div>
          ) : (
            <p className="text-xs text-gray-400">Nenhum serviço informado.</p>
          )}
        </div>

        <div className="pt-3">
          <h3 className="mb-2 text-sm font-semibold text-gray-900">Local de atendimento</h3>
          {locaisAtendimento.length > 0 ? (
            <div className="flex flex-wrap gap-3">
              {locaisAtendimento.map((l: string) => (
                <span
                  key={l}
                  className="inline-flex items-center gap-1.5 rounded-full border border-pink-100 bg-gray-50 px-4 py-2 text-sm text-gray-700 shadow-[0_0_12px_rgba(252,30,173,0.08)]"
                >
                  <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
                  {LABEL_LOCAL[l] ?? corrigirTextoCorrompido(l.replace(/_/g, ' '))}
                </span>
              ))}
            </div>
          ) : (
            <p className="text-xs text-gray-400">Nenhum local informado.</p>
          )}
        </div>

        {linkConteudo && (
          <div className="mt-4 border-t border-gray-200 pt-4">
            <h3 className="mb-2 text-sm font-semibold text-gray-900">Venda de conteúdo</h3>
            <a
              href={linkConteudo}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-2 rounded-lg bg-pink-600 px-4 py-2 text-sm text-white transition hover:bg-pink-700"
            >
              <LinkIcon className="h-4 w-4" />
              Acessar conteúdo exclusivo
            </a>
            <p className="mt-1 text-xs text-gray-400">Você será redirecionado para uma página externa.</p>
          </div>
        )}
      </div>

      <div className="scroll-mt-24 space-y-5 rounded-xl border border-pink-100 bg-white p-6 shadow-[0_0_18px_rgba(252,30,173,0.08)]">
        <h2 className="text-lg font-semibold text-gray-900">Localização</h2>

        <div className="flex items-center text-sm text-gray-700">
          <MapPinIcon className="mr-2 h-5 w-5 text-pink-500" />
          <span>{localizacaoLabel}</span>
        </div>

        {hasLocal ? (
          <>
            <div
              ref={mapaContainerRef}
              data-public-map
              className="relative h-[300px] w-full overflow-hidden rounded-lg border border-gray-200 bg-gray-50"
            >
              {mapaCarregado ? (
                <iframe
                  src={mapaUrl}
                  title={`Mapa de ${localizacaoLabel}`}
                  width="100%"
                  height="100%"
                  loading="lazy"
                  className="rounded-lg"
                />
              ) : (
                <div className="flex h-full w-full items-center justify-center" aria-hidden="true">
                  <MapPinIcon className="h-8 w-8 text-pink-300" />
                </div>
              )}
            </div>
            <a
              href={mapaLink}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-2 rounded-lg border border-pink-200 px-4 py-2 text-sm font-semibold text-pink-700 transition hover:-translate-y-0.5 hover:bg-pink-50 hover:shadow-[0_0_14px_rgba(252,30,173,0.16)]"
            >
              Abrir no Maps
              <ArrowTopRightOnSquareIcon className="h-4 w-4" />
            </a>
          </>
        ) : (
          <div className="flex h-[300px] w-full items-center justify-center rounded-lg border border-gray-200 bg-gray-50">
            <p className="text-sm text-gray-500">Localização não informada.</p>
          </div>
        )}
      </div>
    </div>
  )
}
