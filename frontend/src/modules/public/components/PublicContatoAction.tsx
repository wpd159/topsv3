"use client";

type PublicContatoActionProps = {
  enabled: boolean;
  loading: boolean;
  status: "idle" | "available" | "unavailable";
  unavailableMessage: string | null;
  onClick: () => void;
};

export function PublicContatoAction({
  enabled,
  loading,
  status,
  unavailableMessage,
  onClick
}: PublicContatoActionProps) {
  return (
    <div className="public-contact-action">
      <button className="local-action" type="button" onClick={onClick} disabled={!enabled || loading}>
        Ver WhatsApp
      </button>
      {status === "available" ? <p>Contato autorizado pelo backend local.</p> : null}
      {status === "unavailable" ? <p>{unavailableMessage ?? "contato indisponivel"}</p> : null}
    </div>
  );
}
