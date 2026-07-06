import type { Metadata } from "next";

import type { AnuncioCardPublicoDto, AnuncioDetalhePublicoDto } from "../api/publicTypes";
import { localUrl, routeSegment } from "./localSeo";

const TITLE_SUFFIX = " | Tops do Job";
const MAX_DESCRIPTION_LENGTH = 155;
const FULL_DISPLAY_NAMES: Record<string, string> = {
  "anuncio-exemplo": "Anúncio Exemplo",
  "aparecida-de-goiania": "Aparecida de Goiânia",
  "belem": "Belém",
  "belo-horizonte": "Belo Horizonte",
  "brasilia": "Brasília",
  "campo-grande": "Campo Grande",
  "centro-civico-de-maringa": "Centro Cívico de Maringá",
  "cuiaba": "Cuiabá",
  "feira-de-santana": "Feira de Santana",
  "florianopolis": "Florianópolis",
  "goiania": "Goiânia",
  "joao-pessoa": "João Pessoa",
  "jardim-america": "Jardim América",
  "maringa": "Maringá",
  "mococa": "Mococa",
  "porto-velho": "Porto Velho",
  "rio-de-janeiro": "Rio de Janeiro",
  "rio-verde": "Rio Verde",
  "sao-luis": "São Luís",
  "sao-paulo": "São Paulo",
  "setor-aeroporto": "Setor Aeroporto",
  "setor-bueno": "Setor Bueno",
  "setor-central": "Setor Central",
  "setor-norte-ferroviario": "Setor Norte Ferroviário",
  "setor-sudoeste": "Setor Sudoeste"
};
const WORD_DISPLAY_NAMES: Record<string, string> = {
  anuncio: "Anúncio",
  anuncios: "Anúncios",
  america: "América",
  belem: "Belém",
  brasilia: "Brasília",
  civico: "Cívico",
  ferroviario: "Ferroviário",
  goiania: "Goiânia",
  joao: "João",
  luis: "Luís",
  maringa: "Maringá",
  midia: "Mídia",
  sao: "São"
};

export type PublicSeoMetadata = {
  title: string;
  description: string;
  h1: string;
  canonical: string;
  path: string;
  metadata: Metadata;
};

export type PublicBreadcrumbItem = {
  label: string;
  href?: string;
};

export type PublicLinkItem = {
  label: string;
  href: string;
  description?: string;
};

export const FUTURE_PRODUCTION_ROBOTS = {
  index: true,
  follow: true
} as const;

export function normalizeUf(uf: string): string {
  return (uf || "").trim().toUpperCase().slice(0, 2) || "BR";
}

export function displayCity(cidade: string): string {
  return titleizeSlug(cidade);
}

export function displayBairro(bairro: string): string {
  return titleizeSlug(bairro);
}

export function cidadePath(uf: string, cidade: string): string {
  return `/acompanhantes/${routeSegment(normalizeUf(uf).toLowerCase())}/${routeSegment(cidade)}`;
}

export function bairroPath(uf: string, cidade: string, bairro: string): string {
  return `${cidadePath(uf, cidade)}/${routeSegment(bairro)}`;
}

export function anuncioPath(slug: string): string {
  return `/anuncios/${routeSegment(slug)}`;
}

export function buildCitySeo(uf: string, cidade: string): PublicSeoMetadata {
  const ufLabel = normalizeUf(uf);
  const cityLabel = displayCity(cidade);
  const path = cidadePath(ufLabel, cidade);
  const h1 = `Acompanhantes em ${cityLabel} - ${ufLabel}`;
  const title = `${h1}${TITLE_SUFFIX}`;
  const description = compactDescription(
    `Encontre acompanhantes em ${cityLabel} - ${ufLabel} com navegação por bairros, perfis disponíveis e contato mediado com segurança.`
  );
  return buildMetadata({ title, description, h1, path });
}

export function buildBairroSeo(uf: string, cidade: string, bairro: string): PublicSeoMetadata {
  const ufLabel = normalizeUf(uf);
  const cityLabel = displayCity(cidade);
  const bairroLabel = displayBairro(bairro);
  const path = bairroPath(ufLabel, cidade, bairro);
  const h1 = `Acompanhantes em ${bairroLabel}, ${cityLabel} - ${ufLabel}`;
  const title = `${h1}${TITLE_SUFFIX}`;
  const description = compactDescription(
    `Veja acompanhantes em ${bairroLabel}, ${cityLabel} - ${ufLabel}, com links para a cidade, perfis relacionados e navegação organizada.`
  );
  return buildMetadata({ title, description, h1, path });
}

export function buildAnuncioSeo(slug: string, anuncio: AnuncioDetalhePublicoDto | null): PublicSeoMetadata {
  const path = anuncioPath(slug);
  const titleBase = safeDisplayText(anuncio?.titulo, titleizeSlug(slug));
  const location = formatLocation(anuncio?.localizacao);
  const h1 = titleBase;
  const title = `${titleBase}${location ? ` em ${location}` : ""}${TITLE_SUFFIX}`;
  const seoDescription = isTechnicalSeoDescription(anuncio?.seo.description) ? null : anuncio?.seo.description;
  const description = compactDescription(
    safeDisplayText(
      seoDescription ?? anuncio?.descricao,
      location
        ? `Perfil em ${location} com informações públicas, mídia controlada e contato mediado pelo Tops do Job.`
        : "Perfil com informações públicas, mídia controlada e contato mediado pelo Tops do Job."
    )
  );
  return buildMetadata({ title, description, h1, path });
}

