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
          Use a navegação para chegar a perfis por região, consultar anúncios disponíveis e publicar
          seu anúncio.
        </p>
      )}
    </section>
  );
}
