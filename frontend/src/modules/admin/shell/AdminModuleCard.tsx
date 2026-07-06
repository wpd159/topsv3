import type { AdminModule } from "./adminModules";
import { formatAdminValues } from "./adminDisplay";

type AdminModuleCardProps = {
  module: AdminModule;
};

export function AdminModuleCard({ module }: AdminModuleCardProps) {
  return (
    <a className="admin-card" href={`/admin/${module.slug}`}>
      <span>{module.title}</span>
      <p>{module.summary}</p>
      <small>Perfis futuros: {formatAdminValues(module.futureRoles)}</small>
    </a>
  );
}
