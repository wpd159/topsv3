import Link from "next/link";

export function PublicHomeHero() {
  return (
    <section className="public-home-hero" aria-label="Home pública">
      <div className="public-home-copy">
        <span className="status">Acompanhantes perto de você</span>
        <h1>Tops do Job</h1>
        <p>
          Encontre perfis por cidade e bairro, veja anúncios com contato mediado e anuncie grátis
          para análise.
        </p>
        <div className="public-home-primary-actions" aria-label="Ações principais">
          <Link className="public-hero-cta" href="/acompanhantes/go/goiania">
            Ver acompanhantes
          </Link>
          <Link className="public-hero-secondary" href="/anunciar">
            Publicar anúncio
          </Link>
        </div>
      </div>
      <div className="public-home-preview" aria-label="Prévia de perfis">
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
