import type { MidiaPublicaDto } from "../../../lib/api/publicTypes";

type PublicMidiaPlaceholderProps = {
  midias?: readonly MidiaPublicaDto[];
  compact?: boolean;
};

export function PublicMidiaPlaceholder({ midias = [], compact = false }: PublicMidiaPlaceholderProps) {
  const pendencia =
    midias.find((midia) => Boolean(midia.pendenciaMidia))?.pendenciaMidia ?? "PENDENTE_URL_PUBLICA_MIDIA_CDN";
  const total = midias.length;

  return (
    <div className={compact ? "public-media-placeholder compact-media" : "public-media-placeholder"}>
      <span>Midia publica</span>
      <strong>{total > 0 ? "Aguardando CDN local" : "Placeholder local"}</strong>
      <small>{pendencia}</small>
    </div>
  );
}
