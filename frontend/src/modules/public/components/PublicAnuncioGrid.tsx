import type { AnuncioCardPublicoDto } from "../../../lib/api/publicTypes";
import { PublicAnuncioCard } from "./PublicAnuncioCard";
import { PublicEmptyState } from "./PublicEmptyState";

type PublicAnuncioGridProps = {
  items: readonly AnuncioCardPublicoDto[];
  emptyTitle: string;
  emptyMessage: string;
  title?: string;
  summary?: string;
};

export function PublicAnuncioGrid({ items, emptyTitle, emptyMessage, title, summary }: PublicAnuncioGridProps) {
  if (items.length === 0) {
    return (
      <section className="public-listing-section" aria-label={title ?? emptyTitle}>
        {title ? (
          <div className="public-listing-heading">
            <span className="public-section-kicker">Perfis</span>
            <h2>{title}</h2>
            {summary ? <p>{summary}</p> : null}
          </div>
        ) : null}
        <PublicEmptyState title={emptyTitle} message={emptyMessage} />
      </section>
    );
  }

  return (
    <section className="public-listing-section" aria-label={title ?? "Anúncios públicos"}>
      <div className="public-listing-heading">
        <span className="public-section-kicker">{items.length} perfis</span>
        <h2>{title ?? "Perfis disponíveis"}</h2>
        {summary ? <p>{summary}</p> : null}
      </div>
      <div className="public-anuncio-grid">
        {items.map((item) => (
          <PublicAnuncioCard key={item.slug} item={item} />
        ))}
      </div>
    </section>
  );
}
