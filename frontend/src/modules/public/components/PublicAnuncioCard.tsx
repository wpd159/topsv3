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
    item.midiaExtra ? "Mídia extra" : null,
    item.story ? "Stories" : null
  ].filter((badge): badge is string => Boolean(badge));

  return (
    <article className="public-anuncio-card">
      <PublicMidiaPlaceholder midias={item.midias} compact />
      <div className="public-card-body">
        <div className="public-card-heading">
          <span>{formatLocation(item.localizacao)}</span>
          <h2>{item.titulo ?? "Anúncio"}</h2>
          <strong>{formatPrice(item.preco)}</strong>
        </div>
        <p>{item.descricaoResumo ?? "Resumo indisponível no momento."}</p>
        {badges.length > 0 ? (
          <ul className="public-badge-list" aria-label="Marcadores do anúncio">
            {badges.map((badge) => (
              <li key={badge}>{badge}</li>
            ))}
          </ul>
        ) : null}
        {item.beneficiosPublicos.length > 0 ? (
          <ul className="public-benefit-list" aria-label="Benefícios públicos">
            {item.beneficiosPublicos.slice(0, 3).map((beneficio) => (
              <li key={beneficio}>{beneficio}</li>
            ))}
          </ul>
        ) : null}
        <div className="public-card-actions">
          <Link className="public-card-link" href={`/anuncios/${item.slug}`}>
            Ver anúncio
          </Link>
          {item.localizacao?.cidadeSlug && item.localizacao.uf ? (
            <Link
              className="public-card-secondary-link"
              href={`/acompanhantes/${item.localizacao.uf.toLowerCase()}/${item.localizacao.cidadeSlug}`}
            >
              Ver cidade
            </Link>
          ) : null}
        </div>
      </div>
    </article>
  );
}

function formatPrice(preco: number | null): string {
  if (preco === null) {
    return "Consultar";
  }
  return new Intl.NumberFormat("pt-BR", {
    style: "currency",
    currency: "BRL",
    maximumFractionDigits: 0
  }).format(preco);
}

function formatLocation(item: AnuncioCardPublicoDto["localizacao"]): string {
  if (!item) {
    return "Localidade a confirmar";
  }
  return [item.bairro, item.cidade, item.uf].filter(Boolean).join(", ") || "Localidade a confirmar";
}
