'use client'

import Image from 'next/image'
import Link from 'next/link'
import { BanknotesIcon, EyeIcon, MapPinIcon, MegaphoneIcon, PencilIcon, SparklesIcon } from '@heroicons/react/24/solid'
import { useId } from 'react'
import {
  MeuAnuncioAcoesCicloVida,
  type CicloVidaAcao,
} from '@/components/anuncios/meu-anuncio-acoes-ciclo-vida'
import type { MeuAnuncio, MeuAnuncioCicloVida } from '@/lib/meus-anuncios-api'
import { cn } from '@/lib/utils'
import { formatarVisualizacoesCanonicas } from '@/lib/visualizacoes-canonicas'
import { apresentarBeneficioPremium } from '@/lib/meu-anuncio-beneficios'
import { formatStoryDate, getStoryEntryState } from '@/components/stories/story-entry-state'

const STATUS: Record<string, { label: string; className: string }> = {
  RASCUNHO: { label: 'Rascunho', className: 'border-slate-300 bg-slate-100 text-slate-700' },
  PENDENTE_REVISAO: { label: 'Em revisão', className: 'border-amber-300 bg-amber-100 text-amber-800' },
  APROVADO: { label: 'Aprovado', className: 'border-sky-300 bg-sky-100 text-sky-800' },
  PUBLICADO: { label: 'Publicado', className: 'border-emerald-300 bg-emerald-100 text-emerald-800' },
  PAUSADO: { label: 'Pausado', className: 'border-slate-300 bg-slate-100 text-slate-700' },
  BLOQUEADO: { label: 'Bloqueado', className: 'border-rose-300 bg-rose-100 text-rose-800' },
  REJEITADO: { label: 'Rejeitado', className: 'border-rose-300 bg-rose-100 text-rose-800' },
  REMOVIDO: { label: 'Removido', className: 'border-slate-300 bg-slate-100 text-slate-700' },
}

const MODERACAO: Record<string, string> = {
  NAO_ENVIADO: 'Não enviado à moderação',
  PENDENTE: 'Moderação pendente',
  APROVADO: 'Moderação aprovada',
  REJEITADO: 'Moderação rejeitada',
}

const PARAMETROS_DE_URL_ASSINADA = new Set([
  'expires',
  'key-pair-id',
  'signature',
  'token',
  'x-amz-algorithm',
  'x-amz-credential',
  'x-amz-date',
  'x-amz-expires',
  'x-amz-security-token',
  'x-amz-signature',
])

export function anuncioStatus(status: string) {
  return STATUS[status] ?? {
    label: 'Status indisponível',
    className: 'border-slate-300 bg-slate-100 text-slate-700',
  }
}

export function anuncioModeracao(status: string) {
  return MODERACAO[status] ?? 'Moderação indisponível'
}

export function anuncioLocalizacao(anuncio: MeuAnuncio) {
  const localizacao = anuncio.localizacao
  if (!localizacao) return 'Localização não informada'
  return [localizacao.bairro, localizacao.cidade, localizacao.uf].filter(Boolean).join(', ') || 'Localização não informada'
}

