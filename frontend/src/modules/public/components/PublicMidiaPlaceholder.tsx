import type { MidiaPublicaDto } from "../../../lib/api/publicTypes";

type PublicMidiaPlaceholderProps = {
  midias?: readonly MidiaPublicaDto[];
  compact?: boolean;
};

export function PublicMidiaPlaceholder({ midias = [], compact = false }: PublicMidiaPlaceholderProps) {
  const total = midias.length;

  return (
    <div className={compact ? "public-media-placeholder compact-media" : "public-media-placeholder"}>
      <span>Fotos</span>
      <strong>{total > 0 ? "Fotos em revisão" : "Fotos em breve"}</strong>
      <small>As imagens aparecem quando estiverem liberadas para exibição.</small>
    </div>
  );
}
