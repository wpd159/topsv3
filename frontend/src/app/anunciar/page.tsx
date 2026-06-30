import type { Metadata } from "next";

import { skeletonMetadata } from "../../lib/seo/localSeo";
import { PublicRouteShell } from "../../modules/public/skeleton/PublicRouteShell";
import { SeoPlaceholder } from "../../modules/public/skeleton/SeoPlaceholder";

export const metadata: Metadata = skeletonMetadata("Anunciar", "/anunciar");

export default function AnunciarPage() {
  return (
    <PublicRouteShell title="Anunciar" routePattern="/anunciar">
      <p>
        Skeleton local para uma futura página institucional sobre publicação de anúncios na V3, com
        linguagem neutra e revisão humana obrigatória antes de qualquer conteúdo público final.
      </p>
      <p>
        Não há formulário funcional, login, checkout, pagamento, Pix, anúncio real, dado real ou
        integração externa nesta fase.
      </p>
      <SeoPlaceholder routePath="/anunciar" />
    </PublicRouteShell>
  );
}
