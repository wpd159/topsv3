"use client";

import { useState } from "react";
import Image from "next/image";

import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  EnvelopeIcon,
  EyeIcon,
  EyeSlashIcon,
  Input,
  KeyIcon,
  LockClosedIcon
} from "./PublicModalPrimitives";

interface RecuperarSenhaModalProps {
  open: boolean;
  onOpenChange: (value: boolean) => void;
}

export function RecuperarSenhaModal({ open, onOpenChange }: RecuperarSenhaModalProps) {
  const [step, setStep] = useState(1);
  const [loading, setLoading] = useState(false);
  const [email, setEmail] = useState("");
  const [codigo, setCodigo] = useState("");
  const [novaSenha, setNovaSenha] = useState("");
  const [confirmarSenha, setConfirmarSenha] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [message, setMessage] = useState("");

  const next = () => setStep((current) => current + 1);
  const back = () => setStep((current) => current - 1);

  function handleEnviarCodigo() {
    if (!email.trim()) {
      setMessage("Digite seu e-mail.");
      return;
    }
    setLoading(true);
    setMessage("Se o e-mail estiver cadastrado, enviaremos as instruções.");
    window.setTimeout(() => {
      setLoading(false);
      next();
    }, 250);
  }

  function handleValidarCodigo() {
    if (!codigo.trim()) {
      setMessage("Digite o código recebido por e-mail.");
      return;
    }
    setLoading(true);
    window.setTimeout(() => {
      setLoading(false);
      next();
    }, 250);
  }

  function handleRedefinirSenha() {
    if (!novaSenha || !confirmarSenha) {
      setMessage("Preencha todos os campos.");
      return;
    }
    if (novaSenha !== confirmarSenha) {
      setMessage("As senhas não coincidem.");
      return;
    }
    setMessage("Não foi possível redefinir a senha agora. Tente novamente mais tarde.");
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md p-6 rounded-xl">
        <DialogHeader>
          <DialogTitle className="flex justify-center mb-2">
            <Image
              src="/logo.webp"
              alt="Logo"
              width={150}
              height={50}
              priority
              fetchPriority="high"
              className="h-10 w-auto max-w-[180px] object-contain"
            />
          </DialogTitle>
          <DialogDescription className="text-center text-gray-600">
            {step === 1 && "Digite seu e-mail para recuperar sua senha"}
            {step === 2 && "Digite o código enviado para seu e-mail"}
            {step === 3 && "Redefina sua senha"}
          </DialogDescription>
        </DialogHeader>

        {step === 1 && (
          <div className="mt-4 space-y-5">
            <div className="relative">
              <EnvelopeIcon className="absolute left-3 top-3 w-5 h-5 text-gray-400" />
              <Input
                type="email"
                placeholder="Seu e-mail"
                className="pl-10 py-5"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                disabled={loading}
              />
            </div>

            <Button
              className="w-full py-5 bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-semibold"
              onClick={handleEnviarCodigo}
              disabled={loading}
            >
              {loading ? "Enviando..." : "Enviar código"}
            </Button>
          </div>
        )}

        {step === 2 && (
          <div className="mt-4 space-y-5">
            <div className="relative">
              <KeyIcon className="absolute left-3 top-3 w-5 h-5 text-gray-400" />
              <Input
                type="text"
                placeholder="Código de verificação"
                className="pl-10 py-5 tracking-widest text-center font-medium"
                maxLength={6}
                value={codigo}
                onChange={(event) => setCodigo(event.target.value)}
                disabled={loading}
              />
            </div>

            <div className="flex justify-between">
              <Button variant="outline" className="py-5" onClick={back} disabled={loading}>
                Voltar
              </Button>

              <Button
                className="py-5 bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-semibold"
                onClick={handleValidarCodigo}
                disabled={loading}
              >
                {loading ? "Verificando..." : "Verificar código"}
              </Button>
            </div>
          </div>
        )}

        {step === 3 && (
          <div className="mt-4 space-y-5">
            <div className="relative">
              <LockClosedIcon className="absolute left-3 top-3 w-5 h-5 text-gray-400" />
              <Input
                type={showPassword ? "text" : "password"}
                placeholder="Nova senha"
                className="pl-10 pr-10 py-5"
                value={novaSenha}
                onChange={(event) => setNovaSenha(event.target.value)}
                disabled={loading}
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute right-3 top-3 text-gray-400 hover:text-gray-600"
              >
                {showPassword ? <EyeSlashIcon className="w-5 h-5" /> : <EyeIcon className="w-5 h-5" />}
              </button>
            </div>

            <div className="relative">
              <LockClosedIcon className="absolute left-3 top-3 w-5 h-5 text-gray-400" />
              <Input
                type={showConfirmPassword ? "text" : "password"}
                placeholder="Confirmar nova senha"
                className="pl-10 pr-10 py-5"
                value={confirmarSenha}
                onChange={(event) => setConfirmarSenha(event.target.value)}
                disabled={loading}
              />
              <button
                type="button"
                onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                className="absolute right-3 top-3 text-gray-400 hover:text-gray-600"
              >
                {showConfirmPassword ? <EyeSlashIcon className="w-5 h-5" /> : <EyeIcon className="w-5 h-5" />}
              </button>
            </div>

            <Button
              className="w-full py-5 bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-semibold"
              onClick={handleRedefinirSenha}
              disabled={loading}
            >
              {loading ? "Salvando..." : "Redefinir senha"}
            </Button>
          </div>
        )}

        {message ? <p className="public-clone-message">{message}</p> : null}
      </DialogContent>
    </Dialog>
  );
}
