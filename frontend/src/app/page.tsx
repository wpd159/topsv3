import type { Metadata } from "next";

import { localUrl } from "../lib/seo/localSeo";
import { PublicHomeHero } from "../modules/public/components/PublicHomeHero";
import { PublicSeoTextBlock } from "../modules/public/components/PublicSeoTextBlock";

export const metadata: Metadata = {
  title: "Tops do Job | Acompanhantes por cidade e bairro",
  description: "Navegue por acompanhantes em cidades, bairros e anúncios com contato mediado e cadastro gratuito para análise.",
  alternates: {
    canonical: localUrl("/")
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

export default function HomePage() {
  return (
    <main className="public-route">
      <section className="shell public-shell public-home-shell">
        <PublicHomeHero />
        <PublicSeoTextBlock routePath="/">
          <p>
            Navegue por cidades, bairros e anúncios, ou envie seu perfil em Anuncie grátis para
            análise antes da publicação.
          </p>
        </PublicSeoTextBlock>
      </section>
    </main>
  );
}
