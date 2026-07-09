const LABELS_CLASSIFICACAO: Record<string, string> = {
  SAFE_PUBLIC: "Conteudo live",
  PUBLICO_LIVRE: "Conteudo live",
  NAO_EXPLICITO: "Conteudo com seminudez",
  ADULT_NON_EXPLICIT: "Conteudo com seminudez",
  ADULTO_MODERADO: "Conteudo com seminudez",
  ADULT_RESTRICTED: "Conteudo com seminudez",
  ADULTO_RESTRITO: "Conteudo com seminudez",
  ADULT_EXPLICIT_BLOCKED: "Conteudo explicito",
  ADULTO_EXPLICITO_BLOQUEADO: "Conteudo explicito",
  EXPLICITO_BLOQUEADO: "Conteudo explicito",
}

const STATUS_VERIFICACAO: Record<string, string> = {
  NAO_INICIADO: "Verificacao nao iniciada",
  PENDENTE: "Verificacao pendente",
  EM_REVISAO: "Verificacao em analise",
  APROVADO: "Anunciante aprovado",
  REPROVADO: "Verificacao reprovada",
  SUSPENSO: "Conta suspensa para conteudo restrito",
}

function normalizarChave(value?: string | null) {
  return (value ?? "")
    .trim()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^A-Za-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .toUpperCase()
}

export function formatarClassificacaoConteudo(value?: string | null) {
  const chave = normalizarChave(value)
  if (!chave) return "Sem classificacao definida"
  return LABELS_CLASSIFICACAO[chave] ?? "Classificacao em revisao"
}

export function classificacaoEstaDefinida(value?: string | null) {
  return Boolean(normalizarChave(value))
}

export function formatarStatusVerificacaoAnunciante(value?: string | null) {
  const chave = normalizarChave(value)
  if (!chave) return STATUS_VERIFICACAO.NAO_INICIADO
  return STATUS_VERIFICACAO[chave] ?? "Status de verificacao indisponivel"
}

export function classificacaoExigeValidacaoManual(value?: string | null) {
  const chave = normalizarChave(value)
  return (
    chave === "ADULT_RESTRICTED" ||
    chave === "ADULTO_RESTRITO" ||
    chave === "ADULT_EXPLICIT_BLOCKED" ||
    chave === "ADULTO_EXPLICITO_BLOQUEADO"
  )
}

/**
 * Conteúdo que deve iniciar com blur/overlay na vitrine pública (cards SEO, thumbs),
 * alinhado a {@link midiaPublicaExigeProtecaoCompliance} quando só há classificação (sem flags da API).
 */
export function classificacaoIniciaComProtecao(value?: string | null) {
  const chave = normalizarChave(value)
  return chave === "ADULT_NON_EXPLICIT" || classificacaoExigeValidacaoManual(value)
}

/**
 * Regra única para mídia sensível no cliente: flags da API + classificações adultas (inclui seminudez).
 */
export function midiaPublicaExigeProtecaoCompliance(
  contentClassification?: string | null,
  requiresVisitorVerification?: boolean,
  requiresStrongVerification?: boolean
): boolean {
  const chave = normalizarChave(contentClassification)
  return (
    Boolean(requiresVisitorVerification) ||
    Boolean(requiresStrongVerification) ||
    chave === "ADULT_RESTRICTED" ||
    chave === "ADULTO_RESTRITO" ||
    chave === "ADULT_EXPLICIT_BLOCKED" ||
    chave === "ADULTO_EXPLICITO_BLOQUEADO" ||
    chave === "ADULT_NON_EXPLICIT"
  )
}
