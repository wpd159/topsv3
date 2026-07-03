import "./globals.css";

import type { Metadata, Viewport } from "next";

export const metadata = {
  title: "Tops do Job",
  description: "Previa local do Tops do Job"
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
      <body>{children}</body>
    </html>
  );
}
