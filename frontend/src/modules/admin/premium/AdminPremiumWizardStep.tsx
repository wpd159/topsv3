import type { ReactNode } from "react";

type AdminPremiumWizardStepProps = {
  title: string;
  description: string;
  children: ReactNode;
};

export function AdminPremiumWizardStep({ title, description, children }: AdminPremiumWizardStepProps) {
  return (
    <section className="admin-premium-step" aria-label={title}>
      <div>
        <h3>{title}</h3>
        <p>{description}</p>
      </div>
      <div className="admin-premium-step-body">{children}</div>
    </section>
  );
}
