import type { MidiaPublicaDto } from "../../../lib/api/publicTypes";

type PublicMidiaPlaceholderProps = {
  midias?: readonly MidiaPublicaDto[];
  compact?: boolean;
};

export function PublicMidiaPlaceholder({ midias = [], compact = false }: PublicMidiaPlaceholderProps) {
  const total = midias.length;
  const label = total > 1 ? `${total} fotos` : total === 1 ? "1 foto" : "Fotos";
  const title = total > 0 ? "Fotos do anúncio" : "Fotos em breve";

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
        <small>Fotos e vídeos aparecem aqui quando estiverem disponíveis.</small>
      </div>
    </div>
  );
}
