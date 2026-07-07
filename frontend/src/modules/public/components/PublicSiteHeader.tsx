import Link from "next/link";

export function PublicSiteHeader() {
  return (
    <header className="public-site-header" aria-label="Cabeçalho público">
      <div className="public-site-header-inner">
        <Link className="public-brand" href="/">
          <span>Tops</span>
          <strong>do Job</strong>
        </Link>
        <nav className="public-site-nav" aria-label="Navegação principal">
          <Link href="/acompanhantes/go/goiania">Goiânia</Link>
          <Link href="/acompanhantes/go/goiania/setor-bueno">Setor Bueno</Link>
          <Link href="/anuncios/demo-goiania-livre-premium">Anúncios</Link>
        </nav>
        <Link className="public-header-cta" href="/anunciar">
          Anuncie grátis
        </Link>
      </div>
    </header>
  );
}
