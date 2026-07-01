"use client";

import { useEffect, useState } from "react";

import {
  registrarCliqueWhatsappPublico,
  registrarVisualizacaoPublica
} from "../../../lib/api/publicApi";

type PublicMetricActionsProps = {
  slug: string;
  enabled: boolean;
};

type ContactState =
  | { kind: "idle" }
  | { kind: "loading" }
  | { kind: "unavailable"; message: string }
  | { kind: "available"; whatsappUrl: string };

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
    if (response.ok && response.data.disponivel && response.data.whatsappUrl) {
      setContact({ kind: "available", whatsappUrl: response.data.whatsappUrl });
      return;
    }
    setContact({ kind: "unavailable", message: "contato indisponivel" });
  }

  return (
    <section className="panel" aria-label="Metricas publicas locais">
      <dl className="health-grid compact">
        <div>
          <dt>Visualizacao local</dt>
          <dd>{viewStatus}</dd>
        </div>
        <div>
          <dt>Stories</dt>
          <dd>PENDENTE_URL_PUBLICA_MIDIA_CDN</dd>
        </div>
      </dl>
      <button className="local-action" type="button" onClick={handleContactClick} disabled={!enabled || contact.kind === "loading"}>
        Ver WhatsApp
      </button>
      {contact.kind === "available" ? <p>{contact.whatsappUrl}</p> : null}
      {contact.kind === "unavailable" ? <p>{contact.message}</p> : null}
    </section>
  );
}
