import Image from "next/image";
import Link from "next/link";

export function PublicSiteFooter() {
  return (
    <footer className="public-site-footer" aria-label="Rodapé público">
      <div className="public-site-footer-inner">
        <section className="public-footer-about" aria-label="Sobre o Tops do Job">
          <Image
            src="/logo.webp"
            alt="Tops do Job"
            width={170}
            height={60}
            className="public-footer-logo"
          />
          <p>
            O Tops do Job conecta anunciantes e visitantes em uma navegação pública discreta,
            organizada por cidade, bairro e anúncio.
          </p>
        </section>

        <nav className="public-footer-links" aria-label="Links legais">
          <h2>Legal</h2>
          <Link href="/seguranca">Segurança</Link>
          <Link href="/como-funciona">Como funciona</Link>
          <Link href="/perguntas-frequentes">Perguntas frequentes</Link>
        </nav>

        <nav className="public-footer-links" aria-label="Suporte">
          <h2>Suporte</h2>
          <Link href="/sobre">Sobre nós</Link>
          <Link href="/acompanhantes/go/goiania">Acompanhantes</Link>
          <Link href="/anunciar">Anuncie grátis</Link>
        </nav>

        <section className="public-footer-cta" aria-label="Publicar anúncio">
          <h2>Aumente sua visibilidade</h2>
          <p>Envie seu perfil para análise e acompanhe as próximas etapas com segurança.</p>
          <Link href="/anunciar">PUBLICAR SEU ANÚNCIO</Link>
        </section>
      </div>
      <div className="public-footer-copy">
        <span>Copyright © {new Date().getFullYear()} Tops do Job. Todos os direitos reservados.</span>
      </div>
    </footer>
  );
}
