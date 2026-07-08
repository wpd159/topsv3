import type { Metadata } from "next";

import { skeletonMetadata } from "../../lib/seo/localSeo";
import { PublicCookiePreferences } from "../../modules/public/components/PublicCookiePreferences";
import { PublicRouteShell } from "../../modules/public/skeleton/PublicRouteShell";

export const metadata: Metadata = skeletonMetadata("Política de Cookies", "/cookies");

export default function CookiesPage() {
  return (
    <PublicRouteShell title="Política de Cookies" routePattern="/cookies">
      <div className="public-legal-content">
        <p>
          Utilizamos cookies necessários para funcionamento do site e, mediante consentimento, cookies
          funcionais, de analytics e marketing para melhorar sua experiência.
        </p>
        <p>
          Você pode aceitar todos os cookies, rejeitar os não essenciais ou ajustar suas preferências a
          qualquer momento nesta página.
        </p>
        <PublicCookiePreferences />
      </div>
    </PublicRouteShell>
  );
}
