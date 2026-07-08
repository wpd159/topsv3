"use client";

import Image from "next/image";
import Link from "next/link";
import { useState } from "react";

type HeaderLink = {
  href: string;
  label: string;
  className?: string;
};

const mainLinks: HeaderLink[] = [
  { href: "/acompanhantes/go/goiania", label: "Acompanhantes" },
  { href: "/anuncios/demo-goiania-livre-premium", label: "Anúncios" }
];

const accountLinks: HeaderLink[] = [
  { href: "/entrar", label: "Entrar", className: "public-header-login" },
  { href: "/entrar?modo=registro", label: "Registrar-se", className: "public-header-register" },
  { href: "/entrar?next=/anunciar", label: "PUBLICAR SEU ANÚNCIO", className: "public-header-cta" }
];

export function PublicSiteHeader() {
  const [menuOpen, setMenuOpen] = useState(false);

  return (
    <header className="public-site-header" aria-label="Cabeçalho público">
      <div className="public-site-header-inner">
        <Link className="public-brand" href="/">
          <Image
            src="/logo.webp"
            alt="Tops do Job"
            width={168}
            height={60}
            priority
            className="public-brand-logo"
          />
          <span className="public-brand-region">Brasil</span>
        </Link>

        <button
          className="public-mobile-menu-button"
          type="button"
          aria-expanded={menuOpen}
          aria-controls="public-mobile-menu"
          onClick={() => setMenuOpen((current) => !current)}
        >
          <span className="public-menu-icon" aria-hidden="true">
            <span />
            <span />
            <span />
          </span>
          <span className="sr-only">{menuOpen ? "Fechar menu" : "Abrir menu"}</span>
        </button>

        <nav className="public-site-nav" aria-label="Navegação principal">
          {mainLinks.map((link) => (
            <Link key={link.href} href={link.href}>
              {link.label}
            </Link>
          ))}
        </nav>

        <div className="public-site-actions">
          {accountLinks.map((link) => (
            <Link key={`${link.href}-${link.label}`} className={link.className} href={link.href}>
              {link.label}
            </Link>
          ))}
        </div>

        {menuOpen ? (
          <div className="public-mobile-drawer" id="public-mobile-menu">
            <div className="public-mobile-drawer-heading">
              <strong>Menu</strong>
              <button type="button" onClick={() => setMenuOpen(false)}>
                X
              </button>
            </div>
            <nav aria-label="Menu mobile">
              {accountLinks.map((link) => (
                <Link
                  key={`mobile-${link.href}-${link.label}`}
                  className={link.className}
                  href={link.href}
                  onClick={() => setMenuOpen(false)}
                >
                  {link.label}
                </Link>
              ))}
            </nav>
          </div>
        ) : null}
      </div>
    </header>
  );
}
