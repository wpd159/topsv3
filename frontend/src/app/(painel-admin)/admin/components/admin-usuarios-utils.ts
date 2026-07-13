'use client'

export type AdminUsuario = {
  id: number
  nomeCompleto?: string | null
  username?: string | null
  email?: string | null
  cpf?: string | null
  telefone?: string | null
  status?: string | null
  twoFactorAtivo?: boolean | null
  criadoEm?: string | null
  dataCadastro?: string | null
  estadoUf?: string | null
  cidadeNome?: string | null
  bairroNome?: string | null
  localizacao?: string | null
  totalAnuncios?: number | null
  anunciosAtivos?: number | null
  anunciosPendentes?: number | null
  totalCreditos?: number | null
  beneficiosAtivos?: number | null
}

export type AdminUsuarioDetalhes = AdminUsuario & {
  dataNascimento?: string | null
  totalDocumentos?: number | null
  beneficiosAtivosCodigos?: string[] | null
  anuncios?: Array<{
    id?: number | null
    titulo?: string | null
    status?: string | null
    dataPublicacao?: string | null
    linkAnuncio?: string | null
    beneficiosAtivosCodigos?: string[] | null
  }>
}

export function getUsuarioNomePrincipal(usuario: Pick<AdminUsuario, 'nomeCompleto' | 'username'>) {
  return usuario.nomeCompleto?.trim() || usuario.username?.trim() || '—'
}

export function getUsuarioHandle(usuario: Pick<AdminUsuario, 'username'>) {
  return usuario.username?.trim() ? `@${usuario.username.trim()}` : null
}

export function formatarDataBR(value?: string | null) {
  if (!value) return '—'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return '—'
  return parsed.toLocaleDateString('pt-BR')
}

export function formatarDataHoraBR(value?: string | null) {
  if (!value) return '—'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return '—'
  return parsed.toLocaleString('pt-BR')
}

export function formatarTelefoneExibicao(value?: string | null) {
  const digits = (value || '').replace(/\D/g, '')
  if (!digits) return '—'

  let normalized = digits
  if (normalized.startsWith('55') && normalized.length > 11) {
    normalized = normalized.slice(2)
  }

  if (normalized.length === 11) {
    return normalized.replace(/(\d{2})(\d{5})(\d{4})/, '($1) $2-$3')
  }

  if (normalized.length === 10) {
    return normalized.replace(/(\d{2})(\d{4})(\d{4})/, '($1) $2-$3')
  }

  return value?.trim() || '—'
}

export function formatarLocalUsuario(usuario: Pick<AdminUsuario, 'cidadeNome' | 'estadoUf'>) {
  const cidade = usuario.cidadeNome?.trim()
  const uf = usuario.estadoUf?.trim()
  if (cidade && uf) return `${cidade}/${uf}`
  if (uf) return uf
  return '—'
}

export function normalizarBuscaUsuario(value: string) {
  return value.replace(/\D/g, '')
}

export function getTipoUsuarioMeta(
  usuario: Pick<AdminUsuario, 'status' | 'totalAnuncios' | 'totalCreditos' | 'beneficiosAtivos'>
) {
  const status = (usuario.status || '').toUpperCase()
  const totalAnuncios = Number(usuario.totalAnuncios || 0)
  const totalCreditos = Number(usuario.totalCreditos || 0)
  const beneficiosAtivos = Number(usuario.beneficiosAtivos || 0)

  if (status === 'INATIVO') {
    return {
      label: 'Inativo',
      className: 'border-gray-300 bg-gray-100 text-gray-700',
    }
  }

  if (totalAnuncios <= 0) {
    return {
      label: 'Sem anúncio',
      className: 'border-slate-300 bg-slate-100 text-slate-700',
    }
  }

  if (beneficiosAtivos > 0 || totalCreditos > 0) {
    return {
      label: 'Monetizando',
      className: 'border-emerald-300 bg-emerald-100 text-emerald-700',
    }
  }

  return {
    label: 'Potencial',
    className: 'border-amber-300 bg-amber-100 text-amber-700',
  }
}

export function formatarCodigoBeneficio(codigo: string) {
  const labels: Record<string, string> = {
    CARROSSEL_FOTOS: 'Carrossel fotos',
    FOTOS_EXTRA_5: 'Até 10 fotos',
    VIDEO_1: 'Vídeo 1',
    STORIES: 'Stories',
    WHATSAPP_CARD: 'WhatsApp Card',
    OCULTAR_IDADE: 'Ocultar idade',
  }

  return (
    labels[codigo] ||
    codigo.replace(/_/g, ' ').toLowerCase().replace(/^\w/, (char) => char.toUpperCase())
  )
}

export function buildWhatsAppUrl(telefone?: string | null) {
  const digits = (telefone || '').replace(/\D/g, '')
  if (!digits) return null

  let normalized = digits
  if (normalized.length === 10 || normalized.length === 11) {
    normalized = `55${normalized}`
  }

  if (normalized.length < 12 || normalized.length > 13) {
    return null
  }

  return `https://wa.me/${normalized}`
}
