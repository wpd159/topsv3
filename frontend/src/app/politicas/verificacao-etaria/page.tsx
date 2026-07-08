import type { Metadata } from "next";

import { skeletonMetadata } from "../../../lib/seo/localSeo";
import { PublicRouteShell } from "../../../modules/public/skeleton/PublicRouteShell";

export const metadata: Metadata = skeletonMetadata(
  "Verificação Etária",
  "/politicas/verificacao-etaria"
);

export default function VerificacaoEtariaPage() {
  return (
    <PublicRouteShell title="Verificação Etária" routePattern="/politicas/verificacao-etaria">
      <div className="public-legal-content">
        <p>
          O acesso a conteúdo adulto é restrito a maiores de 18 anos. Ao continuar navegando, o visitante
          declara que é maior de idade.
        </p>
        <p>
          Conteúdo restrito apenas para maiores de 18 anos pode exigir confirmação adicional antes da
          visualização.
        </p>
        <p>
          É necessário ser maior de 18 anos. Se você for menor de idade ou não quiser visualizar esse tipo
          de conteúdo, interrompa a navegação.
        </p>
      </div>
    </PublicRouteShell>
  );
}
