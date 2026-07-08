import type { Metadata } from "next";

import { skeletonMetadata } from "../../lib/seo/localSeo";
import { PublicRouteShell } from "../../modules/public/skeleton/PublicRouteShell";

export const metadata: Metadata = skeletonMetadata("Termos de Uso", "/termos-de-uso");

export default function TermosDeUsoPage() {
  return (
    <PublicRouteShell title="Termos de Uso" routePattern="/termos-de-uso">
      <div className="public-legal-content">
        <p>
          Este site contém conteúdo sexualmente explícito destinado exclusivamente a maiores de 18 anos.
          Se você for menor de idade ou se este tipo de conteúdo for considerado ofensivo, deve sair
          imediatamente.
        </p>
        <p>
          Ao navegar, você declara ser maior de 18 anos e assume responsabilidade pelo acesso ao conteúdo
          adulto publicado nos anúncios.
        </p>
        <p>
          O acesso é restrito a maiores de idade. Todos os perfis, imagens e descrições são de caráter
          adulto.
        </p>
        <p>
          Anunciantes e visitantes devem usar o site de forma responsável, respeitando as regras de
          publicação, privacidade, segurança e moderação aplicáveis.
        </p>
      </div>
    </PublicRouteShell>
  );
}
