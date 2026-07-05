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
          return (
            <li key={step.id} aria-current={index === currentIndex ? "step" : undefined}>
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
