import type { SolicitarAnuncioPublicoResponseDto } from "../../../lib/api/publicTypes";

type PublicAnunciarSuccessProps = {
  response: SolicitarAnuncioPublicoResponseDto | null;
};

export function PublicAnunciarSuccess({ response }: PublicAnunciarSuccessProps) {
  if (!response) {
    return null;
  }

  return (
    <div className="public-form-feedback public-form-feedback-success" role="status">
      <strong>Solicitacao enviada para analise.</strong>
      <p>Seu anuncio foi recebido e sera revisado antes de aparecer no site.</p>
      <ul className="public-success-list">
        <li>A publicacao nao e automatica.</li>
        <li>A equipe avaliara as informacoes enviadas.</li>
        <li>Fotos, videos e impulsionamentos entram em etapas seguras.</li>
      </ul>
    </div>
  );
}
