import type { Metadata } from "next";

import { getAnuncioPublico, getSeoRotaPublica } from "../../../lib/api/publicApi";
import { routeSegment, skeletonMetadata } from "../../../lib/seo/localSeo";
import { PublicAgeGateContent } from "../../../modules/public/skeleton/PublicAgeGateContent";
import { PublicRouteShell } from "../../../modules/public/skeleton/PublicRouteShell";
import { SeoPlaceholder } from "../../../modules/public/skeleton/SeoPlaceholder";

type AnuncioPageProps = {
  params: Promise<{
    slug: string;
  }>;
};

export async function generateMetadata({ params }: AnuncioPageProps): Promise<Metadata> {
  const { slug } = await params;
  return skeletonMetadata("Anúncio local skeleton", `/anuncios/${routeSegment(slug)}`);
}

export default async function AnuncioSkeletonPage({ params }: AnuncioPageProps) {
  const { slug } = await params;
  const routePath = `/anuncios/${routeSegment(slug)}`;
  const [anuncioApi, seoApi] = await Promise.all([
    getAnuncioPublico(slug),
    getSeoRotaPublica(routePath)
  ]);

  return (
    <PublicRouteShell title="Anúncio local skeleton" routePattern="/anuncios/[slug]">
      <p>
        Esta rota existe apenas para preservar o contrato público da V3 em ambiente local. Nenhum
        anúncio real, foto real, contato, preço, localização ou conteúdo sensível é carregado.
      </p>
      <PublicAgeGateContent
        slug={slug}
        initialAnuncio={anuncioApi.ok ? anuncioApi.data : null}
        initialStatus={anuncioApi.status}
        initialMessage={anuncioApi.ok ? null : anuncioApi.message}
      />
      {seoApi.ok ? (
        <section className="panel" aria-label="SEO via API local">
          <p>SEO local recebido da API publica de leitura.</p>
          <dl className="health-grid compact">
            <div>
              <dt>Canonical</dt>
              <dd>{seoApi.data.canonicalPath}</dd>
            </div>
            <div>
              <dt>Robots</dt>
              <dd>{seoApi.data.robots}</dd>
            </div>
          </dl>
        </section>
      ) : null}
      <SeoPlaceholder routePath={routePath} />
    </PublicRouteShell>
  );
}
