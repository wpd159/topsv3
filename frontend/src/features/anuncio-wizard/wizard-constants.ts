import type { WizardStepId } from './types'

export const categorias = [
  { value: 'ACOMPANHANTE_FEMININA', label: 'Acompanhante feminina' },
  { value: 'ACOMPANHANTE_MASCULINO', label: 'Acompanhante masculino' },
  { value: 'TRANSEX_TRAVESTIS', label: 'Trans / Travestis' },
  { value: 'MASSAGENS', label: 'Massagens' },
  { value: 'VENDA_DE_CONTEUDO', label: 'Sexo Virtual' },
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
    title: 'Crie a primeira impressão do seu anúncio',
    description:
      'Dê nome ao anúncio, escolha a categoria e conte um pouco sobre você. É o começo da identidade que os clientes vão perceber.',
  },
  localizacao: {
    title: 'Defina sua área de atendimento',
    description:
      'Cidade, bairro e referência conhecida aparecem em um passo próprio para deixar tudo mais claro, leve e fácil de encontrar.',
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
    title: 'Revise o anúncio antes de enviar',
    description:
      'Faça uma última checagem com calma antes da etapa final. O resumo aqui já se aproxima do que será visto no site.',
  },
  premium: {
    title: 'Impulsione se quiser',
    description:
      'Publicar grátis continua disponível. O destaque aparece só no fim, como um upgrade opcional e sem pressão.',
  },
}

export type SearchableSelectOption = {
  id: string
  label: string
  searchLabel?: string
  subtitle?: string
}
