import type { WizardStepId } from './types'

export const categorias = [
  { value: 'ACOMPANHANTE_FEMININA', label: 'Acompanhante feminina' },
  { value: 'ACOMPANHANTE_MASCULINO', label: 'Acompanhante masculino' },
  { value: 'TRANSEX_TRAVESTIS', label: 'Trans / Travestis' },
  { value: 'MASSAGENS', label: 'Massagens' },
  { value: 'VENDA_DE_CONTEUDO', label: 'Sexo virtual' },
]

export const horarios = [
  { value: 'MANHA', label: 'Manhã' },
  { value: 'TARDE', label: 'Tarde' },
  { value: 'NOITE', label: 'Noite' },
  { value: 'QUALQUER_HORARIO', label: 'Qualquer horário' },
]

export const locais = [
  { value: 'A_COMBINAR', label: 'A combinar' },
  { value: 'HOTEL_MOTEL', label: 'Hotel/Motel' },
  { value: 'MEU_LOCAL', label: 'Meu local' },
]

export const servicos = [
  { value: 'ANAL', label: 'Anal' },
  { value: 'ATIVO', label: 'Ativo' },
  { value: 'PASSIVO', label: 'Passivo' },
  { value: 'ORAL', label: 'Oral' },
  { value: 'MASSAGEM_TANTRICA', label: 'Massagem tântrica' },
  { value: 'MASSAGEM_EROTICA', label: 'Massagem erótica' },
  { value: 'FETICHES', label: 'Fetiches' },
  { value: 'BDSM', label: 'BDSM' },
  { value: 'NAMORADAS', label: 'Namorados' },
  { value: 'TRIO', label: 'Trio' },
  { value: 'VIDEOCHAMADA', label: 'Sexo virtual' },
  { value: 'JOGOS_DE_INTERPRETACAO', label: 'Jogos de interpretação' },
]

export const stepCopy: Record<WizardStepId, { title: string; description: string }> = {
  perfil: {
    title: 'Conte um pouco sobre você',
    description:
      'Uma boa descrição ajuda clientes a conhecerem seu estilo e entrarem em contato com mais confiança.',
  },
  localizacao: {
    title: 'Onde você atende?',
    description:
      'Informe o estado, a cidade e o bairro onde você atende. Isso ajuda as pessoas da sua região a encontrarem seu anúncio.',
  },
  servicos: {
    title: 'Mostre como será a experiência',
    description:
      'Preço, horários, locais e serviços ajudam o cliente a entender seu atendimento sem precisar adivinhar.',
  },
  fotos: {
    title: 'Escolha fotos que valorizem seu perfil',
    description:
      'Perfis com boas fotos costumam receber mais visualizações e transmitir mais confiança.',
  },
  revisao: {
    title: 'Revise seu anúncio',
    description:
      'Confira as informações abaixo antes de enviar. Você ainda pode voltar e fazer alterações.',
  },
  premium: {
    title: 'Escolha como deseja publicar',
    description:
      'Você pode publicar gratuitamente ou adicionar benefícios pagos ao anúncio. No seu primeiro anúncio, será necessário confirmar sua identidade antes do envio para moderação.',
  },
  kyc: {
    title: 'Confirme seus dados para finalizar',
    description:
      'Seus dados e documentos são privados e usados somente para validar sua identidade e maioridade.',
  },
}

export type SearchableSelectOption = {
  id: string
  label: string
  searchLabel?: string
  subtitle?: string
}
