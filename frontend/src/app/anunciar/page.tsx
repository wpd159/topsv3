import type { Metadata } from "next";

import { localUrl } from "../../lib/seo/localSeo";
import { PublicAnunciarForm } from "../../modules/public/components/PublicAnunciarForm";
import { PublicRouteShell } from "../../modules/public/skeleton/PublicRouteShell";

export const metadata: Metadata = {
  title: "Anuncie grátis | Tops do Job",
  description: "Envie seu anúncio para análise antes da publicação no Tops do Job.",
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
    <PublicRouteShell title="Anuncie grátis" routePattern="/anunciar" eyebrow={null} showRoutePattern={false}>
      <PublicAnunciarForm />
    </PublicRouteShell>
  );
}
