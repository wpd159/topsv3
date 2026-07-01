"use client";

import { useEffect, useState } from "react";

import {
  confirmarIdadePublica,
  getStatusIdadePublica,
  getStoriesPublicos
} from "../../../lib/api/publicApi";
import type { ListaStoriesPublicosDto } from "../../../lib/api/publicTypes";

type PublicAgeGateStoriesProps = {
  slug: string;
  enabled: boolean;
};

type AgeState = "pendente" | "confirmada" | "negada" | "indisponivel";

export function PublicAgeGateStories({ slug, enabled }: PublicAgeGateStoriesProps) {
  const [ageState, setAgeState] = useState<AgeState>("pendente");
  const [birthDate, setBirthDate] = useState("1990-01-01");
  const [stories, setStories] = useState<ListaStoriesPublicosDto | null>(null);

  useEffect(() => {
    if (!enabled) {
      setAgeState("indisponivel");
      return;
    }
    let cancelled = false;
    getStatusIdadePublica().then((response) => {
      if (cancelled) {
        return;
      }
      setAgeState(response.ok && response.data.confirmada ? "confirmada" : "pendente");
    });
    return () => {
      cancelled = true;
    };
  }, [enabled]);

  useEffect(() => {
    if (!enabled || ageState !== "confirmada") {
      return;
    }
    let cancelled = false;
    getStoriesPublicos(slug).then((response) => {
      if (!cancelled && response.ok) {
        setStories(response.data);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [ageState, enabled, slug]);

  async function handleConfirm() {
    if (!enabled) {
      return;
    }
    const response = await confirmarIdadePublica({
      dataNascimento: birthDate,
      declaracaoMaioridade: true
    });
    if (response.ok && response.data.confirmada) {
      setAgeState("confirmada");
      return;
    }
    setAgeState("negada");
  }

  return (
    <section className="panel" aria-label="Confirmacao de idade local">
      <dl className="health-grid compact">
        <div>
          <dt>Idade</dt>
          <dd>{ageState}</dd>
        </div>
        <div>
          <dt>Stories autorizados</dt>
          <dd>{stories?.stories.length ?? 0}</dd>
        </div>
      </dl>
      {ageState === "confirmada" ? (
        <p>{stories?.politica.pendencia ?? "PENDENTE_URL_PUBLICA_MIDIA_CDN"}</p>
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
          <button className="local-action" type="button" onClick={handleConfirm} disabled={!enabled}>
            Confirmar idade
          </button>
        </div>
      )}
      {ageState === "negada" ? <p>idade nao confirmada</p> : null}
    </section>
  );
}
