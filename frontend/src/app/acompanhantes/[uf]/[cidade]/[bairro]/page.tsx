import type { Metadata } from "next";

import { getListagemBairroPublica, getSeoRotaPublica } from "../../../../../lib/api/publicApi";
import { routeSegment, skeletonMetadata } from "../../../../../lib/seo/localSeo";
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
    "Bairro local skeleton",
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
      title="Bairro local skeleton"
      routePattern="/acompanhantes/[uf]/[cidade]/[bairro]"
    >
      <p>
        Esta página existe somente como skeleton local. Não há conteúdo adulto real, busca,
        geolocalização real, anúncio, mídia, contato, telefone ou chamada a API externa.
      </p>
      {listagemApi.ok ? (
        <section className="panel" aria-label="API publica local">
          <p>API publica local respondeu ao contrato de listagem por bairro.</p>
          <dl className="health-grid compact">
            <div>
              <dt>Status</dt>
              <dd>{listagemApi.status}</dd>
            </div>
            <div>
              <dt>Itens retornados</dt>
              <dd>{listagemApi.data.itens.length}</dd>
            </div>
            <div>
              <dt>Total local</dt>
              <dd>{listagemApi.data.paginacao.totalItens}</dd>
            </div>
          </dl>
        </section>
      ) : (
        <section className="panel muted" aria-label="Fallback local">
          <p>{listagemApi.message}</p>
          <p>A pagina permanece segura quando o backend local nao esta disponivel.</p>
        </section>
      )}
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
