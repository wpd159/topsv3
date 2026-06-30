import type { AdminModule } from "./adminModules";

type AdminModuleCardProps = {
  module: AdminModule;
};

export function AdminModuleCard({ module }: AdminModuleCardProps) {
  return (
    <a className="admin-card" href={`/admin/${module.slug}`}>
      <span>{module.title}</span>
      <p>{module.summary}</p>
      <small>Perfis futuros: {module.futureRoles.join(", ")}</small>
    </a>
  );
}
