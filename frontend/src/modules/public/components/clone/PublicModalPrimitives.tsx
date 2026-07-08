"use client";

import { ButtonHTMLAttributes, createContext, InputHTMLAttributes, ReactNode, useContext } from "react";

const DialogCloseContext = createContext<(() => void) | null>(null);

export function Dialog({
  open,
  onOpenChange,
  children
}: {
  open: boolean;
  onOpenChange: (value: boolean) => void;
  children: ReactNode;
}) {
  if (!open) return null;

  return (
    <DialogCloseContext.Provider value={() => onOpenChange(false)}>
      <div className="public-dialog-root" role="presentation" onMouseDown={() => onOpenChange(false)}>
        {children}
      </div>
    </DialogCloseContext.Provider>
  );
}

export function DialogContent({
  className = "",
  children,
  showCloseButton = true
}: {
  className?: string;
  children: ReactNode;
  showCloseButton?: boolean;
}) {
  const closeDialog = useContext(DialogCloseContext);

  return (
    <section
      className={`public-dialog-content ${className}`}
      role="dialog"
      aria-modal="true"
      onMouseDown={(event) => event.stopPropagation()}
    >
      {showCloseButton ? (
        <button
          type="button"
          className="public-dialog-close"
          aria-label="Fechar"
          onClick={() => closeDialog?.()}
        >
          <XIcon />
          <span className="sr-only">Fechar</span>
        </button>
      ) : null}
      {children}
    </section>
  );
}

export function DialogHeader({ className = "", children }: { className?: string; children: ReactNode }) {
  return <div className={`public-dialog-header ${className}`}>{children}</div>;
}

export function DialogTitle({ className = "", children }: { className?: string; children: ReactNode }) {
  return <h2 className={`public-dialog-title ${className}`}>{children}</h2>;
}

export function DialogDescription({ className = "", children }: { className?: string; children: ReactNode }) {
  return <p className={`public-dialog-description ${className}`}>{children}</p>;
}

export function Button({
  className = "",
  variant,
  children,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "default" | "outline" | "ghost" | "secondary";
}) {
  return (
    <button className={`public-clone-button ${variant ? `public-clone-button-${variant}` : ""} ${className}`} {...props}>
      {children}
    </button>
  );
}

export function Input({ className = "", ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return <input className={`public-clone-input ${className}`} {...props} />;
}

type IconProps = {
  className?: string;
};

function BaseIcon({
  className = "",
  children
}: IconProps & {
  children: ReactNode;
}) {
  return (
    <svg className={`public-clone-icon ${className}`} viewBox="0 0 24 24" fill="none" aria-hidden="true">
      {children}
    </svg>
  );
}

export function EnvelopeIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="M4 6.75h16v10.5H4V6.75Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
      <path d="m5 8 7 5 7-5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </BaseIcon>
  );
}

export function LockClosedIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="M7 10h10v9H7v-9Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
      <path d="M9 10V7a3 3 0 0 1 6 0v3" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </BaseIcon>
  );
}

export function EyeIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="M3 12s3.4-5 9-5 9 5 9 5-3.4 5-9 5-9-5-9-5Z" stroke="currentColor" strokeWidth="1.8" />
      <path d="M12 14.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Z" stroke="currentColor" strokeWidth="1.8" />
    </BaseIcon>
  );
}

export function EyeSlashIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="m4 4 16 16" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
      <path d="M6.2 6.8C4.2 8.2 3 10 3 10s3.4 5 9 5c1.5 0 2.9-.4 4.1-.9" stroke="currentColor" strokeWidth="1.8" />
      <path d="M9.7 5.2c.7-.1 1.5-.2 2.3-.2 5.6 0 9 5 9 5s-.8 1.2-2.2 2.4" stroke="currentColor" strokeWidth="1.8" />
    </BaseIcon>
  );
}

export function KeyIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="M14.5 9.5a4 4 0 1 0-3.1 3.9L13 15h2v2h2v2h3v-3.1l-5.5-5.5Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
      <path d="M7.5 9.5h.01" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
    </BaseIcon>
  );
}

export function UserIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z" stroke="currentColor" strokeWidth="1.8" />
      <path d="M4.5 20a7.5 7.5 0 0 1 15 0" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </BaseIcon>
  );
}

export function PhoneIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="M7 4h3l1.2 4-2 1.2a11 11 0 0 0 5.6 5.6l1.2-2 4 1.2v3a3 3 0 0 1-3.3 3A15 15 0 0 1 4 7.3 3 3 0 0 1 7 4Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
    </BaseIcon>
  );
}

export function CalendarDaysIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="M7 3v3M17 3v3M4 8h16M5 5h14v15H5V5Z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </BaseIcon>
  );
}

export function ExclamationTriangleIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="M12 4 3 20h18L12 4Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
      <path d="M12 9v5M12 17h.01" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </BaseIcon>
  );
}

export function XIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="M6 6l12 12M18 6 6 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </BaseIcon>
  );
}
