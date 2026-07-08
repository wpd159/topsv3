import type { Metadata } from "next";

import { skeletonMetadata } from "../../lib/seo/localSeo";
import { PublicRouteShell } from "../../modules/public/skeleton/PublicRouteShell";

export const metadata: Metadata = skeletonMetadata("Política de Privacidade", "/politica-de-privacidade");

export default function PoliticaDePrivacidadePage() {
  return (
    <PublicRouteShell title="Política de Privacidade" routePattern="/politica-de-privacidade">
      <div className="public-legal-content">
        <p>
          A navegação pode usar cookies necessários para segurança, sessão e funcionamento básico do site.
          Cookies funcionais, de analytics e marketing dependem das preferências escolhidas pelo visitante.
        </p>
        <p>
          Métricas de navegação devem ser usadas de forma agregada para melhorar páginas, fluxos e
          desempenho, sem expor dados sensíveis em áreas públicas.
        </p>
        <p>
          Dados de contato, documentos, informações privadas de anunciantes e registros de acesso restrito
          não devem ser publicados como conteúdo aberto.
        </p>
        <p>
          Você pode revisar suas preferências em <a href="/cookies">Política de Cookies</a>.
        </p>
      </div>
    </PublicRouteShell>
  );
}
