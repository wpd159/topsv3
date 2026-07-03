"use client";

import { useMemo, useState } from "react";

import { AdminPremiumWizardProgress } from "./AdminPremiumWizardProgress";
import { AdminPremiumWizardStep } from "./AdminPremiumWizardStep";

type PremiumStepId = "intro" | "anuncio" | "beneficios" | "periodo" | "revisao" | "resultado";

type PremiumStep = {
  id: PremiumStepId;
  label: string;
};

type PreviewAd = {
  id: string;
  title: string;
  location: string;
  status: string;
};

type PreviewBenefit = {
  code: string;
  label: string;
  description: string;
};

type PreviewPeriod = {
  days: number;
  label: string;
};

const STEPS: readonly PremiumStep[] = [
  { id: "intro", label: "Inicio" },
  { id: "anuncio", label: "Anuncio" },
  { id: "beneficios", label: "Beneficios" },
  { id: "periodo", label: "Periodo" },
  { id: "revisao", label: "Revisao" },
  { id: "resultado", label: "Preview" }
];

const ADS: readonly PreviewAd[] = [
  {
    id: "preview-anuncio-1",
    title: "Anuncio de exemplo A",
    location: "Cidade Sintetica - ZZ",
    status: "PENDENTE_REVISAO"
  },
  {
    id: "preview-anuncio-2",
    title: "Anuncio de exemplo B",
    location: "Bairro Sintetico, Cidade Sintetica",
    status: "PUBLICADO_LOCAL"
  }
];

const BENEFITS: readonly PreviewBenefit[] = [
  {
    code: "ANUNCIO_TOPO",
    label: "Topo da lista",
    description: "Simula maior exposicao na listagem, sem alterar ranking real."
  },
  {
    code: "WHATSAPP_CARD",
    label: "WhatsApp destacado",
    description: "Mostra como o contato poderia ficar mais visivel quando liberado pelo backend."
  },
  {
    code: "FOTOS_EXTRA_5",
    label: "Mais fotos",
    description: "Reserva a ideia de limite maior de midia para fase futura segura."
  },
  {
    code: "RELATORIO_DESEMPENHO",
    label: "Relatorio de desempenho",
    description: "Relaciona Premium com metricas agregadas, sem promessa de resultado."
  }
];

const PERIODS: readonly PreviewPeriod[] = [
  { days: 7, label: "7 dias" },
  { days: 15, label: "15 dias" },
  { days: 30, label: "30 dias" }
];

