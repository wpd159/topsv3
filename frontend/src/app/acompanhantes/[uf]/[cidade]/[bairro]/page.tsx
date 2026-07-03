import type { Metadata } from "next";

import { getListagemBairroPublica, getSeoRotaPublica } from "../../../../../lib/api/publicApi";
import { routeSegment, skeletonMetadata } from "../../../../../lib/seo/localSeo";
import { PublicAnuncioGrid } from "../../../../../modules/public/components/PublicAnuncioGrid";
import { PublicEmptyState } from "../../../../../modules/public/components/PublicEmptyState";
import { PublicLocalidadeHeader } from "../../../../../modules/public/components/PublicLocalidadeHeader";
import { PublicSeoTextBlock } from "../../../../../modules/public/components/PublicSeoTextBlock";
import { PublicRouteShell } from "../../../../../modules/public/skeleton/PublicRouteShell";
import { SeoPlaceholder } from "../../../../../modules/public/skeleton/SeoPlaceholder";

type BairroPageProps = {
  params: Promise<{
    uf: string;
    cidade: string;
    bairro: string;
  }>;
};

export async function generateMetadata({ params }: BairroPageProps): Promise<Metadata> {
  const { uf, cidade, bairro } = await params;
  return skeletonMetadata(
    "Acompanhantes por bairro - Tops do Job",
    `/acompanhantes/${routeSegment(uf)}/${routeSegment(cidade)}/${routeSegment(bairro)}`
  );
}

export default async function BairroSkeletonPage({ params }: BairroPageProps) {
  const { uf, cidade, bairro } = await params;
  const routePath = `/acompanhantes/${routeSegment(uf)}/${routeSegment(cidade)}/${routeSegment(bairro)}`;
  const [listagemApi, seoApi] = await Promise.all([
    getListagemBairroPublica(uf, cidade, bairro),
    getSeoRotaPublica(routePath)
  ]);

  return (
    <PublicRouteShell
      title="Acompanhantes por bairro"
      routePattern="/acompanhantes/[uf]/[cidade]/[bairro]"
    >
      <p>
        Esta pagina existe somente como previa local. Nao ha conteudo adulto real, busca,
        geolocalizacao real, anuncio, midia, contato, telefone ou chamada a API externa.
      </p>
      <PublicLocalidadeHeader
        title="Acompanhantes locais no bairro"
        routeLabel="/acompanhantes/[uf]/[cidade]/[bairro]"
        totalItens={listagemApi.ok ? listagemApi.data.paginacao.totalItens : null}
        status={listagemApi.ok ? "Disponivel" : "Indisponivel"}
        locationParts={[uf, cidade, bairro]}
      />
      {listagemApi.ok ? (
        <PublicAnuncioGrid
          items={listagemApi.data.itens}
          emptyTitle="Nenhum anuncio local"
          emptyMessage="A listagem por bairro esta disponivel, mas nao retornou itens locais."
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
