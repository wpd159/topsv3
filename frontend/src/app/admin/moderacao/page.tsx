import type { Metadata } from "next";

import { skeletonMetadata } from "../../../lib/seo/localSeo";
import { AdminPlaceholderPage } from "../../../modules/admin/shell/AdminPlaceholderPage";

export const metadata: Metadata = skeletonMetadata("Moderação admin skeleton", "/admin/moderacao");

export default function AdminModeracaoPage() {
  return <AdminPlaceholderPage moduleSlug="moderacao" />;
}
