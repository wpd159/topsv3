"use client";

import { useState } from "react";

import {
  confirmarIdadePublica,
  getAnuncioPublico
} from "../../../lib/api/publicApi";
import type { AnuncioDetalhePublicoDto } from "../../../lib/api/publicTypes";
import { PublicAnuncioDetalhe } from "../components/PublicAnuncioDetalhe";
import { PublicEmptyState } from "../components/PublicEmptyState";
import { PublicStoriesGate } from "../components/PublicStoriesGate";
import { PublicMetricActions } from "./PublicMetricActions";

type PublicAgeGateContentProps = {
  slug: string;
  initialAnuncio: AnuncioDetalhePublicoDto | null;
  initialStatus: number;
  initialMessage: string | null;
};

type AgeGateState = "conteudo_autorizado" | "aguardando_idade" | "confirmando" | "idade_negada" | "indisponivel";

export function PublicAgeGateContent({
  slug,
  initialAnuncio,
  initialStatus,
  initialMessage
}: PublicAgeGateContentProps) {
  const [anuncio, setAnuncio] = useState<AnuncioDetalhePublicoDto | null>(initialAnuncio);
  const [birthDate, setBirthDate] = useState("");
  const [state, setState] = useState<AgeGateState>(
    initialAnuncio ? "conteudo_autorizado" : "aguardando_idade"
  );
  const [message, setMessage] = useState(initialMessage ?? "conteudo indisponivel localmente");
  const birthDateIsValid = isValidBirthDate(birthDate);
  const canConfirmAge = birthDateIsValid && state !== "confirmando";

  async function handleConfirmAge() {
    if (!canConfirmAge) {
      return;
    }
    setState("confirmando");
    const ageResponse = await confirmarIdadePublica({
      dataNascimento: birthDate,
      declaracaoMaioridade: true
    });
    if (!ageResponse.ok || !ageResponse.data.confirmada) {
      setMessage("idade nao confirmada");
      setState("idade_negada");
      return;
    }

    const detailResponse = await getAnuncioPublico(slug);
    if (detailResponse.ok) {
      setAnuncio(detailResponse.data);
      setMessage("conteudo autorizado pelo backend local");
      setState("conteudo_autorizado");
      return;
    }

    setAnuncio(null);
    setMessage(detailResponse.message);
    setState("indisponivel");
  }

  return (
    <>
      {anuncio ? (
        <PublicAnuncioDetalhe anuncio={anuncio} status={state} />
      ) : (
        <PublicEmptyState
          title="Conteudo local protegido"
          message={`${message}. Verificacao inicial: ${initialStatus || "indisponivel"}.`}
        />
      )}

      <section className="panel" aria-label="Confirmacao de idade local para anuncio">
        <dl className="health-grid compact">
          <div>
            <dt>Fluxo</dt>
            <dd>{formatAgeState(state)}</dd>
          </div>
          <div>
            <dt>Autorizacao</dt>
            <dd>{anuncio ? "autorizada" : "pendente"}</dd>
          </div>
        </dl>
        {anuncio ? (
          <p>{message}</p>
        ) : (
          <div className="local-inline-form">
            <label>
              Data de nascimento
              <input
                type="date"
                value={birthDate}
                onChange={(event) => setBirthDate(event.target.value)}
                max="2008-01-01"
              />
            </label>
            <button
              className="local-action"
              type="button"
              onClick={handleConfirmAge}
              disabled={!canConfirmAge}
            >
              Confirmar idade
            </button>
          </div>
        )}
      </section>

      <PublicMetricActions slug={slug} enabled={Boolean(anuncio)} />
      <PublicStoriesGate slug={slug} enabled={Boolean(anuncio)} />
    </>
  );
}

function isValidBirthDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return false;
  }
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) {
    return false;
  }
  return value <= "2008-01-01";
}

function formatAgeState(state: AgeGateState): string {
  switch (state) {
    case "conteudo_autorizado":
      return "autorizado";
    case "aguardando_idade":
      return "aguardando idade";
    case "confirmando":
      return "confirmando";
    case "idade_negada":
      return "idade nao confirmada";
    case "indisponivel":
      return "indisponivel";
  }
}
