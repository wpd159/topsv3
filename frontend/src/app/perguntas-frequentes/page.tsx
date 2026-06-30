import type { Metadata } from "next";

import { skeletonMetadata } from "../../lib/seo/localSeo";
import { PublicRouteShell } from "../../modules/public/skeleton/PublicRouteShell";
import { SeoPlaceholder } from "../../modules/public/skeleton/SeoPlaceholder";

export const metadata: Metadata = skeletonMetadata("Perguntas frequentes", "/perguntas-frequentes");

export default function PerguntasFrequentesPage() {
  return (
    <PublicRouteShell title="Perguntas frequentes" routePattern="/perguntas-frequentes">
      <p>
        Skeleton local para futura FAQ institucional sobre a plataforma, páginas públicas,
        privacidade, moderação, indexação e limites de conteúdo.
      </p>
      <p>
        As respostas finais devem ser neutras, revisadas por humano e alinhadas ao SDD. Nenhum
        schema FAQ final é emitido nesta fase.
      </p>
      <SeoPlaceholder routePath="/perguntas-frequentes" />
    </PublicRouteShell>
  );
}
