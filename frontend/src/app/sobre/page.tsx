import type { Metadata } from "next";

import { skeletonMetadata } from "../../lib/seo/localSeo";
import { PublicRouteShell } from "../../modules/public/skeleton/PublicRouteShell";
import { SeoPlaceholder } from "../../modules/public/skeleton/SeoPlaceholder";

export const metadata: Metadata = skeletonMetadata("Sobre o Tops do Job", "/sobre");

export default function SobrePage() {
  return (
    <PublicRouteShell title="Sobre o Tops do Job" routePattern="/sobre">
      <p>
        O Tops do Job organiza anúncios classificados de acompanhantes com navegação por cidade,
        bairro e perfil.
      </p>
      <p>
        A experiência pública prioriza informação clara, moderação e caminhos simples para quem
        busca ou deseja anunciar.
      </p>
      <SeoPlaceholder routePath="/sobre" />
    </PublicRouteShell>
  );
}
