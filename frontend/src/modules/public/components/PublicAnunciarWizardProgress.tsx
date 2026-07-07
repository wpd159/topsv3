type PublicAnunciarWizardProgressProps = {
  steps: readonly {
    id: string;
    label: string;
  }[];
  currentIndex: number;
};

export function PublicAnunciarWizardProgress({ steps, currentIndex }: PublicAnunciarWizardProgressProps) {
  return (
    <nav className="public-wizard-progress" aria-label="Progresso do cadastro">
      <ol>
        {steps.map((step, index) => {
          const status = index < currentIndex ? "Concluída" : index === currentIndex ? "Atual" : "Pendente";
          const state = index < currentIndex ? "complete" : index === currentIndex ? "current" : "pending";
          return (
            <li key={step.id} data-state={state} aria-current={index === currentIndex ? "step" : undefined}>
              <span>{index + 1}</span>
              <strong>{step.label}</strong>
              <small>{status}</small>
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
