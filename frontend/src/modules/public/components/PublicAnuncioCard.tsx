import Link from "next/link";

import type { AnuncioCardPublicoDto } from "../../../lib/api/publicTypes";
import { PublicMidiaPlaceholder } from "./PublicMidiaPlaceholder";

type PublicAnuncioCardProps = {
  item: AnuncioCardPublicoDto;
};

export function PublicAnuncioCard({ item }: PublicAnuncioCardProps) {
  const badges = [
    item.destaque ? "Destaque" : null,
    item.topo ? "Topo" : null,
    item.midiaExtra ? "Midia extra" : null,
    item.story ? "Stories" : null
  ].filter((badge): badge is string => Boolean(badge));

  return (
    <article className="public-anuncio-card">
      <PublicMidiaPlaceholder midias={item.midias} compact />
      <div className="public-card-body">
        <div className="public-card-heading">
          <h2>{item.titulo ?? "Anuncio local"}</h2>
          <span>{formatPrice(item.preco)}</span>
        </div>
        <p>{item.descricaoResumo ?? "Resumo local indisponivel."}</p>
        <p className="public-card-location">{formatLocation(item.localizacao)}</p>
        {badges.length > 0 ? (
          <ul className="public-badge-list" aria-label="Marcadores do anuncio">
            {badges.map((badge) => (
              <li key={badge}>{badge}</li>
            ))}
          </ul>
        ) : null}
        {item.beneficiosPublicos.length > 0 ? (
          <ul className="public-benefit-list" aria-label="Beneficios publicos">
            {item.beneficiosPublicos.slice(0, 3).map((beneficio) => (
              <li key={beneficio}>{beneficio}</li>
            ))}
          </ul>
        ) : null}
        <Link className="public-card-link" href={`/anuncios/${item.slug}`}>
          Ver anuncio
        </Link>
      </div>
    </article>
  );
}

function formatPrice(preco: number | null): string {
  if (preco === null) {
    return "Valor local";
  }
  return new Intl.NumberFormat("pt-BR", {
    style: "currency",
    currency: "BRL",
    maximumFractionDigits: 0
  }).format(preco);
}

function formatLocation(item: AnuncioCardPublicoDto["localizacao"]): string {
  if (!item) {
    return "Localidade local";
  }
  return [item.bairro, item.cidade, item.uf].filter(Boolean).join(", ") || "Localidade local";
}
