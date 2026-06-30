import { AdminModuleCard } from "./AdminModuleCard";
import { adminModules } from "./adminModules";

type AdminShellProps = {
  title: string;
  children: React.ReactNode;
};

export function AdminShell({ title, children }: AdminShellProps) {
  return (
    <main className="admin-shell">
      <section className="shell admin-shell-inner">
        <span className="status">ADMIN SKELETON LOCAL</span>
        <h1>{title}</h1>
        <p>
          Este shell é apenas estrutural. Autenticação, RBAC, dados reais e ações administrativas
          serão implementados em fases futuras.
        </p>
        <div className="admin-notice">
          Perfis futuros previstos: ADMIN, MODERADOR e COMERCIAL. Nenhuma ação real está disponível.
        </div>
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
