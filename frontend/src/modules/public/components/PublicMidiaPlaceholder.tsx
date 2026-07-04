import type { MidiaPublicaDto } from "../../../lib/api/publicTypes";

type PublicMidiaPlaceholderProps = {
  midias?: readonly MidiaPublicaDto[];
  compact?: boolean;
};

export function PublicMidiaPlaceholder({ midias = [], compact = false }: PublicMidiaPlaceholderProps) {
  const total = midias.length;

  return (
    <div className={compact ? "public-media-placeholder compact-media" : "public-media-placeholder"}>
      <span>Mídia pública</span>
      <strong>{total > 0 ? "Mídia em análise" : "Mídia indisponível"}</strong>
      <small>Fotos e vídeos só aparecem quando estão aprovados para exibição.</small>
    </div>
  );
}
