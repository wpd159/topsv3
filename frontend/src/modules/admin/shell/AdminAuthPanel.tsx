"use client";

import { useEffect, useState } from "react";

import {
  getAdminMe,
  getAdminPermissions,
  loginAdmin,
  logoutAdmin
} from "../../../lib/api/adminAuthApi";
import type { AdminMeDto, AdminPermissionDto } from "../../../lib/api/adminAuthTypes";
import { formatAdminValue, formatAdminValues } from "./adminDisplay";

type AuthState = "carregando" | "autenticado" | "nao_autenticado" | "falha";

export function AdminAuthPanel() {
  const [state, setState] = useState<AuthState>("carregando");
  const [me, setMe] = useState<AdminMeDto | null>(null);
  const [permissions, setPermissions] = useState<readonly AdminPermissionDto[]>([]);
  const [login, setLogin] = useState("");
  const [credencial, setCredencial] = useState("");
  const [message, setMessage] = useState("consultando sessao administrativa local");

  useEffect(() => {
    void refreshSession();
  }, []);

  async function refreshSession() {
    const response = await getAdminMe();
    if (!response.ok) {
      setMe(null);
      setPermissions([]);
      setState(response.status === 401 ? "nao_autenticado" : "falha");
      setMessage(response.status === 401 ? "sessao administrativa ausente" : response.message);
      return;
    }
    const permissionsResponse = await getAdminPermissions();
    setMe(response.data);
    setPermissions(permissionsResponse.ok ? permissionsResponse.data.permissoes : []);
    setState("autenticado");
    setMessage("sessao administrativa local ativa");
  }

  async function handleLogin(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setState("carregando");
    const response = await loginAdmin(login, credencial);
    setCredencial("");
    if (!response.ok) {
      setMe(null);
      setPermissions([]);
      setState("nao_autenticado");
      setMessage("credenciais administrativas invalidas ou ausentes");
      return;
    }
    await refreshSession();
  }

  async function handleLogout() {
    setState("carregando");
    await logoutAdmin();
    setMe(null);
    setPermissions([]);
    setState("nao_autenticado");
    setMessage("logout local executado");
  }

  return (
    <section className="admin-panel" aria-label="Sessao administrativa local">
      <h2>Sessao local</h2>
      <dl className="health-grid compact">
        <div>
          <dt>Estado</dt>
          <dd>{formatAdminValue(state)}</dd>
        </div>
        <div>
          <dt>Papeis</dt>
          <dd>{formatAdminValues(me?.papeis, "sem sessao")}</dd>
        </div>
        <div>
          <dt>Permissoes</dt>
          <dd>{permissions.length}</dd>
        </div>
        <div>
          <dt>Credenciais reais</dt>
          <dd>ausentes</dd>
        </div>
      </dl>
      <p>{message}</p>
      {me ? (
        <div className="admin-auth-actions">
          <p>{me.email ?? "usuario sintetico local"}</p>
          <button type="button" className="local-action" onClick={handleLogout}>
            Logout
          </button>
        </div>
      ) : (
        <form className="admin-auth-form" onSubmit={handleLogin}>
          <label>
            Login local
            <input
              value={login}
              onChange={(event) => setLogin(event.target.value)}
              autoComplete="username"
              placeholder="admin.local@example.invalid"
            />
          </label>
          <label>
            Credencial local
            <input
              type="password"
              value={credencial}
              onChange={(event) => setCredencial(event.target.value)}
              autoComplete="current-password"
            />
          </label>
          <button type="submit" className="local-action" disabled={state === "carregando"}>
            Entrar
          </button>
        </form>
      )}
      {permissions.length > 0 ? (
        <ul className="admin-permission-list" aria-label="Permissoes administrativas locais">
          {permissions.map((permission) => (
            <li key={permission.codigo}>
              <span>{formatAdminValue(permission.codigo)}</span>
              <small>{permission.descricao}</small>
            </li>
          ))}
        </ul>
      ) : null}
    </section>
  );
}
