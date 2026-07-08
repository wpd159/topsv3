import type { Metadata } from "next";

import { skeletonMetadata } from "../../lib/seo/localSeo";
import { PublicRouteShell } from "../../modules/public/skeleton/PublicRouteShell";
import { SeoPlaceholder } from "../../modules/public/skeleton/SeoPlaceholder";

export const metadata: Metadata = skeletonMetadata("Acesso negado", "/acesso-negado");

export default function AcessoNegadoPage() {
  return (
    <PublicRouteShell title="Acesso negado" routePattern="/acesso-negado">
      <p>Você não tem permissão para acessar esta área.</p>
      <SeoPlaceholder routePath="/acesso-negado" />
    </PublicRouteShell>
  );
}
