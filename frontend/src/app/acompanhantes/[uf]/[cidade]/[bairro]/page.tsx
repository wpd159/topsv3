import type { Metadata } from "next";

import { getListagemBairroPublica } from "../../../../../lib/api/publicApi";
import {
  anuncioLinksFromCards,
  bairroBreadcrumbs,
  buildBairroSeo,
  cidadePath,
  displayBairro,
  displayCity,
  normalizeUf
} from "../../../../../lib/seo/publicSeo";
import { PublicAnuncioGrid } from "../../../../../modules/public/components/PublicAnuncioGrid";
import { PublicInternalLinks } from "../../../../../modules/public/components/PublicInternalLinks";
import { PublicLocalitySeoHeader } from "../../../../../modules/public/components/PublicLocalitySeoHeader";
import { PublicSeoIntro } from "../../../../../modules/public/components/PublicSeoIntro";

type BairroPageProps = {
  params: Promise<{
    uf: string;
    cidade: string;
    bairro: string;
  }>;
};

export async function generateMetadata({ params }: BairroPageProps): Promise<Metadata> {
  const { uf, cidade, bairro } = await params;
  return buildBairroSeo(uf, cidade, bairro).metadata;
}

export default async function BairroSeoPage({ params }: BairroPageProps) {
  const { uf, cidade, bairro } = await params;
  const seo = buildBairroSeo(uf, cidade, bairro);
  const ufLabel = normalizeUf(uf);
  const cityLabel = displayCity(cidade);
  const bairroLabel = displayBairro(bairro);
  const listagemApi = await getListagemBairroPublica(uf, cidade, bairro);
  const items = listagemApi.ok ? listagemApi.data.itens : [];
  const anuncioLinks = anuncioLinksFromCards(items);

  return (
    <main className="public-route">
      <section className="shell public-shell public-seo-page">
        <PublicLocalitySeoHeader
          h1={seo.h1}
          description={seo.description}
          breadcrumbs={bairroBreadcrumbs(ufLabel, cidade, bairro)}
          totalItens={listagemApi.ok ? listagemApi.data.paginacao.totalItens : null}
        />
        <PublicSeoIntro title={`Guia de acompanhantes em ${bairroLabel}, ${cityLabel}`}>
          <p>
            Esta página organiza perfis por bairro para quem procura acompanhante em {bairroLabel}
            ou acompanhantes em {bairroLabel}, {cityLabel} - {ufLabel}. A navegação mantém o caminho
            da cidade, os anúncios relacionados e o cadastro gratuito no mesmo fluxo público.
          </p>
        </PublicSeoIntro>
      {listagemApi.ok ? (
        <PublicAnuncioGrid
          items={items}
          emptyTitle="Nenhum perfil disponível no bairro"
          emptyMessage={`Ainda não há perfis suficientes em ${bairroLabel}, ${cityLabel}. A página fica pronta para receber anúncios quando houver oferta útil.`}
        />
      ) : (
        <PublicAnuncioGrid
          items={[]}
          emptyTitle="Perfis temporariamente indisponíveis"
          emptyMessage={`A navegação por ${bairroLabel}, ${cityLabel} permanece organizada enquanto a listagem é atualizada.`}
        />
      )}
        <PublicInternalLinks
          title={`Voltar para ${cityLabel}`}
          links={[
            {
              label: `Acompanhantes em ${cityLabel} - ${ufLabel}`,
              href: cidadePath(ufLabel, cidade),
              description: "Ver todos os bairros e perfis da cidade"
            }
          ]}
        />
        <PublicInternalLinks
          title="Anúncios do bairro"
          links={anuncioLinks}
          emptyText="Os anúncios serão exibidos aqui quando houver perfis suficientes para este bairro."
        />
        <PublicInternalLinks
          title="Também pode ajudar"
          links={[{ label: "Anuncie grátis", href: "/anunciar", description: "Envie seu perfil para análise" }]}
        />
      </section>
    </main>
  );
}
