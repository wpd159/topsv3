import "./globals.css";

import type { Metadata, Viewport } from "next";

import { PublicConsentGate } from "../modules/public/components/PublicConsentGate";
import { PublicSiteFooter } from "../modules/public/components/PublicSiteFooter";
import { PublicSiteHeader } from "../modules/public/components/PublicSiteHeader";

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
        <PublicSiteHeader />
        {children}
        <PublicSiteFooter />
        <PublicConsentGate />
      </body>
    </html>
  );
}
