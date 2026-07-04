import type { PublicBreadcrumbItem } from "../../../lib/seo/publicSeo";
import { PublicBreadcrumbs } from "./PublicBreadcrumbs";

type PublicLocalitySeoHeaderProps = {
  h1: string;
  description: string;
  breadcrumbs: readonly PublicBreadcrumbItem[];
  totalItens: number | null;
  ctaHref?: string;
  ctaLabel?: string;
};

export function PublicLocalitySeoHeader({
  h1,
  description,
  breadcrumbs,
  totalItens,
  ctaHref = "/anunciar",
  ctaLabel = "Anuncie grátis"
}: PublicLocalitySeoHeaderProps) {
  return (
    <header className="public-locality-seo-header">
      <PublicBreadcrumbs items={breadcrumbs} />
      <div className="public-locality-seo-heading">
        <div>
          <h1>{h1}</h1>
          <p>{description}</p>
        </div>
        <div className="public-locality-seo-summary">
          <span>{formatTotal(totalItens)}</span>
          <a href={ctaHref}>{ctaLabel}</a>
        </div>
      </div>
    </header>
  );
}

function formatTotal(totalItens: number | null): string {
  if (totalItens === null) {
    return "Perfis disponíveis serão exibidos aqui";
  }
  if (totalItens === 0) {
    return "Nenhum perfil disponível no momento";
  }
  if (totalItens === 1) {
    return "1 perfil disponível";
  }
  return `${totalItens} perfis disponíveis`;
}
