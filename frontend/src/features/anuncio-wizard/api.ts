import type { WizardFormState, WizardKycState } from './types'
import { UNSUPPORTED_IMAGE_MESSAGE } from '@/utils/image-upload'
import { isValidCpf } from '@/lib/cpf-mask'
import { birthDateToIso } from '@/lib/date/birth-date'
import { publicApiUrl } from '@/lib/api-contract'

export type WizardCategoryOption = {
  value: string
  label: string
}

function precoParaNumero(value: string) {
  const digits = String(value || '').replace(/\D/g, '')
  if (!digits) return ''
  return (Number(digits) / 100).toFixed(2)
}

async function readResponse<T>(res: Response, etapa: string): Promise<T> {
  const raw = await res.text().catch(() => '')
  if (!res.ok) {
    let message = raw || `Erro ${res.status}`
    let code = ''
    try {
      const parsed = JSON.parse(raw)
      message = parsed.mensagem || parsed.message || parsed.detail || parsed.error || message
      code = parsed.codigo || parsed.code || ''
    } catch {}
    throw new Error(mapWizardApiError(res.status, message, code, etapa))
  }
  return raw ? (JSON.parse(raw) as T) : (null as T)
}

function mapWizardApiError(status: number, message: string, code = '', etapa = '') {
  const normalized = String(message || '').trim().toLowerCase()
  const normalizedCode = String(code || '').trim().toUpperCase()
  const documentMessages = [
    'O arquivo deve estar em PDF.',
    'O PDF excede o tamanho permitido.',
    'Não foi possível ler o PDF enviado.',
    'Preencha os dados obrigatórios.',
    'Não foi possível enviar o documento. Tente novamente.',
    'Selecione a imagem da frente do documento.',
    'Selecione também o verso deste documento.',
    'A imagem deve estar em JPG ou PNG.',
    'A imagem excede o tamanho permitido.',
    'Não foi possível ler a imagem enviada.',
  ]
  const specificDocumentMessage = documentMessages.find(
    (candidate) => candidate.toLocaleLowerCase('pt-BR') === normalized
  )
  if (specificDocumentMessage) return specificDocumentMessage

  if (status === 400 && normalizedCode === 'CPF_INVALIDO') {
    return 'Informe um CPF válido.'
  }

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
    if (etapa === 'enviar_kyc') {
      return 'Não foi possível enviar o documento. Tente novamente.'
    }
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

export async function submitWizardAnuncio(state: WizardFormState) {
  const csrf = readCsrfValue()
  const descricao = state.descricao.trim() || state.descricaoPerfil.trim()
  const res = await fetch(publicApiUrl('/anunciar'), {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(csrf ? { [csrfHeaderName()]: csrf } : {}),
    },
    body: JSON.stringify({
      uf: state.estadoUf,
      cidade: state.cidadeNome,
      bairro: state.bairroNome.trim() || null,
      enderecoResumido: state.pontoReferenciaTexto.trim() || null,
      titulo: state.titulo.trim(),
      descricao,
      preco: Number(precoParaNumero(state.preco)),
      categoria: state.categoria,
      servicos: state.servicos,
      linkConteudo: state.linkConteudo.trim() || null,
      atendimentoExclusivamenteVirtual: state.atendimentoExclusivamenteVirtual,
      aceiteTermos: true,
      confirmacaoIdade: true,
    }),
  })
  return readResponse<{ anuncioId: string; slugLocal: string }>(res, 'publicar_anuncio')
}

export async function fetchWizardCategories(): Promise<WizardCategoryOption[]> {
  const res = await fetch(publicApiUrl('/categorias-home'), {
    credentials: 'include',
    cache: 'no-store',
  })
  const payload = await readResponse<Array<{
    identificador: string
    titulo: string
    ativo: boolean
  }>>(res, 'consultar_categorias')
  return payload
    .filter((item) => item.ativo && item.identificador?.trim() && item.titulo?.trim())
    .map((item) => ({
      value: item.identificador.trim(),
      label: item.identificador.trim() === 'VENDA_DE_CONTEUDO'
        ? 'Atendimento Virtual'
        : item.titulo.trim(),
    }))
}

export type WizardKycStatus = {
  status: 'NAO_INICIADO' | 'PENDENTE' | 'EM_ANALISE' | 'APROVADO' | 'REJEITADO' | 'AJUSTE_SOLICITADO'
  nomeCivil: string | null
  cpfPreenchido: boolean
  cpfMascarado: string | null
  dataNascimento: string | null
  motivo: string | null
  prontoParaEnviarAnuncio: boolean
  podeReenviar: boolean
  tamanhoMaximoBytes: number
  formatosAceitos: string[]
  documentos: Array<{
    id: string
    parte: 'UNICO' | 'FRENTE' | 'VERSO'
    status: string
    mimeType: string | null
    tamanhoBytes: number
  }>
}

export async function fetchWizardKycStatus() {
  const res = await fetch(publicApiUrl('/minha-conta/kyc'), {
    credentials: 'include',
    cache: 'no-store',
  })
  return readResponse<WizardKycStatus>(res, 'consultar_kyc')
}

export async function submitWizardKyc(input: WizardKycState) {
  const fd = new FormData()
  if (input.nomeCompleto.trim()) fd.append('nomeCivil', input.nomeCompleto.trim())
  const nascimentoIso = birthDateToIso(input.dataNascimento)
  if (nascimentoIso) fd.append('dataNascimento', nascimentoIso)
  if (input.cpf.trim()) {
    if (!isValidCpf(input.cpf)) throw new Error('Informe um CPF válido.')
    fd.append('cpf', input.cpf.replace(/\D/g, ''))
  }
  if (!input.documentoModo) throw new Error('Escolha o formato do documento.')
  fd.append('modoDocumento', input.documentoModo)
  if (input.documentoModo === 'PDF') {
    if (input.documentos[0]) fd.append('documentoUnico', input.documentos[0])
  } else {
    if (input.documentos[0]) fd.append('documentoFrente', input.documentos[0])
    if (input.documentos[1]) fd.append('documentoVerso', input.documentos[1])
  }
  const csrf = readCsrfValue()
  let res: Response
  try {
    res = await fetch(publicApiUrl('/minha-conta/kyc'), {
      method: 'POST',
      credentials: 'include',
      headers: csrf ? { [csrfHeaderName()]: csrf } : undefined,
      body: fd,
    })
  } catch {
    throw new Error('Não foi possível enviar o documento. Tente novamente.')
  }
  return readResponse<WizardKycStatus>(res, 'enviar_kyc')
}
