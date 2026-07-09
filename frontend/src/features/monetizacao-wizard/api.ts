'use client'

import { corrigirEstruturaTexto } from '@/lib/text/encoding'
import { messageFromApiBody } from '@/utils/read-api-error-response'
import type {
  AnuncioMeuResumo,
  CheckoutData,
  MonetizacaoWizardData,
  PlanoCredito,
  MonetizacaoCotacaoResponse,
} from './types'
import type { AnuncioEditAPI } from '@/components/anuncios/editar/types'

const API = process.env.NEXT_PUBLIC_API_URL || ''

async function readJson<T>(res: Response, fallbackMessage: string): Promise<T> {
  const raw = await res.text()
  let parsed: unknown = null

  if (raw.trim()) {
    try {
      parsed = JSON.parse(raw)
    } catch {
      if (!res.ok) {
        throw new Error(messageFromApiBody(raw, res.status, fallbackMessage))
      }
      throw new Error('Resposta invalida do servidor.')
    }
  }

  if (!res.ok) {
    throw new Error(messageFromApiBody(raw, res.status, fallbackMessage))
  }

  return parsed as T
}

export async function fetchMonetizacaoWizardData(slug: string): Promise<MonetizacaoWizardData> {
  if (!API) throw new Error('NEXT_PUBLIC_API_URL não definido.')

  const anunciosRes = await fetch(`${API}/anuncios/meus`, {
    credentials: 'include',
    cache: 'no-store',
  })
  const anuncios = corrigirEstruturaTexto(
    await readJson<AnuncioMeuResumo[]>(anunciosRes, 'Não foi possível carregar seus anúncios.')
  )

  const anuncio = (Array.isArray(anuncios) ? anuncios : []).find((item) => item.slug === slug)
  if (!anuncio?.id) {
    throw new Error('Anúncio não encontrado para monetização.')
  }

  const [editRes, cotacaoRes, planosRes] = await Promise.all([
    fetch(`${API}/anuncios/meus/${encodeURIComponent(slug)}/editar`, {
      credentials: 'include',
      cache: 'no-store',
    }),
    fetch(`${API}/monetizacao/cotacao/anuncio/${anuncio.id}`, {
      credentials: 'include',
      cache: 'no-store',
    }),
    fetch(`${API}/creditos/planos`, {
      credentials: 'include',
      cache: 'no-store',
    }),
  ])

  const anuncioEdit = corrigirEstruturaTexto(
    await readJson<AnuncioEditAPI>(editRes, 'Não foi possível carregar os dados do anúncio.')
  )
  const cotacao = await readJson<MonetizacaoCotacaoResponse>(
    cotacaoRes,
    'Não foi possível carregar a cotação da monetização.'
  )
  const planos = await readJson<PlanoCredito[]>(planosRes, 'Não foi possível carregar os planos de créditos.')

  return {
    anuncio,
    anuncioEdit,
    cotacao,
    planos: Array.isArray(planos) ? planos : [],
  }
}

export async function createCheckoutCreditos(planoId: number, nomeCompleto: string, cpf: string) {
  if (!API) throw new Error('NEXT_PUBLIC_API_URL não definido.')

  const res = await fetch(`${API}/checkout/creditos/${planoId}`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nomeCompleto, cpf }),
  })

  return readJson<CheckoutData>(res, 'Não foi possível iniciar o checkout Pix.')
}

export async function fetchCheckoutCreditos(pagamentoId: number) {
  if (!API) throw new Error('NEXT_PUBLIC_API_URL não definido.')

  const res = await fetch(`${API}/checkout/creditos/pagamentos/${pagamentoId}`, {
    credentials: 'include',
    cache: 'no-store',
  })

  return readJson<CheckoutData>(res, 'Não foi possível carregar o pagamento Pix.')
}

export async function verifyCheckoutCreditos(pagamentoId: number) {
  if (!API) throw new Error('NEXT_PUBLIC_API_URL não definido.')

  const res = await fetch(`${API}/checkout/creditos/pagamentos/${pagamentoId}/verificar`, {
    method: 'POST',
    credentials: 'include',
  })

  return readJson<CheckoutData>(res, 'Não foi possível verificar o pagamento Pix.')
}

export async function ativarFeaturePorCatalogo(anuncioId: number, codigo: string, dias?: number) {
  if (!API) throw new Error('NEXT_PUBLIC_API_URL não definido.')

  const res = await fetch(`${API}/features/ativar`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ anuncioId, codigo, ...(dias ? { dias } : {}) }),
  })

  return readJson<Record<string, unknown>>(res, 'Não foi possível ativar o benefício.')
}

export async function ativarImpulsionamento(anuncioId: number, dias: number) {
  if (!API) throw new Error('NEXT_PUBLIC_API_URL não definido.')

  const res = await fetch(`${API}/anuncios/${anuncioId}/impulsionar`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ dias }),
  })

  return readJson<Record<string, unknown>>(res, 'Não foi possível ativar o topo da lista.')
}

export async function publicarStory(anuncioId: number, midia: File) {
  if (!API) throw new Error('NEXT_PUBLIC_API_URL não definido.')

  const formData = new FormData()
  formData.append('codigo', 'STORIES')
  formData.append('anuncioId', String(anuncioId))
  formData.append('midia', midia)

  const res = await fetch(`${API}/stories`, {
    method: 'POST',
    credentials: 'include',
    body: formData,
  })

  return readJson<Record<string, unknown>>(res, 'Não foi possível publicar o story.')
}
