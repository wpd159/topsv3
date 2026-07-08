"use client";

import { useEffect, useMemo, useState } from "react";
import Image from "next/image";
import { useRouter } from "next/navigation";

import { loginAdmin } from "../../../../lib/api/adminAuthApi";
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
import { RecuperarSenhaModal } from "./RecuperarSenhaModal";
import { ConfirmarContaModal } from "./ConfirmarContaModal";

interface LoginModalProps {
  open: boolean;
  onOpenChange: (value: boolean) => void;
  onOpenRegister?: () => void;
  redirectAfterSuccess?: string | null;
}

export function LoginModal({ open, onOpenChange, onOpenRegister, redirectAfterSuccess }: LoginModalProps) {
  const router = useRouter();
  const [showPassword, setShowPassword] = useState(false);
  const [forgotPasswordModalOpen, setForgotPasswordModalOpen] = useState(false);
  const [email, setEmail] = useState("");
  const [senha, setSenha] = useState("");
  const [loading, setLoading] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [pendingEmail, setPendingEmail] = useState("");
  const [show2FA, setShow2FA] = useState(false);
  const [codigo2FA, setCodigo2FA] = useState("");
  const [queryRedirectTarget, setQueryRedirectTarget] = useState<string | null>(null);
  const [message, setMessage] = useState("");

  useEffect(() => {
    if (redirectAfterSuccess) {
      setQueryRedirectTarget(null);
      return;
    }

    if (typeof window === "undefined") return;

    const candidate = new URLSearchParams(window.location.search).get("next");
    setQueryRedirectTarget(candidate && candidate.startsWith("/") ? candidate : null);
  }, [redirectAfterSuccess, open]);

  const redirectTarget = useMemo(() => {
    const candidate = redirectAfterSuccess || queryRedirectTarget;
    if (candidate && candidate.startsWith("/")) return candidate;
    return "/meus-anuncios";
  }, [queryRedirectTarget, redirectAfterSuccess]);

  useEffect(() => {
    if (!redirectTarget) return;

    const handleLoginSuccess = () => {
      router.push(redirectTarget);
    };

    window.addEventListener("tops:login-success", handleLoginSuccess);
    return () => window.removeEventListener("tops:login-success", handleLoginSuccess);
  }, [redirectTarget, router]);

  const handleOpenRegister = () => {
    onOpenChange(false);

    window.setTimeout(() => {
      if (onOpenRegister) {
        onOpenRegister();
        return;
      }
    }, 180);
  };

  const handleLogin = async () => {
    if (loading) return;

    if (!email || !senha) {
      setMessage("Preencha e-mail e senha");
      return;
    }

    try {
      setLoading(true);
      setMessage("");
      const response = await loginAdmin(email, senha);
      setSenha("");

      if (response.status === 206) {
        setShow2FA(true);
        setMessage("Digite o código do autenticador para continuar.");
        return;
      }

      if (!response.ok) {
        setMessage(response.message || "Credenciais inválidas");
        return;
      }

      if (typeof window !== "undefined") {
        window.dispatchEvent(new Event("tops:login-success"));
      }
      onOpenChange(false);
    } catch {
      setMessage("Falha na conexão com o servidor");
    } finally {
      setLoading(false);
    }
  };

  const handle2FAConfirm = async () => {
    if (loading) return;

    if (!codigo2FA.trim()) {
      setMessage("Digite o código 2FA");
      return;
    }

    setLoading(true);
    setMessage("Código 2FA indisponível neste ambiente.");
    setLoading(false);
  };

  const handleVerifiedThenLogin = async () => {
    setPendingEmail("");
    onOpenChange(false);
  };

  return (
    <>
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
              Entre na sua conta para continuar
            </DialogDescription>
          </DialogHeader>

          <form
            onSubmit={(event) => {
              event.preventDefault();
              void handleLogin();
            }}
            className="mt-4 space-y-4"
          >
            <div className="relative">
              <EnvelopeIcon className="absolute left-3 top-2.5 w-5 h-5 text-gray-400" />
              <Input
                type="email"
                placeholder="Seu e-mail"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                className="pl-10 py-5"
              />
            </div>

            <div className="relative">
              <LockClosedIcon className="absolute left-3 top-2.5 w-5 h-5 text-gray-400" />
              <Input
                type={showPassword ? "text" : "password"}
                placeholder="Senha"
                value={senha}
                onChange={(event) => setSenha(event.target.value)}
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

            <div className="flex justify-between items-center">
              <button
                type="button"
                onClick={() => setForgotPasswordModalOpen(true)}
                className="text-sm text-[#FC1EAD] cursor-pointer hover:underline"
              >
                Esqueci minha senha
              </button>

              <button type="button" onClick={handleOpenRegister} className="text-sm text-gray-500 hover:underline">
                Criar conta
              </button>
            </div>

            <Button
              type="submit"
              disabled={loading}
              className="w-full py-5 mt-2 bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-semibold"
            >
              {loading ? "Entrando..." : "Entrar"}
            </Button>
          </form>

          {message ? <p className="public-clone-message">{message}</p> : null}
        </DialogContent>
      </Dialog>

      <Dialog
        open={show2FA}
        onOpenChange={(value) => {
          setShow2FA(value);
          if (!value) setCodigo2FA("");
        }}
      >
        <DialogContent className="sm:max-w-md p-6 rounded-xl">
          <DialogHeader>
            <DialogTitle className="flex items-center justify-center gap-2">
              <KeyIcon className="w-5 h-5 text-[#FC1EAD]" />
              Verificação 2FA
            </DialogTitle>
            <DialogDescription className="text-center text-gray-600">
              Digite o código gerado pelo seu aplicativo autenticador.
            </DialogDescription>
          </DialogHeader>

          <form
            onSubmit={(event) => {
              event.preventDefault();
              void handle2FAConfirm();
            }}
            className="mt-4 space-y-4"
          >
            <Input
              placeholder="Código de 6 dígitos"
              value={codigo2FA}
              onChange={(event) => setCodigo2FA(event.target.value)}
              maxLength={6}
              className="text-center tracking-widest font-medium py-5 text-lg"
            />

            <div className="flex gap-2">
              <Button type="button" variant="outline" onClick={() => setShow2FA(false)} disabled={loading} className="flex-1">
                Cancelar
              </Button>

              <Button
                type="submit"
                disabled={loading}
                className="flex-1 bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-semibold"
              >
                {loading ? "Verificando..." : "Confirmar"}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      <RecuperarSenhaModal open={forgotPasswordModalOpen} onOpenChange={setForgotPasswordModalOpen} />

      <ConfirmarContaModal
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        email={pendingEmail}
        onVerified={handleVerifiedThenLogin}
      />
    </>
  );
}
