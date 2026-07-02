"use client";

import { useState } from "react";

import {
  confirmarIdadePublica,
  getAnuncioPublico
} from "../../../lib/api/publicApi";
import type { AnuncioDetalhePublicoDto } from "../../../lib/api/publicTypes";
import { PublicAgeGateStories } from "./PublicAgeGateStories";
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
  const [birthDate, setBirthDate] = useState("1990-01-01");
  const [state, setState] = useState<AgeGateState>(
    initialAnuncio ? "conteudo_autorizado" : "aguardando_idade"
  );
  const [message, setMessage] = useState(initialMessage ?? "conteudo indisponivel localmente");

  async function handleConfirmAge() {
    if (state === "confirmando") {
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
        <section className="panel" aria-label="API publica local">
          <p>API publica local respondeu ao contrato de leitura do anuncio.</p>
          <dl className="health-grid compact">
            <div>
              <dt>Status</dt>
              <dd>{state}</dd>
            </div>
            <div>
              <dt>Midias publicas</dt>
              <dd>{anuncio.midias.length}</dd>
            </div>
            <div>
              <dt>Midia local</dt>
              <dd>{mediaStatus(anuncio.midias)}</dd>
            </div>
            <div>
              <dt>WhatsApp publico</dt>
              <dd>{anuncio.contatoPublico ?? "nao exposto"}</dd>
            </div>
          </dl>
          <p>{anuncio.pendenciaContatoPublico ?? "PENDENTE_POLITICA_EXPOSICAO_WHATSAPP_PUBLICO"}</p>
        </section>
      ) : (
        <section className="panel muted" aria-label="Fallback local">
          <p>{message}</p>
          <p>Status inicial da API local: {initialStatus || "indisponivel"}</p>
        </section>
      )}

      <section className="panel" aria-label="Confirmacao de idade local para anuncio">
        <dl className="health-grid compact">
          <div>
            <dt>Fluxo local</dt>
            <dd>{state}</dd>
          </div>
          <div>
            <dt>Backend</dt>
            <dd>{anuncio ? "autorizou" : "nao autorizou"}</dd>
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
              disabled={state === "confirmando"}
            >
              Confirmar idade
            </button>
          </div>
        )}
      </section>

      <PublicMetricActions slug={slug} enabled={Boolean(anuncio)} />
      <PublicAgeGateStories slug={slug} enabled={Boolean(anuncio)} />
    </>
  );
}

function mediaStatus(midias: AnuncioDetalhePublicoDto["midias"]): string {
  if (midias.length === 0) {
    return "sem midia";
  }
  if (midias.some((midia) => Boolean(midia.urlPublica))) {
    return "url publica autorizada";
  }
  return midias.find((midia) => Boolean(midia.pendenciaMidia))?.pendenciaMidia ?? "PENDENTE_URL_PUBLICA_MIDIA_CDN";
}
