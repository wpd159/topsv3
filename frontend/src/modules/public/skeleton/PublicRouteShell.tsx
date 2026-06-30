type PublicRouteShellProps = {
  title: string;
  routePattern: string;
  children: React.ReactNode;
};

export function PublicRouteShell({ title, routePattern, children }: PublicRouteShellProps) {
  return (
    <main className="public-route">
      <section className="shell public-shell">
        <span className="status">SKELETON LOCAL</span>
        <h1>{title}</h1>
        <p className="route-pattern">{routePattern}</p>
        <div className="public-content">{children}</div>
      </section>
    </main>
  );
}
