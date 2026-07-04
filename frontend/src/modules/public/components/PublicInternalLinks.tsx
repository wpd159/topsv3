import Link from "next/link";

import type { PublicLinkItem } from "../../../lib/seo/publicSeo";

type PublicInternalLinksProps = {
  title: string;
  links: readonly PublicLinkItem[];
  emptyText?: string;
};

export function PublicInternalLinks({ title, links, emptyText }: PublicInternalLinksProps) {
  if (links.length === 0) {
    return emptyText ? (
      <section className="public-internal-links" aria-label={title}>
        <h2>{title}</h2>
        <p>{emptyText}</p>
      </section>
    ) : null;
  }

  return (
    <section className="public-internal-links" aria-label={title}>
      <h2>{title}</h2>
      <ul>
        {links.map((link) => (
          <li key={link.href}>
            <Link href={link.href}>{link.label}</Link>
            {link.description ? <span>{link.description}</span> : null}
          </li>
        ))}
      </ul>
    </section>
  );
}
