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
  initialMessage
}: PublicAgeGateContentProps) {
  const [anuncio, setAnuncio] = useState<AnuncioDetalhePublicoDto | null>(initialAnuncio);
  const [birthDate, setBirthDate] = useState("");
  const [state, setState] = useState<AgeGateState>(
    initialAnuncio ? "conteudo_autorizado" : "aguardando_idade"
  );
  const [message, setMessage] = useState(initialMessage ?? "Conteúdo indisponível no momento");
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
      setMessage("Idade não confirmada.");
      setState("idade_negada");
      return;
    }

    const detailResponse = await getAnuncioPublico(slug);
    if (detailResponse.ok) {
      setAnuncio(detailResponse.data);
      setMessage("Conteúdo liberado para visualização.");
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
          title="Conteúdo protegido"
          message={message || "Confirme sua idade para ver as informações disponíveis."}
        />
      )}

      <section className="panel" aria-label="Confirmação de idade para anúncio">
        <dl className="health-grid compact">
          <div>
            <dt>Status</dt>
            <dd>{formatAgeState(state)}</dd>
          </div>
          <div>
            <dt>Acesso</dt>
            <dd>{anuncio ? "permitido" : "pendente"}</dd>
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
      return "Conteúdo disponível";
    case "aguardando_idade":
      return "Aguardando confirmação";
    case "confirmando":
      return "Confirmando idade";
    case "idade_negada":
      return "Idade não confirmada";
    case "indisponivel":
      return "Indisponível";
  }
}
