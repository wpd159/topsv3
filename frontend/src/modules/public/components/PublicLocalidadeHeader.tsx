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
    <section className="public-locality-header" aria-label="Cabecalho da localidade">
      <div>
        <span className="status" data-debug-label="SKELETON LOCAL">
          Previa local
        </span>
        <h2>{title}</h2>
        <p>{locationParts.filter(Boolean).join(" / ") || "Localidade local"}</p>
      </div>
      <dl className="public-locality-meta">
        <div>
          <dt>Rota</dt>
          <dd>{routeLabel}</dd>
        </div>
        <div>
          <dt>Total local</dt>
          <dd>{totalItens ?? "indisponivel"}</dd>
        </div>
        <div>
          <dt>Status</dt>
          <dd>{status}</dd>
        </div>
      </dl>
    </section>
  );
}
