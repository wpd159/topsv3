"use client";

import Image from "next/image";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";

import { loginAdmin } from "../../../lib/api/adminAuthApi";

export type PublicAuthMode = "login" | "register" | null;

type PublicAuthModalProps = {
  mode: PublicAuthMode;
  nextPath?: string | null;
  onModeChange: (mode: PublicAuthMode) => void;
};

export function PublicAuthModal({ mode, nextPath, onModeChange }: PublicAuthModalProps) {
  const router = useRouter();
  const [login, setLogin] = useState("");
  const [credencial, setCredencial] = useState("");
  const [cadastroNome, setCadastroNome] = useState("");
  const [cadastroEmail, setCadastroEmail] = useState("");
  const [cadastroTelefone, setCadastroTelefone] = useState("");
  const [cadastroNascimento, setCadastroNascimento] = useState("");
  const [cadastroSenha, setCadastroSenha] = useState("");
  const [aceiteTermos, setAceiteTermos] = useState(false);
  const [message, setMessage] = useState("");
  const [submitting, setSubmitting] = useState(false);

  if (!mode) {
    return null;
  }

  const redirectTarget = safeNextPath(nextPath) ?? "/admin";

  async function handleLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;

    if (!login.trim() || !credencial.trim()) {
      setMessage("Preencha e-mail e senha.");
      return;
    }

    setSubmitting(true);
    setMessage("Verificando acesso.");
    const response = await loginAdmin(login, credencial);
    setCredencial("");
    setSubmitting(false);

    if (!response.ok) {
      setMessage("Não foi possível entrar com esses dados.");
      return;
    }

    onModeChange(null);
    router.push(redirectTarget);
  }

  function handleRegister(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!cadastroNome.trim() || !cadastroEmail.trim() || !cadastroTelefone.trim()) {
      setMessage("Preencha nome, e-mail e telefone.");
      return;
    }
    if (!isAtLeast18(cadastroNascimento)) {
      setMessage("Cadastro permitido apenas para maiores de 18 anos.");
      return;
    }
    if (!cadastroSenha.trim() || cadastroSenha.length < 8) {
      setMessage("Informe uma senha com pelo menos 8 caracteres.");
      return;
    }
    if (!aceiteTermos) {
      setMessage("Aceite os termos para continuar.");
      return;
    }

    setMessage("Não foi possível concluir o cadastro agora. Tente novamente mais tarde.");
  }

  return (
    <div className="public-auth-backdrop" role="presentation" onMouseDown={() => onModeChange(null)}>
      <section
        className="public-auth-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="public-auth-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <button className="public-auth-close" type="button" onClick={() => onModeChange(null)} aria-label="Fechar">
          X
        </button>
        <Image src="/logo.webp" alt="Tops do Job" width={170} height={60} className="public-auth-logo" />

        {mode === "login" ? (
          <>
            <div className="public-auth-heading">
              <h2 id="public-auth-title">Entrar</h2>
              <p>Acesse sua conta para continuar.</p>
            </div>
            <form className="public-auth-form" onSubmit={handleLogin}>
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
              <button className="public-auth-primary" type="submit" disabled={submitting}>
                {submitting ? "Entrando..." : "Entrar"}
              </button>
            </form>
            <button className="public-auth-link-button" type="button" onClick={() => onModeChange("register")}>
              Criar conta
            </button>
          </>
        ) : (
          <>
            <div className="public-auth-heading">
              <h2 id="public-auth-title">Registrar-se</h2>
              <p>Informe seus dados para criar sua conta.</p>
            </div>
            <form className="public-auth-form" onSubmit={handleRegister}>
              <label>
                Nome de usuário
                <input value={cadastroNome} onChange={(event) => setCadastroNome(event.target.value)} />
              </label>
              <label>
                E-mail
                <input
                  value={cadastroEmail}
                  onChange={(event) => setCadastroEmail(event.target.value)}
                  autoComplete="email"
                  inputMode="email"
                />
              </label>
              <label>
                Telefone
                <input
                  value={cadastroTelefone}
                  onChange={(event) => setCadastroTelefone(event.target.value)}
                  autoComplete="tel"
                  inputMode="tel"
                />
              </label>
              <label>
                Data de nascimento
                <input
                  type="date"
                  value={cadastroNascimento}
                  onChange={(event) => setCadastroNascimento(event.target.value)}
                  max={adultMaxDate()}
                />
              </label>
              <label>
                Senha
                <input
                  type="password"
                  value={cadastroSenha}
                  onChange={(event) => setCadastroSenha(event.target.value)}
                  autoComplete="new-password"
                />
              </label>
              <label className="public-auth-check">
                <input
                  type="checkbox"
                  checked={aceiteTermos}
                  onChange={(event) => setAceiteTermos(event.target.checked)}
                />
                <span>
                  Li e aceito os <Link href="/termos-de-uso">Termos de Uso</Link> e a{" "}
                  <Link href="/politica-de-privacidade">Política de Privacidade</Link>.
                </span>
              </label>
              <button className="public-auth-primary" type="submit">
                Criar conta
              </button>
            </form>
            <button className="public-auth-link-button" type="button" onClick={() => onModeChange("login")}>
              Já tenho conta
            </button>
          </>
        )}

        {message ? <p className="public-auth-message">{message}</p> : null}
      </section>
    </div>
  );
}

export function safeNextPath(value?: string | null): string | null {
  if (!value || !value.startsWith("/") || value.startsWith("//") || value.startsWith("/api/")) {
    return null;
  }
  return value;
}

function isAtLeast18(value: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const birth = new Date(`${value}T00:00:00`);
  if (Number.isNaN(birth.getTime())) return false;

  const today = new Date();
  let age = today.getFullYear() - birth.getFullYear();
  const monthDiff = today.getMonth() - birth.getMonth();

  if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < birth.getDate())) {
    age -= 1;
  }

  return age >= 18;
}

function adultMaxDate() {
  const today = new Date();
  const max = new Date(today.getFullYear() - 18, today.getMonth(), today.getDate());
  return max.toISOString().slice(0, 10);
}
