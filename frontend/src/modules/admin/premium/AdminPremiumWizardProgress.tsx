type AdminPremiumWizardProgressProps = {
  steps: readonly {
    id: string;
    label: string;
  }[];
  currentIndex: number;
};

export function AdminPremiumWizardProgress({ steps, currentIndex }: AdminPremiumWizardProgressProps) {
  return (
    <nav className="admin-premium-progress" aria-label="Progresso do preview Premium">
      <ol>
        {steps.map((step, index) => (
          <li key={step.id} aria-current={index === currentIndex ? "step" : undefined}>
            <span>{index + 1}</span>
            <strong>{step.label}</strong>
          </li>
        ))}
      </ol>
    </nav>
  );
}
