import { localUrl } from "../../../lib/seo/localSeo";

type SeoPlaceholderProps = {
  routePath: string;
};

export function SeoPlaceholder({ routePath }: SeoPlaceholderProps) {
  return (
    <section className="seo-placeholder" aria-label="Navegação por localidade">
      <h2>Navegação por localidade</h2>
      <dl className="health-grid compact">
        <div>
          <dt>Organização</dt>
          <dd>cidade e bairro</dd>
        </div>
        <div>
          <dt>Endereço</dt>
          <dd>{localUrl(routePath)}</dd>
        </div>
        <div>
          <dt>Exibição</dt>
          <dd>controlada</dd>
        </div>
      </dl>
    </section>
  );
}
