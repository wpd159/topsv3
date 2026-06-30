import type { Metadata } from "next";

import { skeletonMetadata } from "../../../lib/seo/localSeo";
import { AdminPlaceholderPage } from "../../../modules/admin/shell/AdminPlaceholderPage";

export const metadata: Metadata = skeletonMetadata("Créditos admin skeleton", "/admin/creditos");

export default function AdminCreditosPage() {
  return <AdminPlaceholderPage moduleSlug="creditos" />;
}
