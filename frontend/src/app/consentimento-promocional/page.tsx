import type { Metadata } from "next";

import { skeletonMetadata } from "../../lib/seo/localSeo";
import { PublicRouteShell } from "../../modules/public/skeleton/PublicRouteShell";
import { SeoPlaceholder } from "../../modules/public/skeleton/SeoPlaceholder";

export const metadata: Metadata = skeletonMetadata("Consentimento Promocional", "/consentimento-promocional");

export default function ConsentimentoPromocionalPage() {
  return (
    <PublicRouteShell title="Consentimento Promocional" routePattern="/consentimento-promocional">
      <p>
        Explica como o Tops do Job pode enviar comunicações promocionais e como o usuário pode
        gerenciar ou revogar esse consentimento.
      </p>
      <SeoPlaceholder routePath="/consentimento-promocional" />
    </PublicRouteShell>
  );
}
