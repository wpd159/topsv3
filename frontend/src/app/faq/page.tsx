import type { Metadata } from "next";

import { skeletonMetadata } from "../../lib/seo/localSeo";
import { PublicRouteShell } from "../../modules/public/skeleton/PublicRouteShell";
import { SeoPlaceholder } from "../../modules/public/skeleton/SeoPlaceholder";

export const metadata: Metadata = skeletonMetadata("Perguntas frequentes", "/faq");

export default function PerguntasFrequentesPage() {
  return (
    <PublicRouteShell title="Perguntas frequentes" routePattern="/faq">
      <p>
        Reúna respostas simples sobre a plataforma, páginas públicas, privacidade, moderação e
        limites de conteúdo.
      </p>
      <p>
        As orientações devem ser neutras, revisadas e fáceis de entender para visitantes e
        anunciantes.
      </p>
      <SeoPlaceholder routePath="/faq" />
    </PublicRouteShell>
  );
}
