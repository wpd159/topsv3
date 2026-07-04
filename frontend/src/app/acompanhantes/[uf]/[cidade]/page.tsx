import type { Metadata } from "next";

import { getListagemCidadePublica } from "../../../../lib/api/publicApi";
import {
  anuncioLinksFromCards,
  buildCitySeo,
  cityBreadcrumbs,
  displayCity,
  localityLinksFromAnuncios,
  normalizeUf
} from "../../../../lib/seo/publicSeo";
import { PublicAnuncioGrid } from "../../../../modules/public/components/PublicAnuncioGrid";
import { PublicInternalLinks } from "../../../../modules/public/components/PublicInternalLinks";
import { PublicLocalitySeoHeader } from "../../../../modules/public/components/PublicLocalitySeoHeader";
import { PublicSeoIntro } from "../../../../modules/public/components/PublicSeoIntro";

type CidadePageProps = {
  params: Promise<{
    uf: string;
    cidade: string;
  }>;
};

export async function generateMetadata({ params }: CidadePageProps): Promise<Metadata> {
  const { uf, cidade } = await params;
  return buildCitySeo(uf, cidade).metadata;
}

export default async function CidadeSeoPage({ params }: CidadePageProps) {
  const { uf, cidade } = await params;
  const seo = buildCitySeo(uf, cidade);
  const ufLabel = normalizeUf(uf);
  const cityLabel = displayCity(cidade);
  const listagemApi = await getListagemCidadePublica(uf, cidade);
  const items = listagemApi.ok ? listagemApi.data.itens : [];
  const bairroLinks = localityLinksFromAnuncios(items, ufLabel, cidade);
  const anuncioLinks = anuncioLinksFromCards(items);

  return (
    <main className="public-route">
      <section className="shell public-shell public-seo-page">
        <PublicLocalitySeoHeader
          h1={seo.h1}
          description={seo.description}
          breadcrumbs={cityBreadcrumbs(ufLabel, cidade)}
          totalItens={listagemApi.ok ? listagemApi.data.paginacao.totalItens : null}
        />
        <PublicSeoIntro title={`Guia de acompanhantes em ${cityLabel} - ${ufLabel}`}>
          <p>
            A página reúne perfis por cidade e facilita a navegação por bairro, anúncio e cadastro
            gratuito. A navegação ajuda quem busca acompanhante em {cityLabel} e acompanhantes em
            {cityLabel} - {ufLabel}, mantendo contato e mídia sob controle de exibição.
          </p>
        </PublicSeoIntro>
      {listagemApi.ok ? (
        <PublicAnuncioGrid
          items={items}
          emptyTitle="Nenhum perfil disponível no momento"
          emptyMessage={`Ainda não há perfis para ${cityLabel} - ${ufLabel}. Você pode anunciar grátis ou voltar em breve para conferir novas publicações.`}
        />
      ) : (
        <PublicAnuncioGrid
          items={[]}
          emptyTitle="Perfis temporariamente indisponíveis"
          emptyMessage={`A página de ${cityLabel} - ${ufLabel} permanece acessível para navegação e cadastro enquanto a listagem é atualizada.`}
        />
      )}
        <PublicInternalLinks
          title={`Bairros de ${cityLabel}`}
          links={bairroLinks}
          emptyText="Os bairros serão exibidos quando houver perfis suficientes para uma navegação útil."
        />
        <PublicInternalLinks
          title="Anúncios relacionados"
          links={anuncioLinks}
          emptyText="Novos anúncios serão ligados aqui conforme a cidade tiver perfis disponíveis."
        />
        <PublicInternalLinks
          title="Também pode ajudar"
          links={[{ label: "Anuncie grátis", href: "/anunciar", description: "Envie seu perfil para análise" }]}
        />
      </section>
    </main>
  );
}
