import type { Metadata } from "next";

import { routeSegment, skeletonMetadata } from "../../../../../lib/seo/localSeo";
import { PublicRouteShell } from "../../../../../modules/public/skeleton/PublicRouteShell";
import { SeoPlaceholder } from "../../../../../modules/public/skeleton/SeoPlaceholder";

type BairroPageProps = {
  params: Promise<{
    uf: string;
    cidade: string;
    bairro: string;
  }>;
};

export async function generateMetadata({ params }: BairroPageProps): Promise<Metadata> {
  const { uf, cidade, bairro } = await params;
  return skeletonMetadata(
    "Bairro local skeleton",
    `/acompanhantes/${routeSegment(uf)}/${routeSegment(cidade)}/${routeSegment(bairro)}`
  );
}

export default async function BairroSkeletonPage({ params }: BairroPageProps) {
  const { uf, cidade, bairro } = await params;
  const routePath = `/acompanhantes/${routeSegment(uf)}/${routeSegment(cidade)}/${routeSegment(bairro)}`;

  return (
    <PublicRouteShell
      title="Bairro local skeleton"
      routePattern="/acompanhantes/[uf]/[cidade]/[bairro]"
    >
      <p>
        Esta página existe somente como skeleton local. Não há conteúdo adulto real, busca,
        geolocalização real, anúncio, mídia, contato, telefone ou chamada a API externa.
      </p>
      <SeoPlaceholder routePath={routePath} />
    </PublicRouteShell>
  );
}
