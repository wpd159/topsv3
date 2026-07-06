import type { Metadata } from "next";

import { skeletonMetadata } from "../../../lib/seo/localSeo";
import { AdminPlaceholderPage } from "../../../modules/admin/shell/AdminPlaceholderPage";

export const metadata: Metadata = skeletonMetadata("Auditoria admin", "/admin/auditoria");

export default function AdminAuditoriaPage() {
  return <AdminPlaceholderPage moduleSlug="auditoria" />;
}
