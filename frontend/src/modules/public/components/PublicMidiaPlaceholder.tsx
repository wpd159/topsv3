import type { MidiaPublicaDto } from "../../../lib/api/publicTypes";

type PublicMidiaPlaceholderProps = {
  midias?: readonly MidiaPublicaDto[];
  compact?: boolean;
};

export function PublicMidiaPlaceholder({ midias = [], compact = false }: PublicMidiaPlaceholderProps) {
  const total = midias.length;
  const label = total > 1 ? `${total} mídias` : total === 1 ? "1 mídia" : "Mídia pública";
  const title = total > 0 ? "Mídia em análise" : "Mídia pública";

  return (
    <div className={compact ? "public-media-placeholder compact-media" : "public-media-placeholder"}>
      <div className="public-media-placeholder-frame" aria-hidden="true">
        <span />
        <span />
        <span />
      </div>
      <div className="public-media-placeholder-copy">
        <span>{label}</span>
        <strong>{title}</strong>
        <small>Fotos e vídeos aparecem quando estiverem liberados para exibição.</small>
      </div>
    </div>
  );
}
