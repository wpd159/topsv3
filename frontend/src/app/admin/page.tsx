import type { Metadata } from "next";

import { skeletonMetadata } from "../../lib/seo/localSeo";
import { AdminShell } from "../../modules/admin/shell/AdminShell";

export const metadata: Metadata = skeletonMetadata("Admin", "/admin");

export default function AdminOverviewPage() {
  return (
    <AdminShell title="Admin">
      <section className="admin-panel" aria-label="Visão geral do admin">
        <h2>Visão geral</h2>
        <p>Área para acompanhar os módulos administrativos, autenticação, RBAC e ações autorizadas.</p>
      </section>
    </AdminShell>
  );
}
