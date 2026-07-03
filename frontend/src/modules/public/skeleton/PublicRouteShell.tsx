type PublicRouteShellProps = {
  title: string;
  routePattern: string;
  children: React.ReactNode;
};

export function PublicRouteShell({ title, routePattern, children }: PublicRouteShellProps) {
  return (
    <main className="public-route">
      <section className="shell public-shell">
        <span className="status" data-debug-label="SKELETON LOCAL">
          Previa local
        </span>
        <h1>{title}</h1>
        <p className="route-pattern">Rota preservada: {routePattern}</p>
        <div className="public-content">{children}</div>
      </section>
    </main>
  );
}
