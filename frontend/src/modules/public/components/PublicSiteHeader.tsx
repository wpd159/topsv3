import Link from "next/link";
import Image from "next/image";

export function PublicSiteHeader() {
  return (
    <header className="public-site-header" aria-label="Cabeçalho público">
      <div className="public-site-header-inner">
        <Link className="public-brand" href="/">
          <Image
            src="/logo.webp"
            alt="Tops do Job"
            width={140}
            height={50}
            priority
            className="public-brand-logo"
          />
          <span className="public-brand-region">Brasil</span>
        </Link>
        <nav className="public-site-nav" aria-label="Navegação principal">
          <Link href="/acompanhantes/go/goiania">Acompanhantes</Link>
          <Link href="/anuncios/demo-goiania-livre-premium">Anúncios</Link>
          <Link href="/acompanhantes/go/goiania/setor-bueno">Setor Bueno</Link>
        </nav>
        <Link className="public-header-cta" href="/anunciar">
          PUBLICAR SEU ANÚNCIO
        </Link>
      </div>
    </header>
  );
}