export function cityBreadcrumbs(uf: string, cidade: string): PublicBreadcrumbItem[] {
  const ufLabel = normalizeUf(uf);
  const cityLabel = displayCity(cidade);
  return [
    { label: "Início", href: "/" },
    { label: "Acompanhantes" },
    { label: ufLabel },
    { label: cityLabel }
  ];
}

export function bairroBreadcrumbs(uf: string, cidade: string, bairro: string): PublicBreadcrumbItem[] {
  const ufLabel = normalizeUf(uf);
  const cityLabel = displayCity(cidade);
  const bairroLabel = displayBairro(bairro);
  return [
    { label: "Início", href: "/" },
    { label: "Acompanhantes" },
    { label: ufLabel },
    { label: cityLabel, href: cidadePath(ufLabel, cidade) },
    { label: bairroLabel }
  ];
}

export function anuncioBreadcrumbs(anuncio: AnuncioDetalhePublicoDto | null, slug: string): PublicBreadcrumbItem[] {
  const localizacao = anuncio?.localizacao;
  const uf = normalizeUf(localizacao?.uf ?? "");
  const cidade = localizacao?.cidadeSlug ?? localizacao?.cidade ?? "";
  const bairro = localizacao?.bairroSlug ?? localizacao?.bairro ?? "";
  const items: PublicBreadcrumbItem[] = [{ label: "Início", href: "/" }];

  if (cidade) {
    items.push({ label: displayCity(cidade), href: cidadePath(uf, cidade) });
  }
  if (cidade && bairro) {
    items.push({ label: displayBairro(bairro), href: bairroPath(uf, cidade, bairro) });
  }

  items.push({ label: safeDisplayText(anuncio?.titulo, titleizeSlug(slug)) });
  return items;
}

export function localityLinksFromAnuncios(
  items: readonly AnuncioCardPublicoDto[],
  uf: string,
  cidade: string
): PublicLinkItem[] {
  const seen = new Set<string>();
  return items
    .map((item) => item.localizacao)
    .filter((localizacao): localizacao is NonNullable<AnuncioCardPublicoDto["localizacao"]> =>
      Boolean(localizacao?.bairroSlug || localizacao?.bairro)
    )
    .map((localizacao) => {
      const bairroSlug = localizacao.bairroSlug ?? localizacao.bairro ?? "";
      const bairroLabel = localizacao.bairro ?? displayBairro(bairroSlug);
      return {
        key: bairroSlug.toLowerCase(),
        label: `Acompanhantes em ${bairroLabel}`,
        href: bairroPath(uf, cidade, bairroSlug),
        description: `Ver perfis no bairro ${bairroLabel}`
      };
    })
    .filter((link) => {
      if (seen.has(link.key)) {
        return false;
      }
      seen.add(link.key);
      return true;
    })
    .slice(0, 6)
    .map((link) => ({
      label: link.label,
      href: link.href,
      description: link.description
    }));
}

export function anuncioLinksFromCards(items: readonly AnuncioCardPublicoDto[]): PublicLinkItem[] {
  return items.slice(0, 6).map((item) => ({
    label: safeDisplayText(item.titulo, "Ver anúncio"),
    href: anuncioPath(item.slug),
    description: formatLocation(item.localizacao) || "Perfil público"
  }));
}

export function safeDisplayText(value: string | null | undefined, fallback: string): string {
  const normalized = (value ?? "")
    .replace(/[\u0000-\u001f\u007f]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
  return normalized || fallback;
}

function buildMetadata(input: Omit<PublicSeoMetadata, "canonical" | "metadata">): PublicSeoMetadata {
  const canonical = localUrl(input.path);
  const metadata: Metadata = {
    title: input.title,
    description: input.description,
    alternates: {
      canonical
    },
    robots: localNoindexRobots()
  };

  return {
    ...input,
    canonical,
    metadata
  };
}

function localNoindexRobots(): Metadata["robots"] {
  return {
    index: false,
    follow: false,
    googleBot: {
      index: false,
      follow: false
    }
  };
}

function compactDescription(value: string): string {
  const normalized = safeDisplayText(value, "Tops do Job");
  if (normalized.length <= MAX_DESCRIPTION_LENGTH) {
    return normalized;
  }
  return `${normalized.slice(0, MAX_DESCRIPTION_LENGTH - 1).trimEnd()}.`;
}

function isTechnicalSeoDescription(value: string | null | undefined): boolean {
  const original = safeDisplayText(value, "");
  const normalized = safeDisplayText(value, "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "");
  return (
    /metadados publicos locais|API local|sintetico|sintetica|smoke test|descartavel|fixture|mock/i.test(normalized) ||
    /\bANUNCIO\b/.test(original)
  );
}

function titleizeSlug(value: string): string {
  const normalizedValue = (value || "").trim().toLocaleLowerCase("pt-BR");
  if (FULL_DISPLAY_NAMES[normalizedValue]) {
    return FULL_DISPLAY_NAMES[normalizedValue];
  }
  return normalizedValue
    .split(/[-_\s]+/)
    .filter(Boolean)
    .map((part) => {
      const lower = part.toLocaleLowerCase("pt-BR");
      if (WORD_DISPLAY_NAMES[lower]) {
        return WORD_DISPLAY_NAMES[lower];
      }
      return `${lower.charAt(0).toLocaleUpperCase("pt-BR")}${lower.slice(1)}`;
    })
    .join(" ") || "Localidade";
}

function formatLocation(
  localizacao: AnuncioDetalhePublicoDto["localizacao"] | AnuncioCardPublicoDto["localizacao"] | undefined
): string {
  if (!localizacao) {
    return "";
  }
  return [localizacao.bairro, localizacao.cidade, localizacao.uf].filter(Boolean).join(", ");
}
