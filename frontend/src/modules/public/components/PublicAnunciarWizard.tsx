"use client";

import { useMemo, useState } from "react";

import { solicitarAnuncioPublico } from "../../../lib/api/publicApi";
import type {
  SolicitarAnuncioPublicoRequestDto,
  SolicitarAnuncioPublicoResponseDto,
  SolicitarAnuncioValidationErrorDto
} from "../../../lib/api/publicTypes";
import { PublicAnunciarSuccess } from "./PublicAnunciarSuccess";
import { PublicAnunciarValidation } from "./PublicAnunciarValidation";
import { PublicAnunciarWizardProgress } from "./PublicAnunciarWizardProgress";
import { PublicAnunciarWizardStep } from "./PublicAnunciarWizardStep";

type WizardStepId =
  | "intro"
  | "basicos"
  | "localizacao"
  | "contato"
  | "detalhes"
  | "midia"
  | "revisao"
  | "sucesso";

type WizardStep = {
  id: WizardStepId;
  label: string;
};

const WIZARD_STEPS: readonly WizardStep[] = [
  { id: "intro", label: "Início" },
  { id: "basicos", label: "Dados" },
  { id: "localizacao", label: "Local" },
  { id: "contato", label: "Contato" },
  { id: "detalhes", label: "Anúncio" },
  { id: "midia", label: "Mídia" },
  { id: "revisao", label: "Revisão" },
  { id: "sucesso", label: "Recebido" }
];

const INITIAL_FORM: SolicitarAnuncioPublicoRequestDto = {
  nomeExibicao: "",
  email: "",
  whatsapp: "",
  uf: "",
  cidade: "",
  bairro: "",
  titulo: "",
  descricao: "",
  preco: 0,
  categoria: "ACOMPANHANTE",
  aceiteTermos: false,
  confirmacaoIdade: false
};

const STEP_FIELDS: Record<WizardStepId, readonly string[]> = {
  intro: [],
  basicos: ["nomeExibicao", "email"],
  localizacao: ["uf", "cidade", "bairro"],
  contato: ["whatsapp"],
  detalhes: ["titulo", "descricao", "preco", "categoria"],
  midia: [],
  revisao: ["aceiteTermos", "confirmacaoIdade"],
  sucesso: []
};

const PHONE_OR_SOCIAL_IN_TITLE =
  /(\+?\d[\d .()_-]{7,}\d|whats|telefone|instagram|insta\b|telegram|t\.me|onlyfans|facebook|http|www\.|@)/i;

