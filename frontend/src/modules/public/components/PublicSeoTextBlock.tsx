type PublicSeoTextBlockProps = {
  routePath: string;
  children?: React.ReactNode;
};

export function PublicSeoTextBlock({ children }: PublicSeoTextBlockProps) {
  return (
    <section className="public-seo-block" aria-label="Conteúdo por cidade e bairro">
      <h2>Encontre por cidade, bairro e anúncio</h2>
      {children ?? (
        <p>
          Use a navegação pública para chegar a perfis por região, consultar anúncios disponíveis e
          enviar um cadastro para análise.
        </p>
      )}
    </section>
  );
}
