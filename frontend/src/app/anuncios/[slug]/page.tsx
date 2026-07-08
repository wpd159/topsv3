import type { Metadata } from "next";

import { getAnuncioPublico } from "../../../lib/api/publicApi";
import {
  anuncioBreadcrumbs,
  bairroPath,
  buildAnuncioSeo,
  cidadePath,
  displayBairro,
  displayCity,
  normalizeUf
} from "../../../lib/seo/publicSeo";
import { PublicBreadcrumbs } from "../../../modules/public/components/PublicBreadcrumbs";
import { PublicInternalLinks } from "../../../modules/public/components/PublicInternalLinks";
import { PublicSeoIntro } from "../../../modules/public/components/PublicSeoIntro";
import { PublicSiteFooter } from "../../../modules/public/components/PublicSiteFooter";
import { PublicSiteHeader } from "../../../modules/public/components/PublicSiteHeader";
import { PublicAgeGateContent } from "../../../modules/public/skeleton/PublicAgeGateContent";

type AnuncioPageProps = {
  params: Promise<{
    slug: string;
  }>;
};

export async function generateMetadata({ params }: AnuncioPageProps): Promise<Metadata> {
  const { slug } = await params;
  const anuncioApi = await getAnuncioPublico(slug);
  return buildAnuncioSeo(slug, anuncioApi.ok ? anuncioApi.data : null).metadata;
}

export default async function AnuncioSeoPage({ params }: AnuncioPageProps) {
  const { slug } = await params;
  const anuncioApi = await getAnuncioPublico(slug);
  const anuncio = anuncioApi.ok ? anuncioApi.data : null;
  const seo = buildAnuncioSeo(slug, anuncio);
  const localizacao = anuncio?.localizacao;
  const uf = normalizeUf(localizacao?.uf ?? "");
  const cidadeSlug = localizacao?.cidadeSlug ?? localizacao?.cidade ?? "";
  const bairroSlug = localizacao?.bairroSlug ?? localizacao?.bairro ?? "";
  const cidadeLabel = localizacao?.cidade ?? (cidadeSlug ? displayCity(cidadeSlug) : "");
  const bairroLabel = localizacao?.bairro ?? (bairroSlug ? displayBairro(bairroSlug) : "");
  const localityLinks = [
    cidadeSlug
      ? {
          label: `Acompanhantes em ${cidadeLabel} - ${uf}`,
          href: cidadePath(uf, cidadeSlug),
          description: "Ver perfis e bairros da cidade"
        }
      : null,
    cidadeSlug && bairroSlug
      ? {
          label: `Acompanhantes em ${bairroLabel}, ${cidadeLabel}`,
          href: bairroPath(uf, cidadeSlug, bairroSlug),
          description: "Ver perfis no bairro"
        }
      : null,
    {
      label: "Anuncie grátis",
      href: "/anunciar",
      description: "Envie seu perfil para análise"
    }
  ].filter((link): link is { label: string; href: string; description: string } => Boolean(link));

  return (
    <main className="public-route">
      <PublicSiteHeader />
      <section className="shell public-shell public-seo-page">
        <header className="public-anuncio-seo-header">
          <PublicBreadcrumbs items={anuncioBreadcrumbs(anuncio, slug)} />
          <h1>{seo.h1}</h1>
          <p>{seo.description}</p>
        </header>
        <PublicSeoIntro title="Informações do anúncio">
          <p>
            Este perfil mantém a página do anúncio organizada, liga a cidade e bairro quando
            disponíveis e deixa contato, mídia e conteúdo sensível sob controle de exibição.
          </p>
        </PublicSeoIntro>
      <PublicAgeGateContent
        slug={slug}
        initialAnuncio={anuncio}
        initialStatus={anuncioApi.status}
        initialMessage={anuncioApi.ok ? null : anuncioApi.message}
        />
        <PublicInternalLinks title="Navegação relacionada" links={localityLinks} />
      </section>
      <PublicSiteFooter />
    </main>
  );
}
