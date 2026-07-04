import Link from "next/link";

export function PublicHomeHero() {
  return (
    <section className="public-home-hero" aria-label="Home pública">
      <div>
        <span className="status">Acompanhantes por cidade e bairro</span>
        <h1>Tops do Job</h1>
        <p>
          Encontre acompanhantes por cidade, bairro e anúncio com navegação simples, segura e
          responsiva.
        </p>
      </div>
      <div className="public-home-actions" aria-label="Navegação pública">
        <Link href="/acompanhantes/go/goiania">Acompanhantes em Goiânia</Link>
        <Link href="/acompanhantes/go/goiania/setor-bueno">Acompanhantes no Setor Bueno</Link>
        <Link href="/anuncios/anuncio-exemplo">Ver anúncio</Link>
        <Link href="/anunciar">Anuncie grátis</Link>
      </div>
    </section>
  );
}
