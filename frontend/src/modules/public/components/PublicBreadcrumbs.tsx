import Link from "next/link";

import type { PublicBreadcrumbItem } from "../../../lib/seo/publicSeo";

type PublicBreadcrumbsProps = {
  items: readonly PublicBreadcrumbItem[];
};

export function PublicBreadcrumbs({ items }: PublicBreadcrumbsProps) {
  return (
    <nav className="public-breadcrumbs" aria-label="Breadcrumb">
      <ol>
        {items.map((item, index) => {
          const isLast = index === items.length - 1;
          return (
            <li key={`${item.label}-${index}`} aria-current={isLast ? "page" : undefined}>
              {item.href && !isLast ? <Link href={item.href}>{item.label}</Link> : <span>{item.label}</span>}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
