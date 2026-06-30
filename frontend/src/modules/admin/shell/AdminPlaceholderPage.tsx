import { AdminShell } from "./AdminShell";
import { findAdminModule } from "./adminModules";

type AdminPlaceholderPageProps = {
  moduleSlug: string;
};

export function AdminPlaceholderPage({ moduleSlug }: AdminPlaceholderPageProps) {
  const module = findAdminModule(moduleSlug);

  return (
    <AdminShell title={module.title}>
      <section className="admin-panel" aria-label="Status do módulo">
        <h2>Módulo placeholder</h2>
        <p>{module.summary}</p>
        <dl className="health-grid compact">
          <div>
            <dt>Estado</dt>
            <dd>skeleton local</dd>
          </div>
          <div>
            <dt>Dados reais</dt>
            <dd>ausentes</dd>
          </div>
          <div>
            <dt>Ações reais</dt>
            <dd>bloqueadas</dd>
          </div>
          <div>
            <dt>Perfis futuros</dt>
            <dd>{module.futureRoles.join(", ")}</dd>
          </div>
        </dl>
      </section>
    </AdminShell>
  );
}
