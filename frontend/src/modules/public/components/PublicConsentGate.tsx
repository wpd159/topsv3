"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";

const CONSENT_KEY = "tops_public_adult_cookie_consent";
const CONSENT_COOKIE = "tops_public_consent";
const DAYS_180 = 60 * 60 * 24 * 180;

const excludedPrefixes = [
  "/admin",
  "/entrar",
  "/registrar",
  "/cookies",
  "/termos-de-uso",
  "/politica-de-privacidade",
  "/politicas/verificacao-etaria"
];

export function PublicConsentGate() {
  const pathname = usePathname();
  const router = useRouter();
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (excludedPrefixes.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`))) {
      setOpen(false);
      return;
    }

    setOpen(!hasConsent());
  }, [pathname]);

  function acceptAll() {
    const value = JSON.stringify({
      adult: true,
      necessary: true,
      functional: true,
      analytics: true,
      marketing: true,
      ts: Date.now()
    });
    localStorage.setItem(CONSENT_KEY, value);
    document.cookie = `${CONSENT_COOKIE}=${encodeURIComponent(value)}; Path=/; Max-Age=${DAYS_180}; SameSite=Lax`;
    window.dispatchEvent(new CustomEvent("tops:cookie-consent-updated"));
    setOpen(false);
  }

  function openSettings() {
    router.push("/cookies");
  }

  if (!open) {
    return null;
  }

  return (
    <div className="public-consent-overlay" role="presentation">
      <section
        className="public-consent-modal"
        aria-labelledby="public-consent-title"
        aria-describedby="public-consent-description"
        role="dialog"
        aria-modal="true"
      >
        <div className="public-consent-icon" aria-hidden="true">
          18+
        </div>
        <div className="public-consent-copy">
          <h2 id="public-consent-title">Informações sobre conteúdo adulto</h2>
          <p id="public-consent-description">
            Este site contém conteúdo sexualmente explícito destinado exclusivamente a maiores de 18 anos.
            Se você for menor de idade ou se este tipo de conteúdo for considerado ofensivo, deve sair
            imediatamente.
          </p>
          <div className="public-consent-experience">
            <h3>Sua experiência de navegação</h3>
            <p>
              Utilizamos cookies necessários para funcionamento do site e, mediante consentimento, cookies
              funcionais, de analytics e marketing para melhorar sua experiência.
            </p>
          </div>
          <p className="public-consent-note">
            Ao continuar, você declara ser maior de 18 anos e concorda com os{" "}
            <Link href="/termos-de-uso">Termos de Uso</Link>, a{" "}
            <Link href="/politica-de-privacidade">Política de Privacidade</Link> e a{" "}
            <Link href="/cookies">Política de Cookies</Link>.
          </p>
        </div>
        <div className="public-consent-actions">
          <button type="button" className="public-consent-primary" onClick={acceptAll}>
            ACEITAR TODOS
          </button>
          <button type="button" className="public-consent-secondary" onClick={openSettings}>
            CONFIGURAÇÕES DE COOKIES
          </button>
        </div>
      </section>
    </div>
  );
}

function hasConsent() {
  if (typeof window === "undefined") {
    return true;
  }

  if (localStorage.getItem(CONSENT_KEY)) {
    return true;
  }

  return document.cookie.split(";").some((cookie) => cookie.trim().startsWith(`${CONSENT_COOKIE}=`));
}
