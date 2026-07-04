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
      <strong>Solicitação enviada para análise.</strong>
      <p>Seu anúncio foi recebido e será revisado antes de aparecer no site.</p>
      <ul className="public-success-list">
        <li>A publicação não é automática.</li>
        <li>A equipe avaliará as informações enviadas.</li>
        <li>Fotos, vídeos e impulsionamentos entram em etapas seguras.</li>
      </ul>
    </div>
  );
}
