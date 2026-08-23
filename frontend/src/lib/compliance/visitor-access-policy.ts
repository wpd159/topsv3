export type EscopoAcessoVisitante =
  | "MIDIA_RESTRITA"
  | "WHATSAPP"
  | "STORY"
  | "CONTEUDO_EXPLICITO"

export type NivelAcessoVisitante = "LIGHT" | "REINFORCED" | "STRONG"

export type StatusEscopoVisitante = {
  globalAccepted?: boolean
  verified: boolean
  level?: string | null
  expiresAt?: string | null
  explicitVerified?: boolean
  explicitLevel?: string | null
  explicitExpiresAt?: string | null
}

const NIVEL: Record<string, number> = {
  NONE: 0,
  LIGHT: 1,
  REINFORCED: 2,
  STRONG: 3,
}

function nivelSuficiente(atual: string | null | undefined, exigido: NivelAcessoVisitante) {
  return (NIVEL[atual ?? "NONE"] ?? 0) >= NIVEL[exigido]
}

function expiracaoValida(expiraEm: string | null | undefined, agora: number) {
  if (!expiraEm) return false
  const instante = Date.parse(expiraEm)
  return Number.isFinite(instante) && instante > agora
}

export function statusSatisfazEscopo(
  status: StatusEscopoVisitante,
  scope: EscopoAcessoVisitante,
  requiredLevel: NivelAcessoVisitante,
  agora = Date.now(),
): boolean {
  if (!status.globalAccepted) return false

  if (scope === "CONTEUDO_EXPLICITO") {
    return Boolean(
      status.explicitVerified
      && nivelSuficiente(status.explicitLevel, requiredLevel)
      && expiracaoValida(status.explicitExpiresAt, agora),
    )
  }

  return Boolean(
    status.verified
    && nivelSuficiente(status.level, requiredLevel)
    && expiracaoValida(status.expiresAt, agora),
  )
}
