import type { Metadata } from "next";

import { AdminPremiumPanel } from "../../../modules/admin/shell/AdminPremiumPanel";
import { skeletonMetadata } from "../../../lib/seo/localSeo";

export const metadata: Metadata = skeletonMetadata("Premium admin skeleton", "/admin/premium");

export default function AdminPremiumPage() {
  return <AdminPremiumPanel />;
}
