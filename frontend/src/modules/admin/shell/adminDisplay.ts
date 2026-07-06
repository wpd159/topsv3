const ADMIN_LABELS: Record<string, string> = {
  ABERTA: "aberta",
  ADMIN: "Admin",
  ANUNCIO: "anuncio",
  ANUNCIO_REMETIDO_REVISAO: "anuncio remetido para revisao",
  APROVADA: "aprovada",
  BLOQUEADO: "bloqueado",
  COMERCIAL: "Comercial",
  CRIACAO: "criacao",
  EMAIL: "e-mail",
  EM_ANALISE: "em analise",
  ANUNCIANTE_VINCULADA_AO_ANUNCIO: "anunciante vinculada ao anuncio",
  EQUIPE_MODERACAO_LOCAL: "equipe de moderacao local",
  FOTO: "foto",
  LIVRE: "livre",
  MIDIA: "midia",
  MODERACAO_MIDIA_REPROVADA: "midia reprovada pela moderacao",
  MODERACAO_REPROVADA: "moderacao reprovada",
  MODERACAO_SOLICITAR_AJUSTE: "moderacao com ajuste solicitado",
  MODERADOR: "Moderador",
  PENDENTE: "pendente",
  PENDENTE_REVISAO: "pendente de revisao",
  PROCESSADO: "processado",
  PUBLICAVEL: "publicavel",
  PUBLICADO: "publicado",
  REJEITADA: "rejeitada",
  SMS: "SMS",
  USUARIO: "Usuario",
  WHATSAPP: "WhatsApp"
};

export function formatAdminValue(value: string | null | undefined, fallback = "sem informacao"): string {
  if (!value) {
    return fallback;
  }
  const normalized = value.trim();
  if (!normalized) {
    return fallback;
  }
  return ADMIN_LABELS[normalized] ?? normalized.replace(/_/g, " ").toLowerCase();
}

export function formatAdminValues(values: readonly string[] | null | undefined, fallback = "sem informacao"): string {
  if (!values?.length) {
    return fallback;
  }
  return values.map((item) => formatAdminValue(item)).join(", ");
}

export function formatAdminText(value: string | null | undefined, fallback = "sem informacao"): string {
  if (!value) {
    return fallback;
  }
  let formatted = value;
  for (const [code, label] of Object.entries(ADMIN_LABELS)) {
    formatted = formatted.replaceAll(code, label);
  }
  return formatted;
}
