"use client";

import { useState } from "react";

import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  EnvelopeIcon,
  Input,
  KeyIcon
} from "./PublicModalPrimitives";

type ConfirmarContaModalProps = {
  open: boolean;
  onOpenChange: (value: boolean) => void;
  email: string;
  onVerified?: () => Promise<void> | void;
};

export function ConfirmarContaModal({ open, onOpenChange, email, onVerified }: ConfirmarContaModalProps) {
  const [codigo, setCodigo] = useState("");
  const [loading, setLoading] = useState(false);
  const disabled = !email || codigo.trim().length < 6;

  async function handleConfirm() {
    if (disabled) return;
    setLoading(true);
    await onVerified?.();
    setLoading(false);
    onOpenChange(false);
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md p-6 rounded-xl">
        <DialogHeader>
          <DialogTitle>Confirmar conta</DialogTitle>
          <DialogDescription>Enviamos um código de 6 dígitos para o seu e-mail.</DialogDescription>
        </DialogHeader>

        <div className="mt-4 space-y-4">
          <div className="relative">
            <EnvelopeIcon className="absolute left-3 top-2.5 h-5 w-5 text-gray-400" />
            <Input value={email} disabled className="pl-10 py-5" />
          </div>

          <div className="relative">
            <KeyIcon className="absolute left-3 top-2.5 h-5 w-5 text-gray-400" />
            <Input
              inputMode="numeric"
              maxLength={6}
              placeholder="Código de verificação (6 dígitos)"
              value={codigo}
              onChange={(event) => setCodigo(event.target.value.replace(/\D/g, "").slice(0, 6))}
              className="pl-10 py-5 tracking-widest"
              onKeyDown={(event) => {
                if (event.key === "Enter") void handleConfirm();
              }}
            />
          </div>
        </div>

        <Button
          onClick={() => void handleConfirm()}
          disabled={disabled || loading}
          className={`w-full py-5 mt-6 font-semibold text-white ${
            !disabled ? "bg-[#FC1EAD] hover:bg-[#e01a9a]" : "bg-gray-300 cursor-not-allowed"
          }`}
        >
          {loading ? "Confirmando..." : "Confirmar"}
        </Button>
      </DialogContent>
    </Dialog>
  );
}
