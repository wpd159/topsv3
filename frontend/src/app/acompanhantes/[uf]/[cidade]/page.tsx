import type { Metadata } from "next";

import { getListagemCidadePublica, getSeoRotaPublica } from "../../../../lib/api/publicApi";
import { routeSegment, skeletonMetadata } from "../../../../lib/seo/localSeo";
import { PublicAnuncioGrid } from "../../../../modules/public/components/PublicAnuncioGrid";
import { PublicEmptyState } from "../../../../modules/public/components/PublicEmptyState";
import { PublicLocalidadeHeader } from "../../../../modules/public/components/PublicLocalidadeHeader";
import { PublicSeoTextBlock } from "../../../../modules/public/components/PublicSeoTextBlock";
import { PublicRouteShell } from "../../../../modules/public/skeleton/PublicRouteShell";
import { SeoPlaceholder } from "../../../../modules/public/skeleton/SeoPlaceholder";

type CidadePageProps = {
  params: Promise<{
    uf: string;
    cidade: string;
  }>;
};

export async function generateMetadata({ params }: CidadePageProps): Promise<Metadata> {
  const { uf, cidade } = await params;
  return skeletonMetadata(
    "Acompanhantes por cidade - Tops do Job",
    `/acompanhantes/${routeSegment(uf)}/${routeSegment(cidade)}`
  );
}

export default async function CidadeSkeletonPage({ params }: CidadePageProps) {
  const { uf, cidade } = await params;
  const routePath = `/acompanhantes/${routeSegment(uf)}/${routeSegment(cidade)}`;
  const [listagemApi, seoApi] = await Promise.all([
    getListagemCidadePublica(uf, cidade),
    getSeoRotaPublica(routePath)
  ]);

  return (
    <PublicRouteShell title="Acompanhantes por cidade" routePattern="/acompanhantes/[uf]/[cidade]">
      <p>
        Previa local com dados sinteticos para conferir a listagem publica. Nao ha busca real,
        listagem real, dados reais de cidade, anuncio, midia ou integracao externa.
      </p>
      <PublicLocalidadeHeader
        title="Acompanhantes locais"
        routeLabel="/acompanhantes/[uf]/[cidade]"
        totalItens={listagemApi.ok ? listagemApi.data.paginacao.totalItens : null}
        status={listagemApi.ok ? "Disponivel" : "Indisponivel"}
        locationParts={[uf, cidade]}
      />
      {listagemApi.ok ? (
        <PublicAnuncioGrid
          items={listagemApi.data.itens}
          emptyTitle="Nenhum anuncio local"
          emptyMessage="A listagem por cidade esta disponivel, mas nao retornou itens locais."
        />
      ) : (
        <PublicEmptyState
          title="Listagem local indisponivel"
          message={`${listagemApi.message}. A pagina permanece segura quando o backend local nao esta disponivel.`}
        />
      )}
      {seoApi.ok ? (
        <section className="panel" aria-label="SEO da rota publica">
          <p>Informacoes locais de rota preservada.</p>
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
      <PublicSeoTextBlock routePath={routePath} />
      <SeoPlaceholder routePath={routePath} />
    </PublicRouteShell>
  );
}