export function AdminPremiumWizard() {
  const [currentStepIndex, setCurrentStepIndex] = useState(0);
  const [selectedAdId, setSelectedAdId] = useState(ADS[0].id);
  const [selectedBenefitCodes, setSelectedBenefitCodes] = useState<string[]>(["ANUNCIO_TOPO"]);
  const [selectedDays, setSelectedDays] = useState(PERIODS[1].days);
  const [message, setMessage] = useState("Preview local sem pagamento, credito ou ativacao real.");

  const currentStep = STEPS[currentStepIndex];
  const selectedAd = ADS.find((item) => item.id === selectedAdId) ?? ADS[0];
  const selectedBenefits = useMemo(
    () => BENEFITS.filter((item) => selectedBenefitCodes.includes(item.code)),
    [selectedBenefitCodes]
  );
  const selectedPeriod = PERIODS.find((item) => item.days === selectedDays) ?? PERIODS[1];

  function next() {
    const validation = validateStep(currentStep.id);
    if (validation) {
      setMessage(validation);
      return;
    }
    setMessage("Etapa concluida.");
    setCurrentStepIndex((index) => Math.min(index + 1, STEPS.length - 1));
  }

  function back() {
    setMessage("Edite a etapa anterior.");
    setCurrentStepIndex((index) => Math.max(index - 1, 0));
  }

  function restart() {
    setSelectedAdId(ADS[0].id);
    setSelectedBenefitCodes(["ANUNCIO_TOPO"]);
    setSelectedDays(PERIODS[1].days);
    setCurrentStepIndex(0);
    setMessage("Preview local sem pagamento, credito ou ativacao real.");
  }

  function toggleBenefit(code: string) {
    setSelectedBenefitCodes((current) =>
      current.includes(code) ? current.filter((item) => item !== code) : [...current, code]
    );
  }

  return (
    <section id="admin-premium-wizard" className="admin-panel admin-premium-wizard" aria-label="Wizard Premium local">
      <div className="admin-premium-heading">
        <h2>Wizard Premium local</h2>
        <p>Preview administrativo sem compra, Pix, Efi, credito real ou ativacao por dinheiro.</p>
      </div>
      <AdminPremiumWizardProgress steps={STEPS} currentIndex={currentStepIndex} />
      {renderStep()}
      <div className="admin-premium-actions">
        {currentStep.id !== "intro" && currentStep.id !== "resultado" ? (
          <button className="local-secondary-action" type="button" onClick={back}>
            Voltar
          </button>
        ) : null}
        {currentStep.id === "resultado" ? (
          <button className="local-action" type="button" onClick={restart}>
            Reiniciar preview
          </button>
        ) : (
          <button className="local-action" type="button" onClick={next}>
            Continuar
          </button>
        )}
        <span aria-live="polite">{message}</span>
      </div>
    </section>
  );

  function renderStep() {
    switch (currentStep.id) {
      case "intro":
        return (
          <AdminPremiumWizardStep
            title="Preparar preview"
            description="Use este fluxo para simular uma decisao Premium futura sem tocar em financeiro."
          >
            <div className="admin-notice">
              Nenhum pagamento e criado. Nenhum beneficio e ativado. Nenhum credito, Pix, Efi, checkout ou webhook e chamado.
            </div>
          </AdminPremiumWizardStep>
        );
      case "anuncio":
        return (
          <AdminPremiumWizardStep
            title="Escolha um anuncio de exemplo"
            description="A lista e fixa e serve apenas para visualizar combinacoes de beneficios."
          >
            <fieldset className="admin-premium-choice-group">
              <legend>Anuncios</legend>
              {ADS.map((item) => (
                <label key={item.id}>
                  <input
                    type="radio"
                    name="admin-premium-ad"
                    checked={selectedAdId === item.id}
                    onChange={() => setSelectedAdId(item.id)}
                  />
                  <span>
                    <strong>{item.title}</strong>
                    <small>{`${item.location} / ${item.status}`}</small>
                  </span>
                </label>
              ))}
            </fieldset>
          </AdminPremiumWizardStep>
        );
      case "beneficios":
        return (
          <AdminPremiumWizardStep
            title="Escolha os beneficios"
            description="Selecione beneficios para compor a previa. Eles nao serao gravados."
          >
            <fieldset className="admin-premium-choice-group">
              <legend>Beneficios</legend>
              {BENEFITS.map((item) => (
                <label key={item.code}>
                  <input
                    type="checkbox"
                    checked={selectedBenefitCodes.includes(item.code)}
                    onChange={() => toggleBenefit(item.code)}
                  />
                  <span>
                    <strong>{item.label}</strong>
                    <small>{item.description}</small>
                  </span>
                </label>
              ))}
            </fieldset>
          </AdminPremiumWizardStep>
        );
      case "periodo":
        return (
          <AdminPremiumWizardStep
            title="Escolha o periodo"
            description="Os periodos sao apenas parametros de preview, sem valor comercial definitivo."
          >
            <fieldset className="admin-premium-choice-group compact-choice">
              <legend>Periodo</legend>
              {PERIODS.map((item) => (
                <label key={item.days}>
                  <input
                    type="radio"
                    name="admin-premium-period"
                    checked={selectedDays === item.days}
                    onChange={() => setSelectedDays(item.days)}
                  />
                  <span>
                    <strong>{item.label}</strong>
                    <small>Sem cobranca nesta etapa</small>
                  </span>
                </label>
              ))}
            </fieldset>
          </AdminPremiumWizardStep>
        );
      case "revisao":
        return (
          <AdminPremiumWizardStep
            title="Revisao da previa"
            description="Confira a combinacao antes de gerar o resultado local."
          >
            <dl className="public-submit-summary">
              <div>
                <dt>Anuncio</dt>
                <dd>{selectedAd.title}</dd>
              </div>
              <div>
                <dt>Beneficios</dt>
                <dd>{selectedBenefits.map((item) => item.label).join(", ") || "Nenhum"}</dd>
              </div>
              <div>
                <dt>Periodo</dt>
                <dd>{selectedPeriod.label}</dd>
              </div>
              <div>
                <dt>Efeito real</dt>
                <dd>nenhum</dd>
              </div>
            </dl>
          </AdminPremiumWizardStep>
        );
      case "resultado":
        return (
          <AdminPremiumWizardStep
            title="Resultado local"
            description="A previa foi montada em memoria e nao alterou nenhum registro."
          >
            <div className="admin-readonly-list">
              <h3>{selectedAd.title}</h3>
              <ul>
                <li>
                  <span>Periodo</span>
                  <small>{selectedPeriod.label}</small>
                </li>
                <li>
                  <span>Beneficios previstos</span>
                  <small>{selectedBenefits.map((item) => item.code).join(", ") || "nenhum"}</small>
                </li>
                <li>
                  <span>Financeiro</span>
                  <small>sem pagamento, credito, Pix, Efi, checkout ou webhook</small>
                </li>
              </ul>
            </div>
          </AdminPremiumWizardStep>
        );
      default:
        return null;
    }
  }

  function validateStep(step: PremiumStepId): string | null {
    if (step === "anuncio" && !selectedAdId) {
      return "Selecione um anuncio de exemplo.";
    }
    if (step === "beneficios" && selectedBenefitCodes.length === 0) {
      return "Selecione pelo menos um beneficio para visualizar.";
    }
    if (step === "periodo" && !selectedDays) {
      return "Selecione um periodo.";
    }
    return null;
  }
}
