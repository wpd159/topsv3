const ADMIN_LABELS: Record<string, string> = {
  ABERTA: "aberta",
  ADMIN: "Admin",
  ADMIN_CONFIGURAR: "configurar administração",
  ANUNCIO: "anúncio",
  ANUNCIO_LER: "ler anúncios",
  ANUNCIO_MODERAR: "moderar anúncios",
  ANUNCIO_REMETIDO_REVISAO: "anúncio remetido para revisão",
  ANUNCIO_TOPO: "anúncio no topo",
  APROVADA: "aprovada",
  AUDITORIA_LER: "ler auditoria",
  ATIVO: "ativo",
  BENEFICIO_ATIVO: "benefício ativo",
  BENEFICIO_EXPIRADO: "benefício expirado",
  BENEFICIO_EXPIRADO_ANTES_DO_GRUPO: "benefício expirado antes do grupo",
  BENEFICIO_PENDENTE: "benefício pendente",
  BENEFICIO_SEM_GRUPO: "benefício sem grupo",
  BENEFICIO_VENCE_EM_BREVE: "benefício vence em breve",
  BLOQUEADO: "bloqueado",
  COMERCIAL: "Comercial",
  COMERCIAL_GERENCIAR: "gerenciar comercial",
  CRIACAO: "criação",
  DATA_INVALIDA: "data inválida",
  DESTAQUE: "destaque",
  DOCUMENTO_REVISAR: "revisar documentos",
  EMAIL: "e-mail",
  EM_ANALISE: "em análise",
  ANUNCIANTE_VINCULADA_AO_ANUNCIO: "anunciante vinculada ao anúncio",
  EQUIPE_MODERACAO_LOCAL: "equipe de moderação",
  EXPIRADO: "expirado",
  FINANCEIRO_LER: "ler financeiro",
  FOTO: "foto",
  FOTOS_EXTRA: "fotos extra",
  GRUPO_EXPIRADO_COM_BENEFICIO_ATIVO: "grupo expirado com benefício ativo",
  GRUPO_SEM_BENEFICIOS: "grupo sem benefícios",
  LIVRE: "livre",
  MIDIA: "mídia",
  MIDIA_REVISAR: "revisar mídia",
  MODERACAO_MIDIA_REPROVADA: "mídia reprovada pela moderação",
  MODERACAO_REPROVADA: "moderação reprovada",
  MODERACAO_SOLICITAR_AJUSTE: "moderação com ajuste solicitado",
  MODERADOR: "Moderador",
  ORIGEM_DESCONHECIDA: "origem desconhecida",
  PENDENTE: "pendente",
  PENDENTE_REVISAO: "pendente de revisão",
  PREMIUM_OK: "premium ok",
  PROCESSADO: "processado",
  PUBLICAVEL: "publicável",
  PUBLICADO: "publicado",
  REJEITADA: "rejeitada",
  RELATORIO: "relatório",
  SMS: "SMS",
  SEGURANCA_GERENCIAR: "gerenciar segurança",
  SUPORTE_ATENDER: "atender suporte",
  STORIES: "Stories",
  USUARIO: "Usuário",
  VENCENDO: "vencendo",
  VIDEO: "video",
  WHATSAPP: "WhatsApp"
};

export function formatAdminValue(value: string | null | undefined, fallback = "sem informação"): string {
  if (!value) {
    return fallback;
  }
  const normalized = value.trim();
  if (!normalized) {
    return fallback;
  }
  return cleanAdminCopy(ADMIN_LABELS[normalized] ?? normalized.replace(/_/g, " ").toLowerCase());
}

export function formatAdminValues(values: readonly string[] | null | undefined, fallback = "sem informação"): string {
  if (!values?.length) {
    return fallback;
  }
  return values.map((item) => formatAdminValue(item)).join(", ");
}

export function formatAdminText(value: string | null | undefined, fallback = "sem informação"): string {
  if (!value) {
    return fallback;
  }
  let formatted = value;
  for (const [code, label] of Object.entries(ADMIN_LABELS).sort(([left], [right]) => right.length - left.length)) {
    formatted = formatted.replaceAll(code, label);
  }
  return cleanAdminCopy(formatted);
}

function cleanAdminCopy(value: string): string {
  return value
    .replace(/\bPreparar autorização futura comercial\./gi, "Preparar autorização comercial futura.")
    .replace(/\bPreparar autorizacao futura comercial\./gi, "Preparar autorização comercial futura.")
    .replace(/\bPreparar autorização futura de revisão de documentos\./gi, "Preparar revisão futura de documentos.")
    .replace(/\bPreparar autorizacao futura de revisao de documentos\./gi, "Preparar revisão futura de documentos.")
    .replace(/\bPreparar autorização futura de suporte\./gi, "Preparar suporte futuro.")
    .replace(/\bPreparar autorizacao futura de suporte\./gi, "Preparar suporte futuro.")
    .replace(/\bLer metadados administrativos futuros de anúncios\./gi, "Ler informações administrativas futuras de anúncios.")
    .replace(/\bLer metadados administrativos futuros de anuncios\./gi, "Ler informações administrativas futuras de anúncios.")
    .replace(/\bautorizacao\b/gi, "autorização")
    .replace(/\brevisao\b/gi, "revisão")
    .replace(/\banuncios\b/gi, "anúncios")
    .replace(/\banuncio\b/gi, "anúncio")
    .replace(/\bpermissoes\b/gi, "permissões")
    .replace(/\bpapeis\b/gi, "papéis")
    .replace(/\bacoes\b/gi, "ações")
    .replace(/\bacao\b/gi, "ação")
    .replace(/\bbeneficios\b/gi, "benefícios")
    .replace(/\bpublicos\b/gi, "públicos")
    .replace(/\bseguranca\b/gi, "segurança")
    .replace(/\badministracao\b/gi, "administração")
    .replace(/\bcritica\b/gi, "crítica")
    .replace(/anuncio-sintetico(?:-[a-z0-9]+)*/gi, "anúncio de demonstração")
    .replace(/perfil\s+sint[eé]tico/gi, "perfil de demonstração")
    .replace(/an[uú]ncio\s+sint[eé]tico/gi, "anúncio de demonstração")
    .replace(/\bsint[eé]tic[oa]s?\b/gi, "de demonstração")
    .replace(/\blocal(?:mente)?\b/gi, "")
    .replace(/\blocais\b/gi, "")
    .replace(/\s{2,}/g, " ")
    .trim();
}
