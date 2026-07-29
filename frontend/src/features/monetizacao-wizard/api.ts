'use client'

import { buscarMeuAnuncio, type MeuAnuncio } from '@/lib/meus-anuncios-api'
import { messageFromApiBody } from '@/utils/read-api-error-response'
import { publicApiUrl } from '@/lib/api-contract'
import type {
  CompraPremiumResultado,
  MonetizacaoCotacaoOpcao,
  MonetizacaoOpcaoCodigo,
  MonetizacaoWizardData,
} from './types'

type CatalogoBackend = {
  codigo: string
  nome: string
  descricao: string
  ativo: boolean
  opcoes: Array<{
    duracaoDias: number
    custoCreditos: number
    ativo: boolean
  }>
}

export type MinhaMonetizacaoBackend = {
  saldoCreditos: number
  historico: MonetizacaoWizardData['historico']
  catalogo: CatalogoBackend[]
  pacotesCredito: MonetizacaoWizardData['planos']
  beneficiosAtivos: MonetizacaoWizardData['beneficiosAtivos']
}

function csrfCookieName() {
  return ['XSRF', 'TOKEN'].join('-')
}

function csrfHeaderName() {
  return ['X', 'XSRF', 'TOKEN'].join('-')
}

function readCsrfValue() {
  if (typeof document === 'undefined') return null
  const cookieName = csrfCookieName()
  const entry = document.cookie
    .split('; ')
    .find((cookie) => cookie.startsWith(`${cookieName}${String.fromCharCode(61)}`))
  return entry ? decodeURIComponent(entry.slice(cookieName.length + 1)) : null
}

async function bootstrapCsrfValue() {
  await fetch(publicApiUrl('/auth/me'), {
    method: 'GET',
    credentials: 'include',
    cache: 'no-store',
  })
  return readCsrfValue()
}

async function readJson<T>(response: Response, fallback: string): Promise<T> {
  const raw = await response.text()
  if (!response.ok) throw new Error(messageFromApiBody(raw, response.status, fallback))
  if (!raw.trim()) throw new Error('Resposta vazia do servidor.')
  try {
    return JSON.parse(raw) as T
  } catch {
    throw new Error('Resposta invalida do servidor.')
  }
}

function mapAnuncio(anuncio: MeuAnuncio): MonetizacaoWizardData['anuncio'] {
  return {
    id: anuncio.id,
    slug: anuncio.slug,
    titulo: anuncio.titulo,
    fotoCapa: anuncio.capa?.urlPublica || null,
    localizacao: [anuncio.localizacao?.bairro, anuncio.localizacao?.cidade, anuncio.localizacao?.uf]
      .filter(Boolean)
      .join(', '),
    valor: anuncio.preco == null ? 'Consulte valores' : Number(anuncio.preco).toLocaleString('pt-BR', {
      style: 'currency',
      currency: 'BRL',
    }),
    status: anuncio.status,
  }
}

function mapAnuncioEdit(anuncio: MeuAnuncio): MonetizacaoWizardData['anuncioEdit'] {
  return {
    id: anuncio.id,
    slug: anuncio.slug,
    titulo: anuncio.titulo,
    categoria: anuncio.categoria,
    preco: anuncio.preco,
    descricao: anuncio.descricao,
    locaisAtendimento: anuncio.locaisAtendimento,
    servicos: anuncio.servicos,
    estadoUf: anuncio.localizacao?.uf || null,
    cidadeNome: anuncio.localizacao?.cidade || null,
    bairroNome: anuncio.localizacao?.bairro || null,
    fotos: anuncio.midias
      .filter((midia) => midia.tipo === 'FOTO' && Boolean(midia.urlPublica))
      .map((midia) => midia.urlPublica as string),
    videosAnuncio: anuncio.midias
      .filter((midia) => midia.tipo === 'VIDEO' && Boolean(midia.urlPublica))
      .map((midia) => midia.urlPublica as string),
  }
}

function mapCatalogo(data: MinhaMonetizacaoBackend, anuncioId: string) {
  const activeCodes = new Set(data.beneficiosAtivos.map((item) => item.beneficioCodigo))
  const opcoes = data.catalogo
    .filter((item) => item.ativo)
    .map<MonetizacaoCotacaoOpcao>((item) => ({
      codigo: item.codigo,
      titulo: item.nome,
      descricao: item.descricao,
      disponivel: !activeCodes.has(item.codigo),
      motivoIndisponibilidade: activeCodes.has(item.codigo) ? 'BENEFICIO_JA_ATIVO' : null,
      modoAtivacao: 'COMPRA_CREDITOS',
      componentes: [item.codigo],
      duracoes: item.opcoes
        .filter((opcao) => opcao.ativo)
        .map((opcao) => ({
          dias: opcao.duracaoDias,
          duracaoHoras: opcao.duracaoDias * 24,
          creditos: opcao.custoCreditos,
          saldoSuficiente: data.saldoCreditos >= opcao.custoCreditos,
          creditosFaltantes: Math.max(0, opcao.custoCreditos - data.saldoCreditos),
          podeAtivar: !activeCodes.has(item.codigo),
          precisaComprarCreditos: data.saldoCreditos < opcao.custoCreditos,
          motivoBloqueio: activeCodes.has(item.codigo) ? 'BENEFICIO_JA_ATIVO' : null,
        })),
    }))

  return { anuncioId, saldoCreditos: data.saldoCreditos, opcoes }
}

export async function fetchMonetizacaoWizardData(slug: string): Promise<MonetizacaoWizardData> {
  const anuncio = await buscarMeuAnuncio(slug)
  const monetizacao = await fetchMinhaMonetizacao(slug)

  return {
    anuncio: mapAnuncio(anuncio),
    anuncioEdit: mapAnuncioEdit(anuncio),
    cotacao: mapCatalogo(monetizacao, anuncio.id),
    planos: monetizacao.pacotesCredito,
    historico: monetizacao.historico,
    beneficiosAtivos: monetizacao.beneficiosAtivos,
  }
}

export async function fetchMinhaMonetizacao(slug?: string): Promise<MinhaMonetizacaoBackend> {
  const query = slug ? `?anuncioSlug=${encodeURIComponent(slug)}` : ''
  const response = await fetch(publicApiUrl(`/minha-conta/monetizacao${query}`), {
    credentials: 'include',
    cache: 'no-store',
  })
  return readJson<MinhaMonetizacaoBackend>(
    response,
    'Nao foi possivel carregar saldo e beneficios.'
  )
}

export function newPremiumPurchaseIdempotencyKey() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `premium-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

export async function comprarBeneficios(
  anuncioSlug: string,
  itens: Array<{ beneficioCodigo: MonetizacaoOpcaoCodigo; duracaoDias: number }>,
  idempotencyKey: string
) {
  const csrfValue = readCsrfValue() || (await bootstrapCsrfValue())
  const headers = new Headers({
    Accept: 'application/json',
    'Content-Type': 'application/json',
    'Idempotency-Key': idempotencyKey,
  })
  if (csrfValue) headers.set(csrfHeaderName(), csrfValue)

  const response = await fetch(publicApiUrl('/minha-conta/monetizacao/compras'), {
    method: 'POST',
    credentials: 'include',
    cache: 'no-store',
    headers,
    body: JSON.stringify({ anuncioSlug, itens }),
  })
  return readJson<CompraPremiumResultado>(response, 'Nao foi possivel ativar os beneficios.')
}
