"use client";

import { Dialog, DialogContent } from "./PublicModalPrimitives";
import { RegisterForm } from "./RegisterForm";

interface RegisterModalProps {
  open: boolean;
  onOpenChange: (value: boolean) => void;
  onBackToLogin?: () => void;
  refId?: number | null;
}

export function RegisterModal({ open, onOpenChange, onBackToLogin }: RegisterModalProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg p-6 rounded-xl">
        <RegisterForm
          onSuccess={() => {
            onOpenChange(false);
            onBackToLogin?.();
          }}
          onBackToLogin={() => {
            onOpenChange(false);
            onBackToLogin?.();
          }}
        />
      </DialogContent>
    </Dialog>
  );
}
