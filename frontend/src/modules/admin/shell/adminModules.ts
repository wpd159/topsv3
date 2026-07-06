export type AdminModule = {
  slug: string;
  title: string;
  summary: string;
  futureRoles: string[];
};

export const adminModules: AdminModule[] = [
  {
    slug: "moderacao",
    title: "Moderação",
    summary: "Fila futura para revisão de anúncios, mídia e decisões de conteúdo.",
    futureRoles: ["ADMIN", "MODERADOR"]
  },
  {
    slug: "anuncios",
    title: "Anúncios",
    summary: "Mapa estrutural para gestão futura de anúncios sem carregar dados reais.",
    futureRoles: ["ADMIN", "MODERADOR", "COMERCIAL"]
  },
  {
    slug: "usuarios",
    title: "Usuários",
    summary: "Área placeholder para administração futura de contas, papéis e sessões.",
    futureRoles: ["ADMIN"]
  },
  {
    slug: "midia",
    title: "Mídia",
    summary: "Estrutura futura para revisão e operação de arquivos públicos e privados.",
    futureRoles: ["ADMIN", "MODERADOR"]
  },
  {
    slug: "premium",
    title: "Premium",
    summary: "Área para benefícios, ativações e regras futuras de destaque.",
    futureRoles: ["ADMIN", "COMERCIAL"]
  },
  {
    slug: "desempenho",
    title: "Desempenho",
    summary: "Prova de resultado com views, cliques WhatsApp e comparativo Premium sem promessa garantida.",
    futureRoles: ["ADMIN", "COMERCIAL"]
  },
  {
    slug: "creditos",
    title: "Créditos",
    summary: "Mapa futuro para saldos, movimentos e ajustes auditáveis.",
    futureRoles: ["ADMIN", "COMERCIAL"]
  },
  {
    slug: "financeiro",
    title: "Financeiro",
    summary: "Estrutura para pagamentos, conciliação e Pix em fases futuras.",
    futureRoles: ["ADMIN"]
  },
  {
    slug: "seo",
    title: "SEO",
    summary: "Área futura para URLs, metadados, sitemap, robots e conteúdo público.",
    futureRoles: ["ADMIN", "COMERCIAL"]
  },
  {
    slug: "banners",
    title: "Banners",
    summary: "Área para espaços, versões e publicação revisada de banners.",
    futureRoles: ["ADMIN", "COMERCIAL"]
  },
  {
    slug: "comercial",
    title: "Comercial",
    summary: "Mapa futuro para contatos comerciais e acompanhamento operacional.",
    futureRoles: ["ADMIN", "COMERCIAL"]
  },
  {
    slug: "suporte",
    title: "Suporte",
    summary: "Área estrutural para tickets, mensagens e atendimento futuro.",
    futureRoles: ["ADMIN", "MODERADOR", "COMERCIAL"]
  },
  {
    slug: "backup",
    title: "Backup",
    summary: "Área para políticas, execuções e testes de restauração.",
    futureRoles: ["ADMIN"]
  },
  {
    slug: "auditoria",
    title: "Auditoria",
    summary: "Mapa futuro para eventos auditáveis e comandos críticos.",
    futureRoles: ["ADMIN"]
  }
];

export function findAdminModule(slug: string): AdminModule {
  const adminModule = adminModules.find((item) => item.slug === slug);
  if (!adminModule) {
    throw new Error(`Admin module not found: ${slug}`);
  }
  return adminModule;
}
