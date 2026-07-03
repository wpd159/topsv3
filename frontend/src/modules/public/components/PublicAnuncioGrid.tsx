import type { AnuncioCardPublicoDto } from "../../../lib/api/publicTypes";
import { PublicAnuncioCard } from "./PublicAnuncioCard";
import { PublicEmptyState } from "./PublicEmptyState";

type PublicAnuncioGridProps = {
  items: readonly AnuncioCardPublicoDto[];
  emptyTitle: string;
  emptyMessage: string;
};

export function PublicAnuncioGrid({ items, emptyTitle, emptyMessage }: PublicAnuncioGridProps) {
  if (items.length === 0) {
    return <PublicEmptyState title={emptyTitle} message={emptyMessage} />;
  }

  return (
    <section className="public-anuncio-grid" aria-label="Anuncios publicos locais">
      {items.map((item) => (
        <PublicAnuncioCard key={item.slug} item={item} />
      ))}
    </section>
  );
}
