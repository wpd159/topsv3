"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";

import { LoginModal } from "./clone/LoginModal";
import { RegisterModal } from "./clone/RegisterModal";

export function PublicSiteFooter() {
  const pathname = usePathname();
  const [showScrollTop, setShowScrollTop] = useState(false);
  const [loginOpen, setLoginOpen] = useState(false);
  const [registerOpen, setRegisterOpen] = useState(false);
  const [nextPath, setNextPath] = useState<string | null>(null);

  useEffect(() => {
    const handleScroll = () => setShowScrollTop(window.scrollY > 400);
    window.addEventListener("scroll", handleScroll);
    handleScroll();
    return () => window.removeEventListener("scroll", handleScroll);
  }, []);

  function openPublishLogin() {
    setNextPath("/anunciar");
    setLoginOpen(true);
  }

  if (pathname?.startsWith("/admin")) {
    return null;
  }

  return (
    <footer className="public-site-footer" aria-label="Rodapé público">
      <div className="public-site-footer-inner">
        <section className="public-footer-about" aria-label="Sobre o Tops do Job">
          <Image
            src="/logo.webp"
            alt="Tops do Job"
            width={170}
            height={60}
            className="public-footer-logo"
          />
          <p>
            O Tops do Job é uma plataforma digital de classificados online destinada à publicação e
            divulgação de anúncios, conectando usuários por meio de um ambiente virtual de livre
            acesso.
          </p>
          <div className="public-footer-trust" aria-label="Confiança e segurança">
            <strong>Confiança e segurança</strong>
            <span>Contato direto com os anunciantes</span>
            <span>Privacidade e discrição na navegação</span>
            <span>Anúncios com moderação contínua</span>
            <span>Busca simples, rápida e segura</span>
          </div>
        </section>

        <nav className="public-footer-links" aria-label="Legal">
          <h2>LEGAL</h2>
          <Link href="/termos-de-uso">Termos da plataforma</Link>
          <Link href="/politica-de-privacidade">Política de Privacidade</Link>
          <Link href="/cookies">Política de Cookies</Link>
          <Link href="/politicas/verificacao-etaria">Verificação Etária</Link>
          <Link href="/aviso-seguranca-whatsapp">Aviso de Segurança no WhatsApp</Link>
        </nav>

        <nav className="public-footer-links" aria-label="Navegação">
          <h2>NAVEGAÇÃO</h2>
          <Link href="/sobre">Sobre o Tops do Job</Link>
          <Link href="/faq">Fale Conosco</Link>
          <Link href="/faq">Central de ajuda</Link>
          <Link href="/como-funciona">Blog</Link>
        </nav>

        <nav className="public-footer-links" aria-label="Cidades populares">
          <h2>CIDADES POPULARES</h2>
          <Link href="/acompanhantes/go/goiania">Acompanhantes em Goiânia</Link>
          <Link href="/acompanhantes/go/goiania">Acompanhantes em Brasília</Link>
          <Link href="/acompanhantes/go/goiania">Acompanhantes em Aparecida de Goiânia</Link>
          <Link href="/acompanhantes/go/goiania">Acompanhantes em São Paulo</Link>
          <Link href="/acompanhantes/go/goiania">Acompanhantes no Rio de Janeiro</Link>
        </nav>
      </div>

      <div className="public-footer-cta-row">
        <div>
          <h2>Aumente sua visibilidade e apareça para mais clientes</h2>
          <p>
            Destaque seu perfil nas listagens, receba mais contatos qualificados e fortaleça sua
            presença em uma plataforma com navegação simples, moderação ativa e atualização diária.
          </p>
        </div>
        <button type="button" onClick={openPublishLogin}>
          PUBLICAR SEU ANÚNCIO
        </button>
      </div>

      <div className="public-footer-copy">
        <span>Copyright © {new Date().getFullYear()} Tops do Job. Todos os direitos reservados.</span>
      </div>
      {showScrollTop ? (
        <button
          className="public-scroll-top"
          type="button"
          onClick={() => window.scrollTo({ top: 0, behavior: "smooth" })}
        >
          ↑ Voltar ao Início
        </button>
      ) : null}
      <LoginModal
        open={loginOpen}
        onOpenChange={setLoginOpen}
        onOpenRegister={() => setRegisterOpen(true)}
        redirectAfterSuccess={nextPath}
      />
      <RegisterModal
        open={registerOpen}
        onOpenChange={setRegisterOpen}
        onBackToLogin={() => window.setTimeout(() => setLoginOpen(true), 220)}
      />
    </footer>
  );
}
