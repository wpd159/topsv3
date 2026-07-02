import type { Metadata } from "next";

import { skeletonMetadata } from "../../lib/seo/localSeo";
import { AdminShell } from "../../modules/admin/shell/AdminShell";

export const metadata: Metadata = skeletonMetadata("Admin local skeleton", "/admin");

export default function AdminOverviewPage() {
  return (
    <AdminShell title="Admin local skeleton">
      <section className="admin-panel" aria-label="Visao geral do admin">
        <h2>Visao geral</h2>
        <p>
          Area local para mapear os modulos administrativos previstos no SDD. A autenticacao e o RBAC
          minimos existem apenas para sessao local, sem dados reais, integracao externa ou acao
          administrativa funcional nesta fase.
        </p>
      </section>
    </AdminShell>
  );
}
