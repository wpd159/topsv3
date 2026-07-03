import "./globals.css";

export const metadata = {
  title: "Tops do Job",
  description: "Previa local do Tops do Job"
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
