import "./globals.css";

export const metadata = {
  title: "Tops do Job V3",
  description: "Skeleton local do Tops do Job V3"
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
