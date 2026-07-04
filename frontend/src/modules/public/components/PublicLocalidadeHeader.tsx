type PublicLocalidadeHeaderProps = {
  title: string;
  routeLabel: string;
  totalItens: number | null;
  status: string;
  locationParts: readonly string[];
};

export function PublicLocalidadeHeader({
  title,
  routeLabel,
  totalItens,
  status,
  locationParts
}: PublicLocalidadeHeaderProps) {
  return (
    <section className="public-locality-header" aria-label="Cabeçalho da localidade">
      <div>
        <span className="status">Localidade</span>
        <h2>{title}</h2>
        <p>{locationParts.filter(Boolean).join(" / ") || "Localidade a confirmar"}</p>
      </div>
      <dl className="public-locality-meta">
        <div>
          <dt>Rota</dt>
          <dd>{routeLabel}</dd>
        </div>
        <div>
          <dt>Total</dt>
          <dd>{totalItens ?? "indisponível"}</dd>
        </div>
        <div>
          <dt>Status</dt>
          <dd>{status}</dd>
        </div>
      </dl>
    </section>
  );
}
