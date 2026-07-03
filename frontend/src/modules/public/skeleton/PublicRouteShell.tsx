type PublicRouteShellProps = {
  title: string;
  routePattern: string;
  eyebrow?: string | null;
  showRoutePattern?: boolean;
  children: React.ReactNode;
};

export function PublicRouteShell({
  title,
  routePattern,
  eyebrow = "Previa local",
  showRoutePattern = true,
  children
}: PublicRouteShellProps) {
  return (
    <main className="public-route">
      <section className="shell public-shell">
        {eyebrow ? (
          <span className="status" data-debug-label="SKELETON LOCAL">
            {eyebrow}
          </span>
        ) : null}
        <h1>{title}</h1>
        {showRoutePattern ? <p className="route-pattern">Rota preservada: {routePattern}</p> : null}
        <div className="public-content">{children}</div>
      </section>
    </main>
  );
}
