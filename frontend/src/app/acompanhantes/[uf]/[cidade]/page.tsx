import type { Metadata } from "next";

import { getListagemCidadePublica, getSeoRotaPublica } from "../../../../lib/api/publicApi";
import { routeSegment, skeletonMetadata } from "../../../../lib/seo/localSeo";
import { PublicRouteShell } from "../../../../modules/public/skeleton/PublicRouteShell";
import { SeoPlaceholder } from "../../../../modules/public/skeleton/SeoPlaceholder";

type CidadePageProps = {
  params: Promise<{
    uf: string;
    cidade: string;
  }>;
};

type ItemComMidia = {
  midias: readonly { urlPublica: string | null; pendenciaMidia?: string | null }[];
};

export async function generateMetadata({ params }: CidadePageProps): Promise<Metadata> {
  const { uf, cidade } = await params;
  return skeletonMetadata(
    "Listagem local skeleton",
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
    <PublicRouteShell title="Listagem local skeleton" routePattern="/acompanhantes/[uf]/[cidade]">
      <p>
        Esta página valida apenas a existência da rota pública. Não há busca real, listagem real,
        dados de cidade carregados, anúncio, mídia ou integração externa.
      </p>
      {listagemApi.ok ? (
        <section className="panel" aria-label="API publica local">
          <p>API publica local respondeu ao contrato de listagem por cidade.</p>
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
            <div>
              <dt>Midia local</dt>
              <dd>{mediaStatus(listagemApi.data.itens)}</dd>
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

function mediaStatus(itens: readonly ItemComMidia[]): string {
  const totalMidias = itens.reduce((total, item) => total + item.midias.length, 0);
  if (totalMidias === 0) {
    return "sem midia";
  }
  if (itens.some((item) => item.midias.some((midia) => Boolean(midia.urlPublica)))) {
    return "url publica autorizada";
  }
  return itens
    .flatMap((item) => item.midias)
    .find((midia) => Boolean(midia.pendenciaMidia))?.pendenciaMidia ?? "PENDENTE_URL_PUBLICA_MIDIA_CDN";
}
