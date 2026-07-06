"use client";

import { useEffect, useState } from "react";

import {
  getAdminMe,
  getAdminPermissions,
  loginAdmin,
  logoutAdmin
} from "../../../lib/api/adminAuthApi";
import type { AdminMeDto, AdminPermissionDto } from "../../../lib/api/adminAuthTypes";
import { formatAdminText, formatAdminValue, formatAdminValues } from "./adminDisplay";

type AuthState = "carregando" | "autenticado" | "nao_autenticado" | "falha";

export function AdminAuthPanel() {
  const [state, setState] = useState<AuthState>("carregando");
  const [me, setMe] = useState<AdminMeDto | null>(null);
  const [permissions, setPermissions] = useState<readonly AdminPermissionDto[]>([]);
  const [login, setLogin] = useState("");
  const [credencial, setCredencial] = useState("");
  const [message, setMessage] = useState("consultando sessão administrativa");

  useEffect(() => {
    void refreshSession();
  }, []);

  async function refreshSession() {
    const response = await getAdminMe();
    if (!response.ok) {
      setMe(null);
      setPermissions([]);
      setState(response.status === 401 ? "nao_autenticado" : "falha");
      setMessage(response.status === 401 ? "sessão administrativa ausente" : response.message);
      return;
    }
    const permissionsResponse = await getAdminPermissions();
    setMe(response.data);
    setPermissions(permissionsResponse.ok ? permissionsResponse.data.permissoes : []);
    setState("autenticado");
    setMessage("sessão administrativa ativa");
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
      setMessage("credenciais administrativas inválidas ou ausentes");
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
    setMessage("sessão encerrada");
  }

  return (
    <section className="admin-panel" aria-label="Sessão administrativa">
      <h2>Sessão</h2>
      <dl className="health-grid compact">
        <div>
          <dt>Estado</dt>
          <dd>{formatAdminValue(state)}</dd>
        </div>
        <div>
          <dt>Papéis</dt>
          <dd>{formatAdminValues(me?.papeis, "sem sessão")}</dd>
        </div>
        <div>
          <dt>Permissões</dt>
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
          <p>usuário autenticado</p>
          <button type="button" className="local-action" onClick={handleLogout}>
            Logout
          </button>
        </div>
      ) : (
        <form className="admin-auth-form" onSubmit={handleLogin}>
          <label>
            Login
            <input
              value={login}
              onChange={(event) => setLogin(event.target.value)}
              autoComplete="username"
              placeholder="admin@example.invalid"
            />
          </label>
          <label>
            Credencial
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
        <ul className="admin-permission-list" aria-label="Permissões administrativas">
          {permissions.map((permission) => (
            <li key={permission.codigo}>
              <span>{formatAdminValue(permission.codigo)}</span>
              <small>{formatAdminText(permission.descricao)}</small>
            </li>
          ))}
        </ul>
      ) : null}
    </section>
  );
}
