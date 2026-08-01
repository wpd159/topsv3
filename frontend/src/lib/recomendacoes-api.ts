'use client'

import {
  ApiContractError,
  apiErrorFromResponse,
  normalizeApiError,
  publicApiUrl,
} from '@/lib/api-contract'
import { corrigirEstruturaTexto } from '@/lib/text/encoding'

export type RecomendacaoAnunciante = {
  id: string
  anuncioId: string
  anuncioSlug: string
  anuncioTitulo: string
  tipo: 'RENOVACAO' | 'PERFIL' | 'FOTOS' | 'VIDEO' | 'CARROSSEL' | 'IMPULSIONAMENTO'
  titulo: string
  descricao: string
  acaoRotulo: string
  acaoHref: string
}

export type RecomendacoesAnunciante = {
  habilitado: boolean
  itens: RecomendacaoAnunciante[]
}

function payloadIncompativel(): never {
  throw new ApiContractError(
    'O servico retornou as recomendacoes em formato incompativel.',
    'TECHNICAL_FAILURE',
    502,
    true,
  )
}

function texto(value: unknown) {
  if (typeof value !== 'string' || !value.trim()) payloadIncompativel()
  return value
}

function parse(value: unknown): RecomendacoesAnunciante {
  if (!value || typeof value !== 'object' || Array.isArray(value)) payloadIncompativel()
  const raw = value as Record<string, unknown>
  if (typeof raw.habilitado !== 'boolean' || !Array.isArray(raw.itens)) payloadIncompativel()
  const tipos = new Set(['RENOVACAO', 'PERFIL', 'FOTOS', 'VIDEO', 'CARROSSEL', 'IMPULSIONAMENTO'])
  const itens = raw.itens.map((value) => {
    if (!value || typeof value !== 'object' || Array.isArray(value)) payloadIncompativel()
    const item = value as Record<string, unknown>
    const tipo = texto(item.tipo)
    if (!tipos.has(tipo)) payloadIncompativel()
    const acaoHref = texto(item.acaoHref)
    if (!acaoHref.startsWith('/') || acaoHref.startsWith('//')) payloadIncompativel()
    return {
      id: texto(item.id),
      anuncioId: texto(item.anuncioId),
      anuncioSlug: texto(item.anuncioSlug),
      anuncioTitulo: texto(item.anuncioTitulo),
      tipo: tipo as RecomendacaoAnunciante['tipo'],
      titulo: texto(item.titulo),
      descricao: texto(item.descricao),
      acaoRotulo: texto(item.acaoRotulo),
      acaoHref,
    }
  })
  if (new Set(itens.map((item) => item.id)).size !== itens.length) payloadIncompativel()
  return { habilitado: raw.habilitado, itens }
}

export async function fetchRecomendacoesAnunciante(signal?: AbortSignal) {
  try {
    const response = await fetch(publicApiUrl('/painel-anunciante/recomendacoes'), {
      credentials: 'include',
      cache: 'no-store',
      signal,
    })
    if (!response.ok) throw await apiErrorFromResponse(response)
    return parse(corrigirEstruturaTexto(await response.json()))
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw normalizeApiError(error)
  }
}
