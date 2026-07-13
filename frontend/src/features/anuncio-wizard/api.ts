import type { WizardFormState } from './types'
import { UNSUPPORTED_IMAGE_MESSAGE } from '@/utils/image-upload'

function apiBase() {
  return process.env.NEXT_PUBLIC_API_URL || ''
}

function precoParaNumero(value: string) {
  const digits = String(value || '').replace(/\D/g, '')
  if (!digits) return ''
  return (Number(digits) / 100).toFixed(2)
}

function describeFiles(files: File[]) {
  return files.map((file) => ({
    name: file.name,
    type: file.type || 'sem-content-type',
    size: file.size,
  }))
}

async function readResponse<T>(res: Response, etapa: string): Promise<T> {
  const raw = await res.text().catch(() => '')
  if (!res.ok) {
    let message = raw || `Erro ${res.status}`
    let code = ''
    try {
      const parsed = JSON.parse(raw)
      message = parsed.mensagem || parsed.message || parsed.error || message
      code = parsed.codigo || parsed.code || ''
    } catch {}
    console.error('[KYC_PUBLICAR_ERRO]', {
      etapa,
      status: res.status,
      response: raw,
      code,
      message,
    })
    throw new Error(mapWizardApiError(res.status, message, code))
  }
  return raw ? (JSON.parse(raw) as T) : (null as T)
}

function mapWizardApiError(status: number, message: string, code = '') {
  const normalized = String(message || '').trim().toLowerCase()
  const normalizedCode = String(code || '').trim().toUpperCase()

  if (
    status === 409 &&
    (normalizedCode === 'CPF_JA_CADASTRADO' ||
      normalized.includes('cpf') && normalized.includes('cadastrado'))
  ) {
    return 'Este CPF já está cadastrado em outra conta. Verifique os dados ou entre em contato com o suporte.'
  }

  if (status === 400) {
    if (
      normalized.includes('formato de imagem') ||
      normalized.includes('imagem invalida') ||
      normalized.includes('imagem inválida') ||
      normalized.includes('indecodific') ||
      normalized.includes('heic') ||
      normalized.includes('heif')
    ) {
      return UNSUPPORTED_IMAGE_MESSAGE
    }

    if (
      normalized.includes('usuarioid') ||
      normalized.includes('required request parameter') ||
      normalized.includes('missing servlet request parameter')
    ) {
      return 'Nao foi possivel concluir a publicacao agora. Atualize a pagina e tente novamente.'
    }

    if (
      normalized.includes('titulo') ||
      normalized.includes('categoria') ||
      normalized.includes('preco') ||
      normalized.includes('horario') ||
      normalized.includes('cidade') ||
      normalized.includes('bairro') ||
      normalized.includes('servico') ||
      normalized.includes('local de atendimento') ||
      normalized.includes('foto')
    ) {
      return 'Complete os campos obrigatorios destacados antes de publicar.'
    }

    if (
      normalized.includes('aprov') ||
      normalized.includes('documento') ||
      normalized.includes('verifica') ||
      normalized.includes('analise')
    ) {
      return 'Confirme seus dados pessoais e envie os documentos obrigatorios antes de publicar.'
    }

    return hasSpecificBackendMessage(status, message)
      ? message
      : 'Nao foi possivel concluir a publicacao agora.'
  }

  if (status === 401 || status === 403) {
    return 'Sua sessao precisa ser validada novamente antes de publicar.'
  }

  if (status >= 500) {
    return 'Nao foi possivel concluir a publicacao agora.'
  }

  return message || 'Nao foi possivel concluir a publicacao agora.'
}

function hasSpecificBackendMessage(status: number, message: string) {
  const trimmed = String(message || '').trim()
  if (!trimmed) return false
  return trimmed.toLowerCase() !== `erro ${status}`.toLowerCase()
}

function csrfCookieName() {
  return ['XSRF', 'TOKEN'].join('-')
}

function csrfHeaderName() {
  return ['X', 'XSRF', 'TOKEN'].join('-')
}

function readCsrfValue() {
  if (typeof document === 'undefined') return null
  const name = csrfCookieName()
  const entry = document.cookie.split('; ').find((item) => item.startsWith(`${name}=`))
  return entry ? decodeURIComponent(entry.slice(name.length + 1)) : null
}

export async function submitWizardAnuncio(
  state: WizardFormState,
  usuario: { username: string; nomeCompleto: string | null; email: string }
) {
  const csrf = readCsrfValue()
  const descricao = state.descricao.trim() || state.descricaoPerfil.trim()
  const res = await fetch(`${apiBase()}/anunciar`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(csrf ? { [csrfHeaderName()]: csrf } : {}),
    },
    body: JSON.stringify({
      nomeExibicao: usuario.nomeCompleto?.trim() || usuario.username,
      email: usuario.email,
      whatsapp: state.whatsapp.trim(),
      uf: state.estadoUf,
      cidade: state.cidadeNome,
      bairro: state.bairroNome.trim() || null,
      titulo: state.titulo.trim(),
      descricao,
      preco: Number(precoParaNumero(state.preco)),
      categoria: state.categoria,
      aceiteTermos: true,
      confirmacaoIdade: true,
    }),
  })
  return readResponse<{ anuncioId: string; slugLocal: string }>(res, 'publicar_anuncio')
}

export async function updateWizardProfileDescription(input: {
  email: string
  descricaoPerfil: string
}) {
  const res = await fetch(`${apiBase()}/usuarios/${encodeURIComponent(input.email)}/editar`, {
    method: 'PUT',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      descricaoPerfil: input.descricaoPerfil,
    }),
  })

  return readResponse(res, 'atualizar_descricao_perfil')
}

export async function completeWizardKyc(input: {
  email: string
  nomeCompleto: string
  dataNascimento: string
  cpf: string
  estadoId: string
  cidadeId: string
  bairroId: string
  documentos: File[]
}) {
  const fd = new FormData()
  fd.append('email', input.email)
  fd.append('nomeCompleto', input.nomeCompleto.trim())
  fd.append('dataNascimento', input.dataNascimento)
  fd.append('cpf', input.cpf.replace(/\D/g, ''))
  fd.append('estadoId', input.estadoId)
  fd.append('cidadeId', input.cidadeId)
  fd.append('bairroId', input.bairroId)
  input.documentos.forEach((file) => fd.append('documentos', file))

  console.info('[KYC_PUBLICAR_FORMDATA]', {
    etapa: 'completar_cadastro',
    campos: {
      email: Boolean(input.email),
      nomeCompleto: Boolean(input.nomeCompleto.trim()),
      dataNascimento: Boolean(input.dataNascimento),
      cpf: input.cpf.replace(/\D/g, '').length === 11,
      estadoId: Boolean(input.estadoId),
      cidadeId: Boolean(input.cidadeId),
      bairroId: Boolean(input.bairroId),
    },
    documentosFieldName: 'documentos',
    documentosCount: input.documentos.length,
    documentos: describeFiles(input.documentos),
  })

  const res = await fetch(`${apiBase()}/usuarios/completar-cadastro`, {
    method: 'POST',
    credentials: 'include',
    body: fd,
  })
  return readResponse(res, 'completar_cadastro')
}
