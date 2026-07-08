import Image from "next/image";
import Link from "next/link";

export function PublicSiteHeader() {
  return (
    <header className="public-site-header" aria-label="Cabeçalho público">
      <div className="public-site-header-inner">
        <Link className="public-brand" href="/">
          <Image
            src="/logo.webp"
            alt="Tops do Job"
            width={168}
            height={60}
            priority
            className="public-brand-logo"
          />
          <span className="public-brand-region">Brasil</span>
        </Link>
        <nav className="public-site-nav" aria-label="Navegação principal">
          <Link href="/acompanhantes/go/goiania">Acompanhantes</Link>
          <Link href="/anuncios/demo-goiania-livre-premium">Anúncios</Link>
        </nav>
        <div className="public-site-actions">
          <Link className="public-header-login" href="/admin">
            Entrar
          </Link>
          <Link className="public-header-register" href="/anunciar">
            Registrar-se
          </Link>
          <Link className="public-header-cta" href="/anunciar">
            PUBLICAR SEU ANÚNCIO
          </Link>
        </div>
      </div>
    </header>
  );
}