export function PublicAnunciarWizard() {
  const [form, setForm] = useState(INITIAL_FORM);
  const [currentStepIndex, setCurrentStepIndex] = useState(0);
  const [errors, setErrors] = useState<readonly SolicitarAnuncioValidationErrorDto[]>([]);
  const [success, setSuccess] = useState<SolicitarAnuncioPublicoResponseDto | null>(null);
  const [status, setStatus] = useState("Comece seu cadastro preenchendo as informações principais.");
  const [submitting, setSubmitting] = useState(false);

  const currentStep = WIZARD_STEPS[currentStepIndex];
  const reviewRows = useMemo(
    () => [
      ["Nome", form.nomeExibicao || "Não informado"],
      ["Cidade", [form.cidade, form.uf].filter(Boolean).join(" - ") || "Não informada"],
      ["Bairro", form.bairro || "Não informado"],
      ["WhatsApp", form.whatsapp || "Não informado"],
      ["Título", form.titulo || "Não informado"],
      ["Categoria", formatCategoriaPublica(form.categoria) || "Não informada"],
      ["Valor", form.preco > 0 ? `R$ ${form.preco}` : "Não informado"]
    ],
    [form]
  );

  async function continueWizard() {
    if (currentStep.id === "sucesso") {
      return;
    }

    const localErrors = validateStep(form, currentStep.id);
    setErrors(localErrors);
    setSuccess(null);
    if (localErrors.length > 0) {
      setStatus("Revise os campos desta etapa.");
      return;
    }

    if (currentStep.id !== "revisao") {
      setStatus("Etapa concluída.");
      setCurrentStepIndex((index) => Math.min(index + 1, WIZARD_STEPS.length - 1));
      return;
    }

    const allErrors = validate(form);
    setErrors(allErrors);
    if (allErrors.length > 0) {
      setStatus("Revise os campos destacados antes de enviar.");
      setCurrentStepIndex(firstStepIndexForErrors(allErrors));
      return;
    }

    setSubmitting(true);
    setStatus("Enviando seu anúncio para análise.");
    const response = await solicitarAnuncioPublico(normalizedForm(form));
    setSubmitting(false);
    if (response.ok) {
      setSuccess(response.data);
      setStatus("Solicitação enviada para análise.");
      setErrors([]);
      setCurrentStepIndex(WIZARD_STEPS.length - 1);
      return;
    }

    const responseErrors =
      response.validationErrors ?? [{ campo: "payload", codigo: "ERRO_ENVIO", mensagem: "tente novamente em instantes" }];
    setStatus(response.status === 0 ? "Não foi possível enviar agora." : "Revise os campos destacados.");
    setErrors(responseErrors);
    setCurrentStepIndex(firstStepIndexForErrors(responseErrors));
  }

  function backWizard() {
    setErrors([]);
    setStatus("Edite a etapa anterior.");
    setCurrentStepIndex((index) => Math.max(index - 1, 0));
  }

  function resetWizard() {
    setForm(INITIAL_FORM);
    setErrors([]);
    setSuccess(null);
    setStatus("Comece seu cadastro preenchendo as informações principais.");
    setCurrentStepIndex(0);
  }

  return (
    <div className="public-anunciar-layout">
      <form className="public-anunciar-form public-wizard" onSubmit={(event) => event.preventDefault()} noValidate>
        <PublicAnunciarWizardProgress steps={WIZARD_STEPS} currentIndex={currentStepIndex} />
        {renderStep()}
        <PublicAnunciarValidation errors={errors} />
        <div className="public-submit-row public-wizard-actions">
          {currentStep.id !== "intro" && currentStep.id !== "sucesso" ? (
            <button className="local-secondary-action" type="button" onClick={backWizard} disabled={submitting}>
              Voltar
            </button>
          ) : null}
          {currentStep.id === "sucesso" ? (
            <button className="local-action" type="button" onClick={resetWizard}>
              Enviar outro anúncio
            </button>
          ) : (
            <button className="local-action" type="button" onClick={continueWizard} disabled={submitting}>
              {currentStep.id === "revisao" ? (submitting ? "Enviando" : "Enviar para análise") : "Continuar"}
            </button>
          )}
          <span aria-live="polite">{status}</span>
        </div>
      </form>

      <aside className="public-anunciar-side" aria-label="Como funciona">
        <span className="status">Confiança e segurança</span>
        <h2>Apareça para mais clientes com revisão antes da publicação</h2>
        <ul>
          <li>Cadastro guiado em etapas curtas.</li>
          <li>Contato direto só depois das regras públicas futuras.</li>
          <li>Moderação antes de qualquer exibição pública.</li>
          <li>Fotos, vídeos e pagamentos ficam fora deste cadastro inicial.</li>
        </ul>
      </aside>
    </div>
  );

  function renderStep() {
    switch (currentStep.id) {
      case "intro":
        return (
          <PublicAnunciarWizardStep
            title="Comece seu anúncio gratuito"
            description="Crie seu anúncio em etapas simples. O envio é gratuito e passa por revisão antes de qualquer publicação."
          >
            <div className="public-wizard-intro">
              <strong>PUBLICAR SEU ANÚNCIO</strong>
              <p>Preencha os dados principais, revise tudo no fim e aguarde a análise antes de qualquer publicação.</p>
              <ul>
                <li>Sem publicação automática.</li>
                <li>Sem upload de foto ou documento nesta etapa.</li>
                <li>Sem Premium obrigatório para anunciar.</li>
              </ul>
            </div>
          </PublicAnunciarWizardStep>
        );
      case "basicos":
        return (
          <PublicAnunciarWizardStep
            title="Dados básicos"
            description="Informe como seu perfil deve ser identificado pela equipe de análise."
          >
            <div className="public-field-grid">
              <label>
                Nome para exibição
                <input
                  name="nomeExibicao"
                  value={form.nomeExibicao}
                  onChange={(event) => update("nomeExibicao", event.target.value)}
                  placeholder="Nome que aparecerá no anúncio"
                />
              </label>
              <label>
                E-mail
                <input
                  name="email"
                  value={form.email}
                  onChange={(event) => update("email", event.target.value)}
                  placeholder="contato@example.invalid"
                />
              </label>
            </div>
          </PublicAnunciarWizardStep>
        );
      case "localizacao":
        return (
          <PublicAnunciarWizardStep
            title="Localização"
            description="Informe a área principal do anúncio. O bairro pode ficar em branco quando ainda não estiver definido."
          >
            <div className="public-field-grid">
              <label>
                Estado
                <input
                  name="uf"
                  value={form.uf}
                  onChange={(event) => update("uf", event.target.value.toUpperCase())}
                  maxLength={2}
                  placeholder="ZZ"
                />
              </label>
              <label>
                Cidade
                <input
                  name="cidade"
                  value={form.cidade}
                  onChange={(event) => update("cidade", event.target.value)}
                  placeholder="Cidade"
                />
              </label>
              <label>
                Bairro
                <input
                  name="bairro"
                  value={form.bairro}
                  onChange={(event) => update("bairro", event.target.value)}
                  placeholder="Bairro"
                />
              </label>
            </div>
          </PublicAnunciarWizardStep>
        );
      case "contato":
        return (
          <PublicAnunciarWizardStep
            title="Contato"
            description="Informe o contato para análise inicial. Ele não será liberado publicamente por este wizard."
          >
            <label>
              WhatsApp
              <input
                name="whatsapp"
                value={form.whatsapp}
                onChange={(event) => update("whatsapp", event.target.value)}
                placeholder="+55 00 00000-0000"
                inputMode="tel"
              />
            </label>
            <div className="public-upload-future" aria-label="Aviso de contato">
              <strong>O WhatsApp público não é liberado por este formulário.</strong>
              <p>A exibição pública futura depende de moderação e política de contato.</p>
            </div>
          </PublicAnunciarWizardStep>
        );
      case "detalhes":
        return (
          <PublicAnunciarWizardStep
            title="Detalhes do anúncio"
            description="Use um título claro, com foco no anúncio, sem telefone, redes sociais ou links."
          >
            <label>
              Título do anúncio
              <input
                name="titulo"
                value={form.titulo}
                onChange={(event) => update("titulo", event.target.value)}
                placeholder="Título curto e claro"
                maxLength={80}
              />
            </label>
            <label>
              Descrição
              <textarea
                name="descricao"
                value={form.descricao}
                onChange={(event) => update("descricao", event.target.value)}
                placeholder="Conte como será seu anúncio e quais informações devem ser analisadas."
                maxLength={600}
                rows={5}
              />
            </label>
            <div className="public-field-grid">
              <label>
                Valor
                <input
                  name="preco"
                  value={form.preco || ""}
                  onChange={(event) => update("preco", Number(event.target.value))}
                  inputMode="decimal"
                  type="number"
                  min="1"
                  step="1"
                  placeholder="120"
                />
              </label>
              <label>
                Categoria
                <select name="categoria" value={form.categoria} onChange={(event) => update("categoria", event.target.value)}>
                  <option value="ACOMPANHANTE">Acompanhante</option>
                  <option value="MASSAGEM">Massagem</option>
                  <option value="LOCAL_TESTE">Outra categoria</option>
                </select>
              </label>
            </div>
          </PublicAnunciarWizardStep>
        );
      case "midia":
        return (
          <PublicAnunciarWizardStep
            title="Fotos e vídeos"
            description="As mídias passam por revisão antes de aparecerem publicamente."
          >
            <div className="public-upload-future" aria-label="Upload futuro">
              <strong>Upload ainda não está disponível neste fluxo.</strong>
              <p>Fotos, vídeos e documentos não são solicitados aqui. O anúncio segue apenas com texto para análise.</p>
            </div>
          </PublicAnunciarWizardStep>
        );
      case "revisao":
        return (
          <PublicAnunciarWizardStep
            title="Revisão final"
            description="Confira os dados antes de enviar. O envio acontece somente ao clicar no botão final."
          >
            <dl className="public-submit-summary public-wizard-review">
              {reviewRows.map(([label, value]) => (
                <div key={label}>
                  <dt>{label}</dt>
                  <dd>{value}</dd>
                </div>
              ))}
            </dl>
            <fieldset className="public-form-checks">
              <legend>Confirmações</legend>
              <label>
                <input
                  name="aceiteTermos"
                  type="checkbox"
                  checked={form.aceiteTermos}
                  onChange={(event) => update("aceiteTermos", event.target.checked)}
                />
                Li e aceito os termos para envio do anúncio.
              </label>
              <label>
                <input
                  name="confirmacaoIdade"
                  type="checkbox"
                  checked={form.confirmacaoIdade}
                  onChange={(event) => update("confirmacaoIdade", event.target.checked)}
                />
                Confirmo que sou maior de idade e que não estou enviando documento ou foto nesta etapa.
              </label>
            </fieldset>
          </PublicAnunciarWizardStep>
        );
      case "sucesso":
        return (
          <PublicAnunciarWizardStep
            title="Solicitação recebida"
            description="O anúncio foi enviado para análise e não foi publicado automaticamente."
          >
            <PublicAnunciarSuccess response={success} />
          </PublicAnunciarWizardStep>
        );
      default:
        return null;
    }
  }

  function update<K extends keyof SolicitarAnuncioPublicoRequestDto>(
    field: K,
    value: SolicitarAnuncioPublicoRequestDto[K]
  ) {
    setForm((current) => ({ ...current, [field]: value }));
  }
}

