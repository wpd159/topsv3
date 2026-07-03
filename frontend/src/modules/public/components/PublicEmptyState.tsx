type PublicEmptyStateProps = {
  title: string;
  message: string;
};

export function PublicEmptyState({ title, message }: PublicEmptyStateProps) {
  return (
    <section className="public-empty-state" aria-label={title}>
      <strong>{title}</strong>
      <p>{message}</p>
    </section>
  );
}
