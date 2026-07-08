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
        sizes="(max-width: 760px) 728px, 1220px"
        className="production-hero-image"
      />
      <div className="production-hero-overlay" />
      <div className="public-home-copy production-home-copy">
        <h1>
          Encontre <span>acompanhantes</span> perto de você!
        </h1>
        <p>Veja os anúncios de acompanhantes perto de você, com sigilo, segurança e contato direto pelo WhatsApp.</p>
        <div className="production-search-shell" aria-label="Busca visual">
          <div className="production-search-input">Digite cidade, bairro, categoria ou característica...</div>
          <Link className="production-search-button" href="/acompanhantes/go/goiania">
            Buscar
          </Link>
        </div>
      </div>
    </section>
  );
}
