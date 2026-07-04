import type { Metadata } from "next";

import { skeletonMetadata } from "../../lib/seo/localSeo";
import { PublicRouteShell } from "../../modules/public/skeleton/PublicRouteShell";
import { SeoPlaceholder } from "../../modules/public/skeleton/SeoPlaceholder";

export const metadata: Metadata = skeletonMetadata("Segurança", "/seguranca");

export default function SegurancaPage() {
  return (
    <PublicRouteShell title="Segurança" routePattern="/seguranca">
      <p>
        O Tops do Job deve separar informações públicas, áreas privadas, conteúdo sensível e
        processos de moderação.
      </p>
      <p>
        As informações de segurança devem ser objetivas e não prometer verificações ou proteções
        que ainda não estejam disponíveis.
      </p>
      <SeoPlaceholder routePath="/seguranca" />
    </PublicRouteShell>
  );
}
