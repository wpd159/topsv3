export const wizardStepIds = ['perfil', 'localizacao', 'servicos', 'fotos', 'revisao', 'premium', 'kyc'] as const

export type WizardStepId = (typeof wizardStepIds)[number]

export type EditPendingMedia = { file: File; kind: 'photo' | 'video' }

export type WizardFormState = {
  titulo: string
  categoria: string
  descricaoPerfil: string
  preco: string
  horario: string
  locaisAtendimento: string[]
  servicos: string[]
  atendimentoExclusivamenteVirtual: boolean
  descricao: string
  linkConteudo: string
  estadoId: string
  cidadeId: string
  bairroId: string
  estadoNome: string
  estadoUf: string
  cidadeNome: string
  bairroNome: string
  pontoReferenciaTexto: string
  fotos: File[]
  fotoNomes: string[]
  videos: File[]
  videoNomes: string[]
  editPendingMedia: EditPendingMedia[]
  editPendingMediaScope: string | null
  editUploadUnconfirmed: boolean
  premiumChoice: 'gratis' | 'destaque'
}

export type WizardKycState = {
  nomeCompleto: string
  dataNascimento: string
  cpf: string
  documentoModo: 'FRENTE_VERSO' | 'PDF' | null
  documentos: File[]
  documentoNomes: string[]
}

export type WizardState = {
  currentStep: WizardStepId
  form: WizardFormState
  kyc: WizardKycState
}

export const initialWizardFormState: WizardFormState = {
  titulo: '',
  categoria: '',
  descricaoPerfil: '',
  preco: '',
  horario: '',
  locaisAtendimento: [],
  servicos: [],
  atendimentoExclusivamenteVirtual: false,
  descricao: '',
  linkConteudo: '',
  estadoId: '',
  cidadeId: '',
  bairroId: '',
  estadoNome: '',
  estadoUf: '',
  cidadeNome: '',
  bairroNome: '',
  pontoReferenciaTexto: '',
  fotos: [],
  fotoNomes: [],
  videos: [],
  videoNomes: [],
  editPendingMedia: [],
  editPendingMediaScope: null,
  editUploadUnconfirmed: false,
  premiumChoice: 'gratis',
}

export const initialWizardKycState: WizardKycState = {
  nomeCompleto: '',
  dataNascimento: '',
  cpf: '',
  documentoModo: null,
  documentos: [],
  documentoNomes: [],
}

export const initialWizardState: WizardState = {
  currentStep: 'perfil',
  form: initialWizardFormState,
  kyc: initialWizardKycState,
}
