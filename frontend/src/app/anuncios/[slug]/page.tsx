import type { Metadata } from "next";

import { routeSegment, skeletonMetadata } from "../../../lib/seo/localSeo";
import { PublicRouteShell } from "../../../modules/public/skeleton/PublicRouteShell";
import { SeoPlaceholder } from "../../../modules/public/skeleton/SeoPlaceholder";

type AnuncioPageProps = {
  params: Promise<{
    slug: string;
  }>;
};

export async function generateMetadata({ params }: AnuncioPageProps): Promise<Metadata> {
  const { slug } = await params;
  return skeletonMetadata("Anúncio local skeleton", `/anuncios/${routeSegment(slug)}`);
}

export default async function AnuncioSkeletonPage({ params }: AnuncioPageProps) {
  const { slug } = await params;
  const routePath = `/anuncios/${routeSegment(slug)}`;

  return (
    <PublicRouteShell title="Anúncio local skeleton" routePattern="/anuncios/[slug]">
      <p>
        Esta rota existe apenas para preservar o contrato público da V3 em ambiente local. Nenhum
        anúncio real, foto real, contato, preço, localização ou conteúdo sensível é carregado.
      </p>
      <SeoPlaceholder routePath={routePath} />
    </PublicRouteShell>
  );
}
