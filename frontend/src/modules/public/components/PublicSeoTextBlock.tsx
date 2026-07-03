type PublicSeoTextBlockProps = {
  routePath: string;
  children?: React.ReactNode;
};

export function PublicSeoTextBlock({ routePath, children }: PublicSeoTextBlockProps) {
  return (
    <section className="public-seo-block" aria-label="Texto SEO local">
      <h2>Informacao local</h2>
      {children ?? (
        <p>
          Conteudo textual reservado para a rota {routePath}. A V3 local preserva o caminho publico,
          mas nao publica SEO final nem dados reais nesta etapa.
        </p>
      )}
    </section>
  );
}
