const DATA_BENEFICIO = new Intl.DateTimeFormat('pt-BR', {
  timeZone: 'America/Sao_Paulo',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

export function formatarDataBeneficio(value) {
  if (!value || !Number.isFinite(Date.parse(value))) return null
  return DATA_BENEFICIO.format(new Date(value)).replace(',', ' às')
}

export function apresentarBeneficioPremium(beneficio) {
  if (beneficio.status === 'DISPONIVEL_PARA_PUBLICAR') {
    return apresentarBeneficioPremium({
      ...beneficio,
      status: 'AGUARDANDO_MODERACAO',
      motivoEspera: 'AGUARDANDO_PUBLICACAO_STORY',
    })
  }
  if (beneficio.status === 'AGUARDANDO_MODERACAO') {
    if (beneficio.motivoEspera === 'AGUARDANDO_PUBLICACAO_STORY') {
      return {
        situacao: 'Disponível para publicar Story',
        complemento: 'O prazo começa quando o Story for publicado.',
      }
    }
    return {
      situacao: 'Aguardando aprovação da moderação',
      complemento: 'O prazo ainda não começou.',
    }
  }
  const fim = formatarDataBeneficio(beneficio.fimEm)
  if (beneficio.status === 'ATIVO' && fim) {
    const dias = beneficio.diasRestantes
    return {
      situacao: `Ativo até ${fim}`,
      complemento: Number.isInteger(dias) && dias > 0
        ? `Restam ${dias} ${dias === 1 ? 'dia' : 'dias'}.`
        : null,
    }
  }
  if (beneficio.status === 'EXPIRADO' && fim) {
    return { situacao: `Expirou em ${fim}`, complemento: null }
  }
  if (beneficio.status === 'PENDENTE') {
    return { situacao: 'Ativação pendente', complemento: null }
  }
  if (beneficio.status === 'INATIVO') {
    return { situacao: 'Inativo', complemento: null }
  }
  return { situacao: 'Situação indisponível', complemento: null }
}
