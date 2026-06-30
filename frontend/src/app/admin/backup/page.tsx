import type { Metadata } from "next";

import { skeletonMetadata } from "../../../lib/seo/localSeo";
import { AdminPlaceholderPage } from "../../../modules/admin/shell/AdminPlaceholderPage";

export const metadata: Metadata = skeletonMetadata("Backup admin skeleton", "/admin/backup");

export default function AdminBackupPage() {
  return <AdminPlaceholderPage moduleSlug="backup" />;
}
