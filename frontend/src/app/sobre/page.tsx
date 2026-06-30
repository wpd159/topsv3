import type { Metadata } from "next";

import { skeletonMetadata } from "../../lib/seo/localSeo";
import { PublicRouteShell } from "../../modules/public/skeleton/PublicRouteShell";
import { SeoPlaceholder } from "../../modules/public/skeleton/SeoPlaceholder";

export const metadata: Metadata = skeletonMetadata("Sobre o Tops do Job", "/sobre");

export default function SobrePage() {
  return (
    <PublicRouteShell title="Sobre o Tops do Job" routePattern="/sobre">
      <p>
        Skeleton local para apresentar a marca Tops do Job como plataforma brasileira de anúncios
        classificados de acompanhantes, com organização por localidade e páginas públicas
        preservadas.
      </p>
      <p>
        O conteúdo final será revisado antes de produção. Esta página não usa dados reais,
        anúncios reais, imagens reais, integração externa ou backend de domínio.
      </p>
      <SeoPlaceholder routePath="/sobre" />
    </PublicRouteShell>
  );
}
