"use client";

import { useState } from "react";

import {
  Button,
  CalendarDaysIcon,
  EnvelopeIcon,
  ExclamationTriangleIcon,
  EyeIcon,
  EyeSlashIcon,
  Input,
  LockClosedIcon,
  PhoneIcon,
  UserIcon
} from "./PublicModalPrimitives";

interface RegisterFormProps {
  onSuccess?: () => void;
  onBackToLogin?: () => void;
}

function isValidDateInput(value: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const parsed = new Date(`${value}T00:00:00`);
  if (Number.isNaN(parsed.getTime())) return false;
  return parsed.toISOString().slice(0, 10) === value;
}

function isAtLeast18(value: string) {
  if (!isValidDateInput(value)) return false;

  const birth = new Date(`${value}T00:00:00`);
  const today = new Date();
  let age = today.getFullYear() - birth.getFullYear();
  const monthDiff = today.getMonth() - birth.getMonth();

  if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < birth.getDate())) {
    age -= 1;
  }

  return age >= 18;
}

function getAdultMaxDate() {
  const today = new Date();
  const max = new Date(today.getFullYear() - 18, today.getMonth(), today.getDate());
  return max.toISOString().slice(0, 10);
}

function validateEmail(value: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
}

function formatPhone(value: string) {
  const digits = value.replace(/\D/g, "").slice(0, 11);
  if (digits.length <= 2) return digits;
  if (digits.length <= 7) return `(${digits.slice(0, 2)}) ${digits.slice(2)}`;
  return `(${digits.slice(0, 2)}) ${digits.slice(2, 7)}-${digits.slice(7)}`;
}

