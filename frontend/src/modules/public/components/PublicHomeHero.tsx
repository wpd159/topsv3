import Image from "next/image";
import Link from "next/link";

export function PublicHomeHero() {
  return (
    <section className="public-home-hero production-home-hero" aria-label="Home pública">
      <Image
        src="/2151117281.jpg"
        alt=""
        fill
        priority
        sizes="(max-width: 760px) 100vw, 1220px"
        className="production-hero-image"
      />
      <div className="production-hero-overlay" />
      <div className="public-home-copy production-home-copy">
        <h1>
          Encontre <span>acompanhantes</span> perto de você
        </h1>
        <p>Veja anúncios na sua cidade e região, com contato mediado pela V3.</p>
        <div className="production-search-shell" aria-label="Busca visual">
          <div className="production-search-input">Digite cidade, bairro, categoria ou característica...</div>
          <Link className="production-search-button" href="/acompanhantes/go/goiania">
            Buscar
          </Link>
        </div>
        <div className="public-home-primary-actions" aria-label="Ações principais">
          <Link className="public-hero-cta" href="/acompanhantes/go/goiania">
            Ver acompanhantes
          </Link>
          <Link className="public-hero-secondary" href="/anunciar">
            Publicar anúncio
          </Link>
        </div>
      </div>
      <div className="public-home-preview production-home-preview" aria-label="Prévia de perfis">
        <Link href="/anuncios/demo-goiania-livre-premium" className="public-preview-card public-preview-card-primary">
          <span>Destaque</span>
          <strong>Perfil em Goiânia</strong>
          <small>Ver anúncio</small>
        </Link>
        <Link href="/acompanhantes/go/goiania/setor-bueno" className="public-preview-card">
          <span>Bairro</span>
          <strong>Setor Bueno</strong>
          <small>Explorar perfis</small>
        </Link>
      </div>
    </section>
  );
}
