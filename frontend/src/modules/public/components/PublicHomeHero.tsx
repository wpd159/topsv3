import Link from "next/link";

export function PublicHomeHero() {
  return (
    <section className="public-home-hero" aria-label="Home publica local">
      <div>
        <span className="status" data-debug-label="SKELETON LOCAL">
          Previa local
        </span>
        <h1>Tops do Job</h1>
        <p>
          Navegue por cidades, bairros e anuncios com uma experiencia simples, segura e responsiva.
        </p>
      </div>
      <div className="public-home-actions" aria-label="Rotas publicas preservadas">
        <Link href="/acompanhantes/zz/cidade-sintetica">Ver por cidade</Link>
        <Link href="/acompanhantes/zz/cidade-sintetica/bairro-sintetico">Ver por bairro</Link>
        <Link href="/anuncios/anuncio-sintetico-local">Ver anuncio</Link>
      </div>
    </section>
  );
}
