export const wizardStepIds = ['perfil', 'localizacao', 'servicos', 'fotos', 'revisao', 'premium', 'kyc'] as const

export type WizardStepId = (typeof wizardStepIds)[number]

export type WizardFormState = {
  titulo: string
  categoria: string
  descricaoPerfil: string
  preco: string
  horario: string
  locaisAtendimento: string[]
  servicos: string[]
  descricao: string
  linkConteudo: string
  whatsapp: string
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
  premiumChoice: 'gratis' | 'destaque'
}

export type WizardKycState = {
  nomeCompleto: string
  dataNascimento: string
  cpf: string
  documentoModo: 'FRENTE_VERSO' | 'PDF'
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
  descricao: '',
  linkConteudo: '',
  whatsapp: '',
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
  premiumChoice: 'gratis',
}

export const initialWizardKycState: WizardKycState = {
  nomeCompleto: '',
  dataNascimento: '',
  cpf: '',
  documentoModo: 'FRENTE_VERSO',
  documentos: [],
  documentoNomes: [],
}

export const initialWizardState: WizardState = {
  currentStep: 'perfil',
  form: initialWizardFormState,
  kyc: initialWizardKycState,
}
