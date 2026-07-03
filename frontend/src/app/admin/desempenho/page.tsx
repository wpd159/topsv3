import type { Metadata } from "next";

import { skeletonMetadata } from "../../../lib/seo/localSeo";
import { AdminDesempenhoPanel } from "../../../modules/admin/shell/AdminDesempenhoPanel";

export const metadata: Metadata = skeletonMetadata("Desempenho admin local", "/admin/desempenho");

export default function AdminDesempenhoPage() {
  return <AdminDesempenhoPanel />;
}
