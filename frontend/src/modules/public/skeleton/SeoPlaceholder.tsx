import { localUrl } from "../../../lib/seo/localSeo";

type SeoPlaceholderProps = {
  routePath: string;
};

export function SeoPlaceholder({ routePath }: SeoPlaceholderProps) {
  return (
    <section className="seo-placeholder" aria-label="SEO local">
      <h2>SEO local seguro</h2>
      <dl className="health-grid compact">
        <div>
          <dt>Indexação</dt>
          <dd>noindex</dd>
        </div>
        <div>
          <dt>Canonical local</dt>
          <dd>{localUrl(routePath)}</dd>
        </div>
        <div>
          <dt>Dados reais</dt>
          <dd>ausentes</dd>
        </div>
      </dl>
    </section>
  );
}
