import { AdminShell } from "./AdminShell";
import { formatAdminValues } from "./adminDisplay";
import { findAdminModule } from "./adminModules";

type AdminPlaceholderPageProps = {
  moduleSlug: string;
};

export function AdminPlaceholderPage({ moduleSlug }: AdminPlaceholderPageProps) {
  const adminModule = findAdminModule(moduleSlug);

  return (
    <AdminShell title={adminModule.title}>
      <section className="admin-panel" aria-label="Status do módulo">
        <h2>Modulo em preparacao</h2>
        <p>{adminModule.summary}</p>
        <dl className="health-grid compact">
          <div>
            <dt>Estado</dt>
            <dd>estrutura local preparada</dd>
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
            <dd>{formatAdminValues(adminModule.futureRoles)}</dd>
          </div>
        </dl>
      </section>
    </AdminShell>
  );
}
