"use client";

import Image from "next/image";
import Link from "next/link";
import { useEffect, useState } from "react";

import { PublicAuthModal, PublicAuthMode, safeNextPath } from "./PublicAuthModal";

type HeaderLink = {
  href: string;
  label: string;
  className?: string;
};

const mainLinks: HeaderLink[] = [
  { href: "/acompanhantes/go/goiania", label: "Acompanhantes" },
  { href: "/anuncios/demo-goiania-livre-premium", label: "Anúncios" }
];

export function PublicSiteHeader() {
  const [menuOpen, setMenuOpen] = useState(false);
  const [authMode, setAuthMode] = useState<PublicAuthMode>(null);
  const [nextPath, setNextPath] = useState<string | null>(null);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const next = safeNextPath(params.get("next"));
    const shouldOpenLogin = params.get("login") === "1";
    const shouldOpenRegister = params.get("registro") === "1";

    if (shouldOpenLogin || shouldOpenRegister) {
      setNextPath(next);
      setAuthMode(shouldOpenRegister ? "register" : "login");
      params.delete("login");
      params.delete("registro");
      params.delete("next");
      const cleanSearch = params.toString();
      window.history.replaceState(null, "", `${window.location.pathname}${cleanSearch ? `?${cleanSearch}` : ""}`);
    }
  }, []);

  function openAuth(mode: PublicAuthMode, next: string | null = null) {
    setMenuOpen(false);
    setNextPath(next);
    setAuthMode(mode);
  }

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
          <button className="public-header-login" type="button" onClick={() => openAuth("login")}>
            Entrar
          </button>
          <button className="public-header-register" type="button" onClick={() => openAuth("register")}>
            Registrar-se
          </button>
          <button className="public-header-cta" type="button" onClick={() => openAuth("login", "/anunciar")}>
            PUBLICAR SEU ANÚNCIO
          </button>
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
              <button className="public-header-login" type="button" onClick={() => openAuth("login")}>
                Entrar
              </button>
              <button className="public-header-register" type="button" onClick={() => openAuth("register")}>
                Registrar-se
              </button>
              <button className="public-header-cta" type="button" onClick={() => openAuth("login", "/anunciar")}>
                PUBLICAR SEU ANÚNCIO
              </button>
            </nav>
          </div>
        ) : null}
      </div>
      <PublicAuthModal mode={authMode} nextPath={nextPath} onModeChange={setAuthMode} />
    </header>
  );
}
