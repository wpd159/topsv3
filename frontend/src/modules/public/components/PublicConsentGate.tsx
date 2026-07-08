"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";

const AGE_GATE_COOKIE_NAME = "age_gate_accepted";
const AGE_GATE_STORAGE_KEY = "age_gate_accepted_until";
const AGE_GATE_TTL_MS = 7 * 24 * 60 * 60 * 1000;

const excludedPrefixes = [
  "/admin",
  "/cookies",
  "/termos-de-uso",
  "/politica-de-privacidade",
  "/politicas/verificacao-etaria"
];

export function PublicConsentGate() {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const [accepting, setAccepting] = useState(false);

  useEffect(() => {
    if (excludedPrefixes.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`))) {
      setOpen(false);
      return;
    }

    setOpen(!hasConsent());
  }, [pathname]);

  function acceptAgeGate() {
    setAccepting(true);
    const expiresAt = Date.now() + AGE_GATE_TTL_MS;
    const maxAgeSeconds = Math.max(1, Math.floor((expiresAt - Date.now()) / 1000));
    const value = `v1.${expiresAt}`;

    localStorage.setItem(AGE_GATE_STORAGE_KEY, String(expiresAt));
    document.cookie = `${AGE_GATE_COOKIE_NAME}=${encodeURIComponent(value)}; Path=/; Max-Age=${maxAgeSeconds}; SameSite=Lax`;
    setOpen(false);
    setAccepting(false);
  }

  function leaveSite() {
    window.location.href = "https://www.google.com";
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
          !
        </div>
        <div className="public-consent-copy">
          <h2 id="public-consent-title">Aviso de Conteúdo Adulto</h2>
          <p id="public-consent-description">
            Este site contém conteúdo sexualmente explícito destinado exclusivamente a maiores de 18 anos.
            Se você for menor de idade ou se este tipo de conteúdo for considerado ofensivo, deve sair
            imediatamente.
          </p>
          <p className="public-consent-note">
            Ao clicar em <b>Aceitar</b>, declaro que sou maior de 18 anos e li os{" "}
            <Link href="/termos-de-uso">Termos de Uso</Link>.
          </p>
          <p className="public-consent-note">
            O acesso é restrito a maiores de idade. Todos os perfis, imagens e descrições são de caráter
            adulto. Preferências de navegação ficam em <Link href="/cookies">Política de Cookies</Link>.
          </p>
        </div>
        <div className="public-consent-actions">
          <button type="button" className="public-consent-secondary" onClick={leaveSite} disabled={accepting}>
            Sair
          </button>
          <button type="button" className="public-consent-primary" onClick={acceptAgeGate} disabled={accepting}>
            {accepting ? "Salvando..." : "Aceitar"}
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

  const storageExpiresAt = Number(localStorage.getItem(AGE_GATE_STORAGE_KEY));
  if (Number.isFinite(storageExpiresAt) && storageExpiresAt > Date.now()) {
    return true;
  }

  const rawCookie = document.cookie
    .split(";")
    .map((cookie) => cookie.trim())
    .find((cookie) => cookie.startsWith(`${AGE_GATE_COOKIE_NAME}=`));

  if (!rawCookie) return false;
  const rawValue = decodeURIComponent(rawCookie.split("=").slice(1).join("="));
  if (rawValue === "yes") return true;
  if (!rawValue.startsWith("v1.")) return false;

  const expiresAt = Number(rawValue.slice(3));
  return Number.isFinite(expiresAt) && expiresAt > Date.now();
}
