import type { Metadata } from "next";

import { skeletonMetadata } from "../../../lib/seo/localSeo";
import { AdminPlaceholderPage } from "../../../modules/admin/shell/AdminPlaceholderPage";

export const metadata: Metadata = skeletonMetadata("Mídia admin skeleton", "/admin/midia");

export default function AdminMidiaPage() {
  return <AdminPlaceholderPage moduleSlug="midia" />;
}
