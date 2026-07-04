"use client";

import { useEffect, useState } from "react";

import {
  registrarCliqueWhatsappPublico,
  registrarVisualizacaoPublica
} from "../../../lib/api/publicApi";
import { PublicContatoAction } from "../components/PublicContatoAction";

type PublicMetricActionsProps = {
  slug: string;
  enabled: boolean;
};

type ContactState =
  | { kind: "idle" }
  | { kind: "loading" }
  | { kind: "unavailable"; message: string }
  | { kind: "available" };

export function PublicMetricActions({ slug, enabled }: PublicMetricActionsProps) {
  const [viewStatus, setViewStatus] = useState("pendente");
  const [contact, setContact] = useState<ContactState>({ kind: "idle" });

  useEffect(() => {
    if (!enabled) {
      setViewStatus("indisponivel");
      return;
    }
    let cancelled = false;
    registrarVisualizacaoPublica(slug, {
      dispositivo: "DESCONHECIDO"
    }).then((response) => {
      if (cancelled) {
        return;
      }
      setViewStatus(response.ok && response.data.registrado ? "registrada" : "indisponivel");
    });
    return () => {
      cancelled = true;
    };
  }, [enabled, slug]);

  async function handleContactClick() {
    if (!enabled || contact.kind === "loading") {
      return;
    }
    setContact({ kind: "loading" });
    const response = await registrarCliqueWhatsappPublico(slug, {
      dispositivo: "DESCONHECIDO"
    });
    if (response.ok && response.data.disponivel) {
      setContact({ kind: "available" });
      return;
    }
    setContact({ kind: "unavailable", message: "Contato indisponível no momento." });
  }

  return (
    <section className="panel" aria-label="Métricas públicas">
      <dl className="health-grid compact">
        <div>
          <dt>Visualização</dt>
          <dd>{viewStatus}</dd>
        </div>
        <div>
          <dt>Stories</dt>
          <dd>protegidos</dd>
        </div>
      </dl>
      <PublicContatoAction
        enabled={enabled}
        loading={contact.kind === "loading"}
        status={contact.kind === "available" ? "available" : contact.kind === "unavailable" ? "unavailable" : "idle"}
        unavailableMessage={contact.kind === "unavailable" ? contact.message : null}
        onClick={handleContactClick}
      />
    </section>
  );
}
