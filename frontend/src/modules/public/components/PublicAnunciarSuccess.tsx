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
      <strong>Anúncio recebido.</strong>
      <p>Seu anúncio foi enviado para o Tops do Job.</p>
      <ul className="public-success-list">
        <li>Guarde seus dados de contato atualizados.</li>
        <li>Fotos e vídeos entram em uma etapa própria.</li>
        <li>Você pode enviar outro anúncio quando quiser.</li>
      </ul>
    </div>
  );
}
