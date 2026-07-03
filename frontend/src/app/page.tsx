import { skeletonMetadata } from "../lib/seo/localSeo";
import { PublicHomeHero } from "../modules/public/components/PublicHomeHero";
import { PublicSeoTextBlock } from "../modules/public/components/PublicSeoTextBlock";

export const metadata = skeletonMetadata("Tops do Job", "/");

export default function HomePage() {
  return (
    <main className="public-route">
      <section className="shell public-shell public-home-shell">
        <PublicHomeHero />
        <PublicSeoTextBlock routePath="/">
          <p>
            Escolha uma rota para conferir a navegacao publica com exemplos sinteticos e placeholders seguros.
          </p>
        </PublicSeoTextBlock>
      </section>
    </main>
  );
}
