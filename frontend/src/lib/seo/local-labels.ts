export function labelAcompanhantesCidade(cidadeNome: string) {
  return `Acompanhantes em ${cidadeNome}`
}

export function labelGarotasProgramaCidade(cidadeNome: string) {
  return `Garotas de Programa em ${cidadeNome}`
}

export function labelAcompanhantesBairro(bairroNome: string) {
  const nomeNormalizado = bairroNome.trim().toLocaleLowerCase("pt-BR")

  if (nomeNormalizado.startsWith("setor ")) {
    return `Acompanhantes no ${bairroNome}`
  }

  if (
    nomeNormalizado.startsWith("vila ") ||
    nomeNormalizado.startsWith("zona ") ||
    nomeNormalizado.startsWith("região ") ||
    nomeNormalizado.startsWith("praia ")
  ) {
    return `Acompanhantes na ${bairroNome}`
  }

  return `Acompanhantes em ${bairroNome}`
}

export function labelAcompanhantesBairroCidade(bairroNome: string, cidadeNome: string) {
  return `${labelAcompanhantesBairro(bairroNome)}, ${cidadeNome}`
}

export function labelGarotasProgramaBairro(bairroNome: string) {
  return `Garotas de Programa no ${bairroNome}`
}
