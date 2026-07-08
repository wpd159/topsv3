import type { Metadata } from "next";
import { Suspense } from "react";

import { localUrl } from "../../lib/seo/localSeo";
import { PublicLoginPanel } from "../../modules/public/components/PublicLoginPanel";
import { PublicSiteFooter } from "../../modules/public/components/PublicSiteFooter";
import { PublicSiteHeader } from "../../modules/public/components/PublicSiteHeader";

export const metadata: Metadata = {
  title: "Entrar | Tops do Job",
  description: "Entre no Tops do Job para acessar sua área e publicar seu anúncio.",
  alternates: {
    canonical: localUrl("/entrar")
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

export default function EntrarPage() {
  return (
    <main className="public-route">
      <PublicSiteHeader />
      <section className="shell public-shell public-login-shell">
        <Suspense fallback={<div className="public-login-card">Carregando entrada.</div>}>
          <PublicLoginPanel />
        </Suspense>
      </section>
      <PublicSiteFooter />
    </main>
  );
}
