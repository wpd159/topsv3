import Image from "next/image";
import Link from "next/link";

export function PublicSiteFooter() {
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
          <Link href="/seguranca">Termos da plataforma</Link>
          <Link href="/seguranca">Política de Privacidade</Link>
          <Link href="/seguranca">Política de Cookies</Link>
          <Link href="/seguranca">Verificação Etária</Link>
          <Link href="/seguranca">Aviso de Segurança no WhatsApp</Link>
        </nav>

        <nav className="public-footer-links" aria-label="Navegação">
          <h2>NAVEGAÇÃO</h2>
          <Link href="/sobre">Sobre o Tops do Job</Link>
          <Link href="/perguntas-frequentes">Fale Conosco</Link>
          <Link href="/perguntas-frequentes">Central de ajuda</Link>
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
        <Link href="/anunciar">PUBLICAR SEU ANÚNCIO</Link>
      </div>

      <div className="public-footer-copy">
        <span>Copyright © {new Date().getFullYear()} Tops do Job. Todos os direitos reservados.</span>
      </div>
    </footer>
  );
}
