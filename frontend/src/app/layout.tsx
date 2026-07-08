import "./globals.css";

import type { Metadata, Viewport } from "next";

import { PublicConsentGate } from "../modules/public/components/PublicConsentGate";

export const metadata = {
  title: "Tops do Job",
  description: "Tops do Job",
  icons: {
    icon: "/favicon.ico",
    shortcut: "/favicon.ico"
  }
} satisfies Metadata;

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1
};

export default function RootLayout({
  children
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="pt-BR">
      <body>
        {children}
        <PublicConsentGate />
      </body>
    </html>
  );
}
