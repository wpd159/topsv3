import { AdminModuleCard } from "./AdminModuleCard";
import { AdminAuthPanel } from "./AdminAuthPanel";
import { AdminModerationPanel } from "./AdminModerationPanel";
import { AdminOutboxPanel } from "./AdminOutboxPanel";
import { AdminReadonlyPanel } from "./AdminReadonlyPanel";
import { adminModules } from "./adminModules";

type AdminShellProps = {
  title: string;
  children: React.ReactNode;
};

export function AdminShell({ title, children }: AdminShellProps) {
  return (
    <main className="admin-shell">
      <section className="shell admin-shell-inner">
        <span className="status">PAINEL ADMIN</span>
        <h1>{title}</h1>
        <p>Área administrativa para acompanhar sessões, papéis, permissões e ações autorizadas.</p>
        <div className="admin-notice">
          Perfis previstos: Admin, Moderador e Comercial. A moderação mínima está disponível.
        </div>
        <AdminAuthPanel />
        <AdminReadonlyPanel />
        <AdminModerationPanel />
        <AdminOutboxPanel />
        {children}
        <nav className="admin-grid" aria-label="Módulos administrativos">
          {adminModules.map((module) => (
            <AdminModuleCard key={module.slug} module={module} />
          ))}
        </nav>
      </section>
    </main>
  );
}
