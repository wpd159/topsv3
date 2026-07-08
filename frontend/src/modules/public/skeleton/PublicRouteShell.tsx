import { PublicSiteFooter } from "../components/PublicSiteFooter";
import { PublicSiteHeader } from "../components/PublicSiteHeader";

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
  eyebrow = null,
  showRoutePattern = false,
  children
}: PublicRouteShellProps) {
  return (
    <main className="public-route">
      <PublicSiteHeader />
      <section className="shell public-shell">
        {eyebrow ? (
          <span className="status" data-debug-label="PUBLIC_STATUS">
            {eyebrow}
          </span>
        ) : null}
        <h1>{title}</h1>
        {showRoutePattern ? <p className="route-pattern">{routePattern}</p> : null}
        <div className="public-content">{children}</div>
      </section>
      <PublicSiteFooter />
    </main>
  );
}
