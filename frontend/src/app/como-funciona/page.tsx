import type { Metadata } from "next";

import { skeletonMetadata } from "../../lib/seo/localSeo";
import { PublicRouteShell } from "../../modules/public/skeleton/PublicRouteShell";
import { SeoPlaceholder } from "../../modules/public/skeleton/SeoPlaceholder";

export const metadata: Metadata = skeletonMetadata("Como funciona", "/como-funciona");

export default function ComoFuncionaPage() {
  return (
    <PublicRouteShell title="Como funciona" routePattern="/como-funciona">
      <p>
        Skeleton local para explicar, em linguagem neutra, como a V3 deverá organizar páginas de
        anúncio, páginas por cidade, páginas por bairro e conteúdo institucional.
      </p>
      <p>
        Não há busca real, cadastro funcional, login, checkout, Pix, dados reais ou chamada a API
        externa nesta fase.
      </p>
      <SeoPlaceholder routePath="/como-funciona" />
    </PublicRouteShell>
  );
}
