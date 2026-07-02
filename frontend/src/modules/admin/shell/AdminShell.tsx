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
        <span className="status">ADMIN SKELETON LOCAL</span>
        <h1>{title}</h1>
        <p>
          Este shell e local e permanece apenas estrutural. A autenticacao administrativa minima
          valida sessao, papeis e permissoes, sem dados reais ou acoes de producao.
        </p>
        <div className="admin-notice">
          Perfis locais previstos: ADMIN, MODERADOR e COMERCIAL. Apenas moderacao local minima esta disponivel.
        </div>
        <AdminAuthPanel />
        <AdminReadonlyPanel />
        <AdminModerationPanel />
        <AdminOutboxPanel />
        {children}
        <nav className="admin-grid" aria-label="Modulos administrativos">
          {adminModules.map((module) => (
            <AdminModuleCard key={module.slug} module={module} />
          ))}
        </nav>
      </section>
    </main>
  );
}
