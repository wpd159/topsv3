const ADMIN_LABELS: Record<string, string> = {
  ABERTA: "aberta",
  ADMIN: "Admin",
  ANUNCIO: "anuncio",
  ANUNCIO_REMETIDO_REVISAO: "anuncio remetido para revisao",
  ANUNCIO_TOPO: "anuncio no topo",
  APROVADA: "aprovada",
  ATIVO: "ativo",
  BENEFICIO_ATIVO: "beneficio ativo",
  BENEFICIO_EXPIRADO: "beneficio expirado",
  BENEFICIO_EXPIRADO_ANTES_DO_GRUPO: "beneficio expirado antes do grupo",
  BENEFICIO_PENDENTE: "beneficio pendente",
  BENEFICIO_SEM_GRUPO: "beneficio sem grupo",
  BENEFICIO_VENCE_EM_BREVE: "beneficio vence em breve",
  BLOQUEADO: "bloqueado",
  COMERCIAL: "Comercial",
  CRIACAO: "criacao",
  DATA_INVALIDA: "data invalida",
  DESTAQUE: "destaque",
  EMAIL: "e-mail",
  EM_ANALISE: "em analise",
  ANUNCIANTE_VINCULADA_AO_ANUNCIO: "anunciante vinculada ao anuncio",
  EQUIPE_MODERACAO_LOCAL: "equipe de moderacao local",
  EXPIRADO: "expirado",
  FOTO: "foto",
  FOTOS_EXTRA: "fotos extra",
  GRUPO_EXPIRADO_COM_BENEFICIO_ATIVO: "grupo expirado com beneficio ativo",
  GRUPO_SEM_BENEFICIOS: "grupo sem beneficios",
  LIVRE: "livre",
  MIDIA: "midia",
  MODERACAO_MIDIA_REPROVADA: "midia reprovada pela moderacao",
  MODERACAO_REPROVADA: "moderacao reprovada",
  MODERACAO_SOLICITAR_AJUSTE: "moderacao com ajuste solicitado",
  MODERADOR: "Moderador",
  ORIGEM_DESCONHECIDA: "origem desconhecida",
  PENDENTE: "pendente",
  PENDENTE_REVISAO: "pendente de revisao",
  PREMIUM_OK: "premium ok",
  PROCESSADO: "processado",
  PUBLICAVEL: "publicavel",
  PUBLICADO: "publicado",
  REJEITADA: "rejeitada",
  RELATORIO: "relatorio",
  SMS: "SMS",
  STORIES: "Stories",
  USUARIO: "Usuario",
  VENCENDO: "vencendo",
  VIDEO: "video",
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
