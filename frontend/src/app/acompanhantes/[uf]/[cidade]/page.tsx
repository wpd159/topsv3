import type { Metadata } from "next";

import { routeSegment, skeletonMetadata } from "../../../../lib/seo/localSeo";
import { PublicRouteShell } from "../../../../modules/public/skeleton/PublicRouteShell";
import { SeoPlaceholder } from "../../../../modules/public/skeleton/SeoPlaceholder";

type CidadePageProps = {
  params: Promise<{
    uf: string;
    cidade: string;
  }>;
};

export async function generateMetadata({ params }: CidadePageProps): Promise<Metadata> {
  const { uf, cidade } = await params;
  return skeletonMetadata(
    "Listagem local skeleton",
    `/acompanhantes/${routeSegment(uf)}/${routeSegment(cidade)}`
  );
}

export default async function CidadeSkeletonPage({ params }: CidadePageProps) {
  const { uf, cidade } = await params;
  const routePath = `/acompanhantes/${routeSegment(uf)}/${routeSegment(cidade)}`;

  return (
    <PublicRouteShell title="Listagem local skeleton" routePattern="/acompanhantes/[uf]/[cidade]">
      <p>
        Esta página valida apenas a existência da rota pública. Não há busca real, listagem real,
        dados de cidade carregados, anúncio, mídia ou integração externa.
      </p>
      <SeoPlaceholder routePath={routePath} />
    </PublicRouteShell>
  );
}
