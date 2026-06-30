import type { Metadata } from "next";

import { skeletonMetadata } from "../../../lib/seo/localSeo";
import { AdminPlaceholderPage } from "../../../modules/admin/shell/AdminPlaceholderPage";

export const metadata: Metadata = skeletonMetadata("Comercial admin skeleton", "/admin/comercial");

export default function AdminComercialPage() {
  return <AdminPlaceholderPage moduleSlug="comercial" />;
}
