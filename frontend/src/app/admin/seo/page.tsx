import type { Metadata } from "next";

import { skeletonMetadata } from "../../../lib/seo/localSeo";
import { AdminPlaceholderPage } from "../../../modules/admin/shell/AdminPlaceholderPage";

export const metadata: Metadata = skeletonMetadata("SEO admin", "/admin/seo");

export default function AdminSeoPage() {
  return <AdminPlaceholderPage moduleSlug="seo" />;
}
