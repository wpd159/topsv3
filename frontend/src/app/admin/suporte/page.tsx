import type { Metadata } from "next";

import { skeletonMetadata } from "../../../lib/seo/localSeo";
import { AdminPlaceholderPage } from "../../../modules/admin/shell/AdminPlaceholderPage";

export const metadata: Metadata = skeletonMetadata("Suporte admin", "/admin/suporte");

export default function AdminSuportePage() {
  return <AdminPlaceholderPage moduleSlug="suporte" />;
}
