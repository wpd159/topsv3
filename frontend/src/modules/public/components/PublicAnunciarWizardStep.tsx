import type { ReactNode } from "react";

type PublicAnunciarWizardStepProps = {
  title: string;
  description: string;
  children: ReactNode;
};

export function PublicAnunciarWizardStep({ title, description, children }: PublicAnunciarWizardStepProps) {
  return (
    <section className="public-wizard-step" aria-label={title}>
      <div className="public-form-heading">
        <span className="status">Anuncie grátis</span>
        <h2>{title}</h2>
        <p>{description}</p>
      </div>
      <div className="public-wizard-step-body">{children}</div>
    </section>
  );
}
