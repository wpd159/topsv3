import type { Metadata } from "next";

import { skeletonMetadata } from "../../../lib/seo/localSeo";
import { AdminPagamentosPanel } from "../../../modules/admin/shell/AdminPagamentosPanel";

export const metadata: Metadata = skeletonMetadata("Pagamentos admin local", "/admin/financeiro");

export default function AdminFinanceiroPage() {
  return <AdminPagamentosPanel />;
}
