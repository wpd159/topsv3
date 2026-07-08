"use client";

import Image from "next/image";
import { useRouter } from "next/navigation";
import { useState } from "react";

export function PublicHomeHero() {
  const router = useRouter();
  const [query, setQuery] = useState("");

  function handleSearch(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmedQuery = query.trim();
    const target = trimmedQuery
      ? `/acompanhantes/go/goiania?busca=${encodeURIComponent(trimmedQuery)}`
      : "/acompanhantes/go/goiania";

    router.push(target);
  }

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
        <form className="production-search-shell" aria-label="Buscar acompanhantes" onSubmit={handleSearch}>
          <input
            className="production-search-input"
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Digite cidade, bairro, categoria ou característica..."
            aria-label="Digite cidade, bairro, categoria ou característica"
          />
          <button className="production-search-button" type="submit">
            Buscar
          </button>
        </form>
      </div>
    </section>
  );
}
