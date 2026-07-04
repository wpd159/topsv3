import type { Metadata } from "next";

import { skeletonMetadata } from "../../lib/seo/localSeo";
import { PublicRouteShell } from "../../modules/public/skeleton/PublicRouteShell";
import { SeoPlaceholder } from "../../modules/public/skeleton/SeoPlaceholder";

export const metadata: Metadata = skeletonMetadata("Como funciona", "/como-funciona");

export default function ComoFuncionaPage() {
  return (
    <PublicRouteShell title="Como funciona" routePattern="/como-funciona">
      <p>
        Entenda como o Tops do Job organiza anúncios, cidades, bairros e conteúdos institucionais
        para facilitar a navegação.
      </p>
      <p>
        As informações públicas devem ser claras, revisadas e apresentadas sem prometer recursos
        que ainda não estejam disponíveis.
      </p>
      <SeoPlaceholder routePath="/como-funciona" />
    </PublicRouteShell>
  );
}
