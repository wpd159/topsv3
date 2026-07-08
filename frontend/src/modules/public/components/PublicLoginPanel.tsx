"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";

import { loginAdmin } from "../../../lib/api/adminAuthApi";

function safeNextPath(value: string | null): string {
  if (!value || !value.startsWith("/") || value.startsWith("//") || value.startsWith("/api/")) {
    return "/admin";
  }
  return value;
}

export function PublicLoginPanel() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const nextPath = safeNextPath(searchParams.get("next"));
  const mode = searchParams.get("modo");
  const [login, setLogin] = useState("");
  const [credencial, setCredencial] = useState("");
  const [message, setMessage] = useState(mode === "registro" ? "Entre para continuar seu cadastro." : "Entre para continuar.");
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setMessage("Verificando acesso.");
    const response = await loginAdmin(login, credencial);
    setCredencial("");
    setSubmitting(false);

    if (!response.ok) {
      setMessage("Não foi possível entrar com esses dados.");
      return;
    }

    router.push(nextPath);
  }

  return (
    <section className="public-login-card" aria-labelledby="public-login-title">
      <div className="public-login-heading">
        <span className="public-section-kicker">Acesso</span>
        <h1 id="public-login-title">Entrar no Tops do Job</h1>
        <p>{message}</p>
      </div>
      <form className="public-login-form" onSubmit={handleSubmit}>
        <label>
          E-mail
          <input
            value={login}
            onChange={(event) => setLogin(event.target.value)}
            autoComplete="username"
            inputMode="email"
            placeholder="seuemail@exemplo.com"
          />
        </label>
        <label>
          Senha
          <input
            type="password"
            value={credencial}
            onChange={(event) => setCredencial(event.target.value)}
            autoComplete="current-password"
          />
        </label>
        <button className="local-action" type="submit" disabled={submitting}>
          {submitting ? "Entrando..." : "Entrar"}
        </button>
      </form>
    </section>
  );
}
