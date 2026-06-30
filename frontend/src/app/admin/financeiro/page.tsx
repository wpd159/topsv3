import type { Metadata } from "next";

import { skeletonMetadata } from "../../../lib/seo/localSeo";
import { AdminPlaceholderPage } from "../../../modules/admin/shell/AdminPlaceholderPage";

export const metadata: Metadata = skeletonMetadata("Financeiro admin skeleton", "/admin/financeiro");

export default function AdminFinanceiroPage() {
  return <AdminPlaceholderPage moduleSlug="financeiro" />;
}
