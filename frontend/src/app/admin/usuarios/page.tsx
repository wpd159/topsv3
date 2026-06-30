import type { Metadata } from "next";

import { skeletonMetadata } from "../../../lib/seo/localSeo";
import { AdminPlaceholderPage } from "../../../modules/admin/shell/AdminPlaceholderPage";

export const metadata: Metadata = skeletonMetadata("Usuários admin skeleton", "/admin/usuarios");

export default function AdminUsuariosPage() {
  return <AdminPlaceholderPage moduleSlug="usuarios" />;
}