function normalizedForm(form: SolicitarAnuncioPublicoRequestDto): SolicitarAnuncioPublicoRequestDto {
  return {
    ...form,
    nomeExibicao: form.nomeExibicao.trim(),
    email: form.email.trim(),
    whatsapp: form.whatsapp.trim(),
    uf: form.uf.trim(),
    cidade: form.cidade.trim(),
    bairro: form.bairro.trim(),
    titulo: form.titulo.trim(),
    descricao: form.descricao.trim(),
    categoria: form.categoria.trim()
  };
}

function formatCategoriaPublica(categoria: string): string {
  switch (categoria.trim()) {
    case "ACOMPANHANTE":
      return "Acompanhante";
    case "MASSAGEM":
      return "Massagem";
    case "LOCAL_TESTE":
      return "Outra categoria";
    default:
      return "";
  }
}

function firstStepIndexForErrors(errors: readonly SolicitarAnuncioValidationErrorDto[]): number {
  for (const error of errors) {
    const index = WIZARD_STEPS.findIndex((step) => STEP_FIELDS[step.id].includes(error.campo));
    if (index >= 0) {
      return index;
    }
  }
  return 0;
}

function validateStep(
  form: SolicitarAnuncioPublicoRequestDto,
  step: WizardStepId
): SolicitarAnuncioValidationErrorDto[] {
  const fields = STEP_FIELDS[step];
  if (fields.length === 0) {
    return [];
  }
  return validate(form).filter((error) => fields.includes(error.campo));
}

