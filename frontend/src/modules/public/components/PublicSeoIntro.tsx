type PublicSeoIntroProps = {
  title: string;
  children: React.ReactNode;
};

export function PublicSeoIntro({ title, children }: PublicSeoIntroProps) {
  return (
    <section className="public-seo-intro" aria-labelledby="public-seo-intro-title">
      <h2 id="public-seo-intro-title">{title}</h2>
      <div className="public-seo-intro-copy">{children}</div>
    </section>
  );
}