export function RegisterForm({ onBackToLogin }: RegisterFormProps) {
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [dataNascimento, setDataNascimento] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [terms, setTerms] = useState({ uso: false, privacidade: false, promo: true });
  const [message, setMessage] = useState("");

  const emailValid = !email || validateEmail(email);
  const dataNascimentoPreenchida = dataNascimento.trim().length > 0;
  const dataNascimentoValida = !dataNascimentoPreenchida || isValidDateInput(dataNascimento);
  const maioridadeOk = !dataNascimentoPreenchida || isAtLeast18(dataNascimento);
  const dataNascimentoComErro = dataNascimentoPreenchida && (!dataNascimentoValida || !maioridadeOk);
  const dataNascimentoMax = getAdultMaxDate();
  const passwordMismatch = confirmPassword.length > 0 && password !== confirmPassword;

  const allFieldsValid =
    username.trim().length >= 3 &&
    phone.replace(/\D/g, "").length >= 10 &&
    validateEmail(email) &&
    dataNascimentoPreenchida &&
    dataNascimentoValida &&
    maioridadeOk &&
    password.length >= 8 &&
    Object.is(password, confirmPassword) &&
    terms.uso &&
    terms.privacidade;

  const buttonLabel = allFieldsValid ? "Criar conta" : "Preencha os dados para continuar";

  function handleRegister() {
    if (!allFieldsValid) {
      setMessage("Revise os dados obrigatórios para continuar.");
      return;
    }

    setMessage("Não foi possível concluir o cadastro agora. Tente novamente mais tarde.");
  }

  return (
    <div className="space-y-5">
      <div className="relative">
        <UserIcon className="absolute left-3 top-2.5 h-5 w-5 text-gray-400" />
        <Input
          type="text"
          placeholder="Nome de usuário"
          value={username}
          onChange={(event) => setUsername(event.target.value)}
          className="pl-10 py-5"
        />
      </div>

      <div className="relative">
        <CalendarDaysIcon className="absolute left-3 top-2.5 h-5 w-5 text-gray-400" />
        <Input
          type="date"
          value={dataNascimento}
          onChange={(event) => setDataNascimento(event.target.value)}
          max={dataNascimentoMax}
          className={`register-date-input h-9 min-h-9 pl-10 py-1 leading-5 ${dataNascimentoComErro ? "border-red-400" : ""}`}
        />
        {dataNascimentoPreenchida && !dataNascimentoValida && (
          <p className="mt-1 flex items-center gap-1 text-xs text-red-600">
            <ExclamationTriangleIcon className="h-4 w-4" /> Data de nascimento inválida.
          </p>
        )}
        {dataNascimentoPreenchida && dataNascimentoValida && !maioridadeOk && (
          <p className="mt-1 flex items-center gap-1 text-xs text-red-600">
            <ExclamationTriangleIcon className="h-4 w-4" /> Cadastro permitido apenas para maiores de 18 anos.
          </p>
        )}
      </div>

      <div className="relative">
        <PhoneIcon className="absolute left-3 top-2.5 w-5 h-5 text-gray-400" />
        <Input
          type="tel"
          placeholder="Telefone"
          value={phone}
          onChange={(event) => setPhone(formatPhone(event.target.value))}
          maxLength={15}
          className="pl-10 py-5"
        />
      </div>

      <div className="relative">
        <EnvelopeIcon className="absolute left-3 top-2.5 w-5 h-5 text-gray-400" />
        <Input
          type="email"
          placeholder="Seu e-mail"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          className={`pl-10 py-5 ${email && !emailValid ? "border-red-400" : ""}`}
        />
        {email && !emailValid && (
          <p className="mt-1 text-xs text-red-600 flex items-center gap-1">
            <ExclamationTriangleIcon className="w-4 h-4" /> E-mail inválido.
          </p>
        )}
      </div>

      <div className="relative">
        <LockClosedIcon className="absolute left-3 top-2.5 w-5 h-5 text-gray-400" />
        <Input
          type={showPassword ? "text" : "password"}
          placeholder="Senha"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          className="pl-10 pr-10 py-5"
        />
        <button
          type="button"
          onClick={() => setShowPassword(!showPassword)}
          className="absolute right-3 top-3 text-gray-400 hover:text-gray-600"
        >
          {showPassword ? <EyeSlashIcon className="w-5 h-5" /> : <EyeIcon className="w-5 h-5" />}
        </button>
      </div>

      <div className="relative mt-2">
        <LockClosedIcon className="absolute left-3 top-2.5 w-5 h-5 text-gray-400" />
        <Input
          type={showConfirmPassword ? "text" : "password"}
          placeholder="Confirmar senha"
          value={confirmPassword}
          onChange={(event) => setConfirmPassword(event.target.value)}
          className="pl-10 pr-10 py-5"
        />
        <button
          type="button"
          onClick={() => setShowConfirmPassword(!showConfirmPassword)}
          className="absolute right-3 top-3 text-gray-400 hover:text-gray-600"
        >
          {showConfirmPassword ? <EyeSlashIcon className="w-5 h-5" /> : <EyeIcon className="w-5 h-5" />}
        </button>
        {passwordMismatch && (
          <p className="mt-1 text-xs text-red-600 flex items-center gap-1">
            <ExclamationTriangleIcon className="w-4 h-4" /> As senhas não coincidem.
          </p>
        )}
      </div>

      <div className="space-y-2 mt-4 text-xs">
        <label className="flex items-center gap-1">
          <input checked={terms.uso} onChange={(event) => setTerms((current) => ({ ...current, uso: event.target.checked }))} type="checkbox" />
          Estou de acordo com os
          <a href="/termos-de-uso" target="_blank" rel="noreferrer" className="text-[#FC1EAD] font-semibold underline cursor-pointer">
            {" "}Termos de Uso
          </a>
        </label>
        <label className="flex items-center gap-1">
          <input
            checked={terms.privacidade}
            onChange={(event) => setTerms((current) => ({ ...current, privacidade: event.target.checked }))}
            type="checkbox"
          />
          Estou de acordo com as
          <a href="/politica-de-privacidade" target="_blank" rel="noreferrer" className="text-[#FC1EAD] font-semibold underline cursor-pointer">
            {" "}Políticas de Privacidade
          </a>
        </label>
        <label className="flex items-center gap-2">
          <input checked={terms.promo} onChange={(event) => setTerms((current) => ({ ...current, promo: event.target.checked }))} type="checkbox" />
          Receber e-mails promocionais.
        </label>
      </div>

      <Button
        onClick={handleRegister}
        disabled={!allFieldsValid}
        className={`w-full py-5 mt-6 font-semibold text-white ${
          allFieldsValid ? "bg-[#FC1EAD] hover:bg-[#e01a9a]" : "bg-gray-300 cursor-not-allowed"
        }`}
      >
        {buttonLabel}
      </Button>

      {onBackToLogin && (
        <p className="text-center text-sm text-gray-600 mt-4">
          Já tem uma conta?{" "}
          <button onClick={onBackToLogin} className="text-[#FC1EAD] font-medium hover:underline transition">
            Entrar
          </button>
        </p>
      )}

      {message ? <p className="public-clone-message">{message}</p> : null}
    </div>
  );
}
