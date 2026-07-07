import type { Metadata } from "next";
import Link from "next/link";

import { localUrl } from "../lib/seo/localSeo";
import { PublicHomeHero } from "../modules/public/components/PublicHomeHero";
import { PublicSeoTextBlock } from "../modules/public/components/PublicSeoTextBlock";
import { PublicSiteHeader } from "../modules/public/components/PublicSiteHeader";

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
      <PublicSiteHeader />
      <section className="shell public-shell public-home-shell">
        <PublicHomeHero />
        <section className="public-home-directory" aria-label="Principais caminhos">
          <div>
            <span className="public-section-kicker">Explore por região</span>
            <h2>Acompanhantes por cidade e bairro</h2>
            <p>
              Comece pelos caminhos mais usados e acesse perfis, bairros e anúncios com navegação
              simples.
            </p>
          </div>
          <div className="public-home-directory-grid">
            <Link href="/acompanhantes/go/goiania">
              <strong>Goiânia</strong>
              <span>Perfis por cidade</span>
            </Link>
            <Link href="/acompanhantes/go/goiania/setor-bueno">
              <strong>Setor Bueno</strong>
              <span>Perfis por bairro</span>
            </Link>
            <Link href="/anuncios/demo-goiania-livre-premium">
              <strong>Anúncio em destaque</strong>
              <span>Ver perfil público</span>
            </Link>
          </div>
        </section>
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
