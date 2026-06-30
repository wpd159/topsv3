import type { Metadata } from "next";

import { skeletonMetadata } from "../../lib/seo/localSeo";
import { PublicRouteShell } from "../../modules/public/skeleton/PublicRouteShell";
import { SeoPlaceholder } from "../../modules/public/skeleton/SeoPlaceholder";

export const metadata: Metadata = skeletonMetadata("Segurança", "/seguranca");

export default function SegurancaPage() {
  return (
    <PublicRouteShell title="Segurança" routePattern="/seguranca">
      <p>
        Skeleton local para registrar princípios futuros de segurança, privacidade, moderação,
        revisão humana e separação entre conteúdo público e áreas privadas.
      </p>
      <p>
        O texto final não deve prometer verificações ou proteções ainda não implementadas. Esta
        página não expõe documentos, dados privados, pagamentos, tokens ou áreas administrativas.
      </p>
      <SeoPlaceholder routePath="/seguranca" />
    </PublicRouteShell>
  );
}
