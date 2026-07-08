/** @type {import('next').NextConfig} */
const nextConfig = {
  devIndicators: false,
  poweredByHeader: false,
  reactStrictMode: true,
  async redirects() {
    return [
      {
        source: "/seguranca",
        destination: "/aviso-seguranca-whatsapp",
        permanent: true
      },
      {
        source: "/perguntas-frequentes",
        destination: "/faq",
        permanent: true
      }
    ];
  }
};

export default nextConfig;
