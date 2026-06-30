import type { Metadata } from "next";

import { skeletonMetadata } from "../../../lib/seo/localSeo";
import { AdminPlaceholderPage } from "../../../modules/admin/shell/AdminPlaceholderPage";

export const metadata: Metadata = skeletonMetadata("Banners admin skeleton", "/admin/banners");

export default function AdminBannersPage() {
  return <AdminPlaceholderPage moduleSlug="banners" />;
}