function validate(form: SolicitarAnuncioPublicoRequestDto): SolicitarAnuncioValidationErrorDto[] {
  const errors: SolicitarAnuncioValidationErrorDto[] = [];
  requireText(errors, "nomeExibicao", form.nomeExibicao, 3, 80);
  if (form.email.trim() && !/^[a-z0-9._%+-]+@example\.invalid$/i.test(form.email.trim())) {
    errors.push({ campo: "email", codigo: "EMAIL_NAO_RESERVADO", mensagem: "informe um e-mail permitido nesta etapa" });
  }
  if (form.whatsapp.trim() !== "+5500000000000") {
    errors.push({ campo: "whatsapp", codigo: "WHATSAPP_INVALIDO", mensagem: "informe um WhatsApp válido para esta etapa" });
  }
  if (!/^[A-Za-z]{2}$/.test(form.uf.trim())) {
    errors.push({ campo: "uf", codigo: "UF_INVALIDA", mensagem: "informe a sigla do estado" });
  }
  requireText(errors, "cidade", form.cidade, 3, 80);
  if (form.bairro.trim() && (form.bairro.trim().length < 2 || form.bairro.trim().length > 80)) {
    errors.push({ campo: "bairro", codigo: "TAMANHO_INVALIDO", mensagem: "informe entre 2 e 80 caracteres" });
  }
  requireText(errors, "titulo", form.titulo, 10, 80);
  requireText(errors, "descricao", form.descricao, 20, 600);
  if (PHONE_OR_SOCIAL_IN_TITLE.test(form.titulo)) {
    errors.push({
      campo: "titulo",
      codigo: "TITULO_CONTATO_OU_REDE_SOCIAL",
      mensagem: "não coloque telefone, WhatsApp ou rede social no título"
    });
  }
  if (!Number.isFinite(form.preco) || form.preco <= 0) {
    errors.push({ campo: "preco", codigo: "PRECO_INVALIDO", mensagem: "informe um valor maior que zero" });
  }
  if (!form.categoria.trim()) {
    errors.push({ campo: "categoria", codigo: "CAMPO_OBRIGATORIO", mensagem: "informe a categoria" });
  }
  if (!form.aceiteTermos) {
    errors.push({ campo: "aceiteTermos", codigo: "ACEITE_TERMOS_OBRIGATORIO", mensagem: "confirme os termos" });
  }
  if (!form.confirmacaoIdade) {
    errors.push({
      campo: "confirmacaoIdade",
      codigo: "CONFIRMACAO_IDADE_OBRIGATORIA",
      mensagem: "confirme a maioridade"
    });
  }
  return errors;
}

function requireText(
  errors: SolicitarAnuncioValidationErrorDto[],
  campo: string,
  value: string,
  min: number,
  max: number
) {
  const normalized = value.trim();
  if (normalized.length < min || normalized.length > max) {
    errors.push({ campo, codigo: "TAMANHO_INVALIDO", mensagem: `informe entre ${min} e ${max} caracteres` });
  }
}