export function anuncioPreco(preco: number | null) {
  if (preco == null) return 'Preço não informado'
  const valor = Number(preco)
  if (!Number.isFinite(valor)) return 'Preço indisponível'
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

export function anuncioPodeMonetizar(anuncio: Pick<MeuAnuncio, 'status'>) {
  return anuncio.status === 'PUBLICADO'
}

function formatarDataReprovacao(value: string) {
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

export function meuAnuncioUrlPublicaSegura(url: string | null | undefined) {
  const valor = url?.trim()
  if (!valor) return null

  try {
    const resolvida = new URL(valor, 'https://publico.topsv3.invalid')
    if (resolvida.protocol !== 'https:') return null
    const possuiAssinatura = Array.from(resolvida.searchParams.keys()).some((chave) =>
      PARAMETROS_DE_URL_ASSINADA.has(chave.toLowerCase())
    )
    return possuiAssinatura ? null : valor
  } catch {
    return null
  }
}

type MeuAnuncioCardProps = {
  anuncio: MeuAnuncio
  onCicloVida: (resultado: MeuAnuncioCicloVida, acao: CicloVidaAcao) => void
  onStoryOpen: (anuncioId: string, trigger: HTMLButtonElement) => void
}

export function MeuAnuncioCard({ anuncio, onCicloVida, onStoryOpen }: MeuAnuncioCardProps) {
  const storyUnavailableId = useId()
  const status = anuncioStatus(anuncio.status)
  const storyEntry = getStoryEntryState(anuncio)
  const storyMediaError = anuncio.storyAtivo?.modoConteudo === 'MIDIA_UPLOAD'
    && anuncio.storyAtivo.estadoMidia === 'INDISPONIVEL'

  const capaPublica = !anuncio.capa?.restrita
    ? meuAnuncioUrlPublicaSegura(anuncio.capa?.urlPublica)
    : null
  const capa = capaPublica ?? '/icone-sem-foto.png'
  const podeMonetizar = anuncioPodeMonetizar(anuncio)
  const deveCorrigirEReenviar = anuncio.acoesPermitidas.corrigirEReenviar

  return (
    <article className="group mx-auto flex w-full max-w-[330px] flex-col overflow-hidden rounded-xl border border-gray-200 bg-white transition-all duration-300 hover:-translate-y-0.5 hover:border-gray-300 hover:shadow-md">
      <div className="relative aspect-[3/4] w-full overflow-hidden bg-gray-50">
        <Image
          src={capa}
          alt={`Capa de ${anuncio.titulo}`}
          fill
          className="object-cover object-center transition-transform duration-500 group-hover:scale-[1.02]"
          quality={85}
          sizes="(max-width: 768px) 100vw, 330px"
        />
        <span className={cn('absolute left-2 top-2 rounded-md border px-2 py-1 text-[11px] font-semibold', status.className)}>
          {status.label}
        </span>
        {anuncio.capa?.restrita ? (
          <span className="absolute bottom-2 left-2 rounded-md bg-slate-950/80 px-2 py-1 text-[11px] font-medium text-white">
            Capa protegida
          </span>
        ) : null}
      </div>

      <div className="flex flex-1 flex-col p-4">
        <h2 className="line-clamp-2 break-words text-base font-semibold leading-tight text-gray-900">{anuncio.titulo}</h2>
        <p className="mt-3 flex items-center gap-1.5 text-lg font-bold text-slate-950">
          <BanknotesIcon className="h-5 w-5 shrink-0 text-[#FC1EAD]" aria-hidden="true" />
          <span className="break-words">{anuncioPreco(anuncio.preco)}</span>
        </p>
        <p className="mt-2 flex items-start text-xs leading-5 text-gray-500">
          <MapPinIcon className="mr-1 mt-0.5 h-4 w-4 shrink-0" />
          {anuncioLocalizacao(anuncio)}
        </p>
        <p className="mt-2 flex items-center gap-1.5 text-xs text-slate-500">
          <EyeIcon className="h-4 w-4 text-slate-400" aria-hidden="true" />
          {formatarVisualizacoesCanonicas(anuncio.visualizacoes)} visualizações
        </p>
        <p className="mt-2 text-xs text-slate-500">{anuncioModeracao(anuncio.statusModeracao)}</p>
        {deveCorrigirEReenviar && anuncio.reprovacao ? (
          <div className="mt-3 border border-rose-200 bg-rose-50 px-3 py-3 text-xs text-rose-950">
            <p className="font-semibold">Alterações necessárias</p>
            <p className="mt-1 whitespace-pre-wrap break-words leading-5">{anuncio.reprovacao.motivo}</p>
            <p className="mt-2 text-rose-700">
              Decisão em {formatarDataReprovacao(anuncio.reprovacao.decididoEm)}. Corrija os dados e salve para reenviar à análise.
            </p>
          </div>
        ) : null}
        {anuncio.beneficiosPremium.length ? (
          <section className="mt-3 border-t border-slate-100 pt-3" aria-label="Benefícios Premium">
            <h3 className="flex items-center gap-1.5 text-xs font-semibold text-slate-800">
              <SparklesIcon className="h-4 w-4 shrink-0 text-[#FC1EAD]" aria-hidden="true" />
              Benefícios Premium
            </h3>
            <ul className="mt-2 space-y-2">
              {anuncio.beneficiosPremium.map((beneficio) => {
                const apresentacao = apresentarBeneficioPremium(beneficio)
                return (
                  <li key={beneficio.codigo} className="min-w-0 border-l-2 border-[#FC1EAD]/30 pl-2 text-xs">
                    <p className="break-words font-semibold text-slate-800">{beneficio.nome}</p>
                    <p className="mt-0.5 break-words leading-5 text-slate-600">{apresentacao.situacao}</p>
                    {apresentacao.complemento ? (
                      <p className="break-words leading-5 text-slate-500">{apresentacao.complemento}</p>
                    ) : null}
                  </li>
                )
              })}
            </ul>
          </section>
        ) : null}

        {anuncio.storyAtivo ? (
          <section className="mt-3 border-t border-slate-100 pt-3" aria-label="Stories">
            <h3 className="flex items-center gap-1.5 text-xs font-semibold text-slate-800">
              <SparklesIcon className="h-4 w-4 shrink-0 text-[#FC1EAD]" aria-hidden="true" />
              Stories
            </h3>
            <div
              className={cn('mt-2 rounded-lg border p-3 text-xs text-slate-700', storyMediaError ? 'border-amber-300 bg-amber-50' : 'border-pink-200 bg-pink-50')}
              role={storyMediaError ? 'alert' : 'status'}
            >
              <p className="font-semibold text-slate-900">
                {storyMediaError ? 'Story com falha na mídia' : 'Story ativo'}
              </p>
              <p className="mt-1">Modo: {anuncio.storyAtivo.modoConteudo === 'ANUNCIO' ? 'Divulgar meu anúncio' : 'Mídia enviada'}</p>
              <p className="mt-1">Ativo até {formatStoryDate(anuncio.storyAtivo.fimEm)}</p>
              {anuncio.storyAtivo.modoConteudo === 'MIDIA_UPLOAD' && anuncio.storyAtivo.estadoMidia === 'INDISPONIVEL' ? (
                <p className="mt-2 font-medium text-amber-800">Story ativo, mas a mídia enviada não está disponível.</p>
              ) : null}
              <Link
                href={`/anuncios/${encodeURIComponent(anuncio.slug)}`}
                className="mt-2 inline-flex min-h-9 items-center font-semibold text-[#b5127a] underline-offset-2 hover:underline"
              >
                Ver anúncio
              </Link>
            </div>
          </section>
        ) : null}

        <div className="mt-auto grid grid-cols-2 gap-2 border-t border-gray-100 pt-4">
          <Link
            href={`/meus-anuncios/${encodeURIComponent(anuncio.slug)}`}
            className="inline-flex items-center justify-center rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-700 transition hover:-translate-y-0.5 hover:border-slate-300 hover:shadow-sm"
          >
            <EyeIcon className="mr-1 h-4 w-4" />
            Detalhes
          </Link>
          <Link
            href={`/meus-anuncios/${encodeURIComponent(anuncio.slug)}/editar`}
            className="inline-flex items-center justify-center rounded-lg bg-[#FC1EAD] px-3 py-2 text-xs font-semibold text-white transition hover:-translate-y-0.5 hover:bg-[#e01a9a] hover:shadow-sm"
          >
            <PencilIcon className="mr-1 h-4 w-4" />
            {deveCorrigirEReenviar ? 'Corrigir e reenviar' : 'Editar'}
          </Link>
          {podeMonetizar ? (
            <Link
              href={`/meus-anuncios/${encodeURIComponent(anuncio.slug)}/monetizar`}
              className="col-span-2 inline-flex items-center justify-center rounded-lg border border-[#FC1EAD]/30 bg-[#FC1EAD]/5 px-3 py-2 text-xs font-semibold text-[#b5127a] transition hover:-translate-y-0.5 hover:border-[#FC1EAD]/50 hover:bg-[#FC1EAD]/10 hover:shadow-sm"
            >
              <SparklesIcon className="mr-1 h-4 w-4" aria-hidden="true" />
              Monetizar
            </Link>
          ) : null}
          <div className="col-span-2 min-w-0">
            <button
              type="button"
              onClick={(event) => {
                if (!storyEntry.disabled) onStoryOpen(anuncio.id, event.currentTarget)
              }}
              disabled={storyEntry.disabled}
              aria-describedby={storyEntry.reason ? storyUnavailableId : undefined}
              title={storyEntry.reason ?? undefined}
              className="inline-flex min-h-10 w-full items-center justify-center rounded-lg border border-[#FC1EAD]/40 bg-pink-50 px-3 py-2 text-xs font-semibold text-[#b5127a] transition hover:-translate-y-0.5 hover:border-[#FC1EAD] hover:bg-pink-100 hover:shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#FC1EAD] focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:border-slate-200 disabled:bg-slate-100 disabled:text-slate-500 disabled:hover:translate-y-0 disabled:hover:shadow-none"
            >
              <MegaphoneIcon className="mr-1 h-4 w-4" aria-hidden="true" />
              {storyEntry.buttonLabel}
            </button>
            {storyEntry.reason ? (
              <p id={storyUnavailableId} className="mt-1.5 break-words text-center text-[11px] leading-4 text-slate-500">
                {storyEntry.reason}
              </p>
            ) : null}
          </div>
        </div>
        <MeuAnuncioAcoesCicloVida
          anuncio={anuncio}
          onSuccess={onCicloVida}
          className="mt-3 border-t border-gray-100 pt-3"
        />
      </div>
    </article>
  )
}
