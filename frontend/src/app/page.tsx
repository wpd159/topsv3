import type { Metadata } from "next";
import Link from "next/link";

import { localUrl } from "../lib/seo/localSeo";
import { PublicCategorySection } from "../modules/public/components/PublicCategorySection";
import { PublicHomeHero } from "../modules/public/components/PublicHomeHero";
import { PublicSeoTextBlock } from "../modules/public/components/PublicSeoTextBlock";
import { PublicSiteFooter } from "../modules/public/components/PublicSiteFooter";
import { PublicSiteHeader } from "../modules/public/components/PublicSiteHeader";

export const metadata: Metadata = {
  title: "Tops do Job | Acompanhantes por cidade e bairro",
  description: "Veja anúncios de acompanhantes por cidade e bairro, encontre perfis na sua região e publique seu anúncio.",
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
        <PublicCategorySection />
        <section className="public-home-directory" aria-label="Principais caminhos">
          <div>
            <span className="public-section-kicker">Explore por região</span>
            <h2>Acompanhantes por cidade e bairro</h2>
            <p>
              Acesse páginas locais com anúncios de acompanhantes por cidade e continue a busca por
              regiões próximas.
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
              <span>Ver anúncio</span>
            </Link>
          </div>
        </section>
        <PublicSeoTextBlock routePath="/">
          <p>
            Encontre acompanhantes por cidade, bairro e anúncio. Publique seu anúncio e apareça para
            mais clientes.
          </p>
        </PublicSeoTextBlock>
        <section className="public-home-trust" aria-labelledby="public-home-trust-title">
          <div className="public-home-trust-heading">
            <h2 id="public-home-trust-title">
              Confiança que <span>se sente</span> em cada detalhe!
            </h2>
            <p>
              Verificações reais, autenticações constantes e um time dedicado à segurança de quem
              anuncia e contrata.
            </p>
          </div>
          <div className="public-home-trust-grid">
            <article className="public-home-trust-card">
              <span aria-hidden="true">360°</span>
              <h3>Mídias 360° revisadas</h3>
              <p>Cada foto e vídeo é verificado pela nossa equipe para garantir que o perfil é real e atualizado.</p>
            </article>
            <article className="public-home-trust-card">
              <span aria-hidden="true">ID</span>
              <h3>Identidade confirmada</h3>
              <p>Todos os profissionais passam por verificação documental antes de seus anúncios ficarem públicos.</p>
            </article>
            <article className="public-home-trust-card">
              <span aria-hidden="true">OK</span>
              <h3>Conteúdo protegido</h3>
              <p>Fotos e vídeos são armazenados com segurança e auditados regularmente para evitar falsificações.</p>
            </article>
          </div>
        </section>
      </section>
      <PublicSiteFooter />
    </main>
  );
}
