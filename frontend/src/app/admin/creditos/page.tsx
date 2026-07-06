import type { Metadata } from "next";

import { skeletonMetadata } from "../../../lib/seo/localSeo";
import { AdminCreditosPanel } from "../../../modules/admin/shell/AdminCreditosPanel";

export const metadata: Metadata = skeletonMetadata("Créditos admin", "/admin/creditos");

export default function AdminCreditosPage() {
  return <AdminCreditosPanel />;
}
