 export const normalizarCategoria = (categoria: string | null) => {
    if (!categoria) return '-'
    const map: Record<string, string> = {
      ACOMPANHANTE_FEMININA: 'Acompanhante Feminina',
      ACOMPANHANTE_MASCULINO: 'Acompanhante Masculino',
      ENCONTROS_CASUAIS: 'Encontros Casuais',
      MASSAGENS: 'Massagens',
      TRANSEX_TRAVESTIS: 'Transex / Travestis',
      VENDA_DE_CONTEUDO: 'Sexo Virtual',
    }
    return map[categoria] || categoria
  }

  // Normaliza local de atendimento
 export const normalizarLocal = (valor: string | null) => {
    if (!valor) return '-'
    const map: Record<string, string> = {
      A_COMBINAR: 'A combinar',
      HOTEL_MOTEL: 'Hotel/Motel',
      MEU_LOCAL: 'Meu Local',
    }
    return map[valor] || valor
  }

  // Normaliza o status do ticket
export const normalizarStatusTicket = (valor: string | null) => {
  if (!valor) return '-'
  const map: Record<string, string> = {
    ABERTO: 'Aberto',
    EM_ANDAMENTO: 'Em Andamento',
    FECHADO: 'Fechado',
  }
  return map[valor] || valor
}

  // Normaliza o status do ticket
export const normalizarStaff = (valor: string | null) => {
  if (!valor) return '-'
  const map: Record<string, string> = {
    ADMIN: 'Administrador',
    MODERADOR: 'Moderador',
  }
  return map[valor] || valor
}

// 🔹 Normaliza o status do pagamento
export const normalizarStatusPagamento = (valor: string | null) => {
  if (!valor) return '-'
  const map: Record<string, string> = {
    CREATED: 'Pendente',
    PENDING: 'Pendente',
    ATIVA: 'Pendente',
    ACTIVE: 'Pendente',
    CRIADO_LOCALMENTE: 'Pendente',
    APPROVED: 'Aprovado',
    CONCLUIDA: 'Aprovado',
    COMPLETED: 'Aprovado',
    PAID: 'Aprovado',
    REJECTED: 'Rejeitado',
    CANCELLED: 'Cancelado',
    CANCELED: 'Cancelado',
    EXPIRED: 'Cancelado',
    EXPIRADA: 'Cancelado',
    ERROR: 'Erro',
    ERRO: 'Erro',
  }
  const upper = valor.toUpperCase()
  return map[upper] || valor
}

export const normalizarHorario = (valor: string | null) => {
    if (!valor) return '-'
    const map: Record<string, string> = {
      MANHA: 'Manhã',
      TARDE: 'Tarde',
      NOITE: 'Noite',
      QUALQUER_HORARIO: 'Qualquer Horário',
    }
    return map[valor] || valor
  }

  // Lista de serviços disponíveis (para exibir checkboxes)
 export const servicosDisponiveis = [
    { label: 'Anal', value: 'ANAL' },
    { label: 'Ativo', value: 'ATIVO' },
    { label: 'Passivo', value: 'PASSIVO' },
    { label: 'Massagem Tântrica', value: 'MASSAGEM_TANTRICA' },
    { label: 'Massagem Erótica', value: 'MASSAGEM_EROTICA' },
    { label: 'Fetiches', value: 'FETICHES' },
    { label: 'Oral', value: 'ORAL' },
    { label: 'BDSM', value: 'BDSM' },
    { label: 'Namorados', value: 'NAMORADAS' },
    { label: 'Trio', value: 'TRIO' },
    { label: 'Sexo virtual', value: 'VIDEOCHAMADA' },
    { label: 'Jogos de Interpretação', value: 'JOGOS_DE_INTERPRETACAO' },
    { label: 'Atriz Pornô', value: 'ATRIZ_PORNO' },
    { label: 'Ator Pornô', value: 'ATOR_PORNO' },
    { label: 'Ejaculação Corporal', value: 'EJACULACAO_CORPORAL' },
  ]
