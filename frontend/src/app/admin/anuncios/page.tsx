import type { Metadata } from "next";

import { skeletonMetadata } from "../../../lib/seo/localSeo";
import { AdminPlaceholderPage } from "../../../modules/admin/shell/AdminPlaceholderPage";

export const metadata: Metadata = skeletonMetadata("Anúncios admin skeleton", "/admin/anuncios");

export default function AdminAnunciosPage() {
  return <AdminPlaceholderPage moduleSlug="anuncios" />;
}
