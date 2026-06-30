import type { Metadata } from "next";

import { skeletonMetadata } from "../../lib/seo/localSeo";
import { AdminShell } from "../../modules/admin/shell/AdminShell";

export const metadata: Metadata = skeletonMetadata("Admin local skeleton", "/admin");

export default function AdminOverviewPage() {
  return (
    <AdminShell title="Admin local skeleton">
      <section className="admin-panel" aria-label="Visão geral do admin">
        <h2>Visão geral</h2>
        <p>
          Área local para mapear os módulos administrativos previstos no SDD. Não há autenticação,
          permissões, dados reais, integração externa ou ação administrativa funcional nesta fase.
        </p>
      </section>
    </AdminShell>
  );
}
