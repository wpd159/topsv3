"use client";

import { useEffect, useState } from "react";

type ConsentState = {
  adult: boolean;
  necessary: boolean;
  functional: boolean;
  analytics: boolean;
  marketing: boolean;
  ts?: number;
};

const CONSENT_KEY = "tops_public_adult_cookie_consent";
const CONSENT_COOKIE = "tops_public_consent";
const DAYS_180 = 60 * 60 * 24 * 180;

const defaultConsent: ConsentState = {
  adult: true,
  necessary: true,
  functional: false,
  analytics: true,
  marketing: false
};

const cookieCatalog = [
  {
    name: "session",
    provider: "topsdojob.com",
    type: "Necessário",
    duration: "Sessão",
    purpose: "Mantém a sessão do usuário autenticado."
  },
  {
    name: "csrf_token",
    provider: "topsdojob.com",
    type: "Necessário",
    duration: "Sessão",
    purpose: "Proteção contra CSRF em formulários."
  },
  {
    name: "tpz_locale",
    provider: "topsdojob.com",
    type: "Funcional",
    duration: "30 dias",
    purpose: "Recorda idioma e preferências de interface."
  },
  {
    name: "_ga",
    provider: "google.com",
    type: "Analytics",
    duration: "2 anos",
    purpose: "Diferencia usuários para métricas de navegação."
  },
  {
    name: "_gid",
    provider: "google.com",
    type: "Analytics",
    duration: "24 horas",
    purpose: "Diferencia usuários em sessões curtas."
  },
  {
    name: "_gcl_au",
    provider: "google.com",
    type: "Marketing",
    duration: "90 dias",
    purpose: "Atribuição de conversões em campanhas."
  },
  {
    name: "_fbp",
    provider: "facebook.com",
    type: "Marketing",
    duration: "90 dias",
    purpose: "Apoia segmentação e mensuração de campanhas."
  }
];

export function PublicCookiePreferences() {
  const [consent, setConsent] = useState<ConsentState>(defaultConsent);
  const [loaded, setLoaded] = useState(false);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    try {
      const raw = localStorage.getItem(CONSENT_KEY) ?? getCookie(CONSENT_COOKIE);
      if (raw) {
        const parsed = JSON.parse(raw) as Partial<ConsentState>;
        setConsent({
          adult: true,
          necessary: true,
          functional: !!parsed.functional,
          analytics: typeof parsed.analytics === "boolean" ? parsed.analytics : true,
          marketing: !!parsed.marketing,
          ts: parsed.ts
        });
      }
    } catch {
      setConsent(defaultConsent);
    } finally {
      setLoaded(true);
    }
  }, []);

  function persistConsent(next: ConsentState) {
    const normalized = { ...next, adult: true, necessary: true, ts: Date.now() };
    const value = JSON.stringify(normalized);
    localStorage.setItem(CONSENT_KEY, value);
    document.cookie = `${CONSENT_COOKIE}=${encodeURIComponent(value)}; Path=/; Max-Age=${DAYS_180}; SameSite=Lax`;
    window.dispatchEvent(new CustomEvent("tops:cookie-consent-updated", { detail: normalized }));
    setConsent(normalized);
    setSaved(true);
  }

  return (
    <section className="public-cookie-preferences" aria-label="Preferências de cookies">
      <div className="public-cookie-summary">
        <h2>Sua experiência de navegação</h2>
        <p>Necessários: segurança, login e funcionamento básico.</p>
        <p>Funcionais: idioma, filtros e preferências de interface.</p>
        <p>Analytics: métricas para evoluir produto e navegação.</p>
        <p>Marketing: mensuração e relevância de campanhas quando permitido.</p>
      </div>

      <div className="public-cookie-options">
        <CookieRow
          label="Necessários"
          description="Sempre ativos para segurança, login e integridade do site."
          checked
          disabled
          onChange={() => undefined}
        />
        <CookieRow
          label="Funcionais"
          description="Lembrar idioma e preferências de interface."
          checked={!!consent.functional}
          disabled={!loaded}
          onChange={(value) => setConsent((prev) => ({ ...prev, functional: value }))}
        />
        <CookieRow
          label="Analytics"
          description="Métricas agregadas para evoluir o produto."
          checked={!!consent.analytics}
          disabled={!loaded}
          onChange={(value) => setConsent((prev) => ({ ...prev, analytics: value }))}
        />
        <CookieRow
          label="Marketing"
          description="Mensuração e relevância de campanhas."
          checked={!!consent.marketing}
          disabled={!loaded}
          onChange={(value) => setConsent((prev) => ({ ...prev, marketing: value }))}
        />
      </div>

      <div className="public-cookie-actions">
        <button
          type="button"
          className="public-cookie-secondary"
          onClick={() => persistConsent({ ...defaultConsent, analytics: false })}
          disabled={!loaded}
        >
          Rejeitar não essenciais
        </button>
        <button
          type="button"
          className="public-cookie-primary"
          onClick={() =>
            persistConsent({
              adult: true,
              necessary: true,
              functional: true,
              analytics: true,
              marketing: true
            })
          }
          disabled={!loaded}
        >
          ACEITAR TODOS
        </button>
        <button
          type="button"
          className="public-cookie-secondary"
          onClick={() => persistConsent(consent)}
          disabled={!loaded}
        >
          Salvar preferências
        </button>
      </div>

      {saved ? <p className="public-cookie-saved">Preferências salvas.</p> : null}

      <div className="public-cookie-table-wrap">
        <table className="public-cookie-table">
          <thead>
            <tr>
              <th>Cookie</th>
              <th>Tipo</th>
              <th>Finalidade</th>
              <th>Validade</th>
              <th>Provedor</th>
            </tr>
          </thead>
          <tbody>
            {cookieCatalog.map((cookie) => (
              <tr key={`${cookie.name}-${cookie.provider}`}>
                <td>{cookie.name}</td>
                <td>{cookie.type}</td>
                <td>{cookie.purpose}</td>
                <td>{cookie.duration}</td>
                <td>{cookie.provider}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function CookieRow({
  label,
  description,
  checked,
  disabled = false,
  onChange
}: {
  label: string;
  description: string;
  checked: boolean;
  disabled?: boolean;
  onChange: (value: boolean) => void;
}) {
  return (
    <div className="public-cookie-row">
      <div>
        <strong>{label}</strong>
        <p>{description}</p>
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        aria-label={label}
        className="public-cookie-switch"
        data-checked={checked}
        disabled={disabled}
        onClick={() => onChange(!checked)}
      >
        <span />
      </button>
    </div>
  );
}

function getCookie(name: string) {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : null;
}
