import type { Metadata } from "next";

import { localUrl } from "../../lib/seo/localSeo";
import { PublicAnunciarForm } from "../../modules/public/components/PublicAnunciarForm";
import { PublicRouteShell } from "../../modules/public/skeleton/PublicRouteShell";

export const metadata: Metadata = {
  title: "PUBLICAR SEU ANÚNCIO | Tops do Job",
  description: "Publique seu anúncio no Tops do Job e apareça para mais clientes na sua cidade.",
  alternates: {
    canonical: localUrl("/anunciar")
  },
  robots: {
    index: false,
    follow: false,
    googleBot: {
      index: false,
      follow: false
    }
  }
};

export default function AnunciarPage() {
  return (
    <PublicRouteShell title="PUBLICAR SEU ANÚNCIO" routePattern="/anunciar" eyebrow={null} showRoutePattern={false}>
      <PublicAnunciarForm />
    </PublicRouteShell>
  );
}
