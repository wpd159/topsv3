import type { AnuncioDetalhePublicoDto } from "../../../lib/api/publicTypes";
import { PublicMidiaPlaceholder } from "./PublicMidiaPlaceholder";

type PublicAnuncioDetalheProps = {
  anuncio: AnuncioDetalhePublicoDto;
  status: string;
};

export function PublicAnuncioDetalhe({ anuncio, status }: PublicAnuncioDetalheProps) {
  return (
    <section className="public-detail-layout" aria-label="Detalhe público do anúncio">
      <PublicMidiaPlaceholder midias={anuncio.midias} />
      <article className="public-detail-copy">
        <span className="status">Perfil público</span>
        <h2>{anuncio.titulo ?? "Anúncio"}</h2>
        <p>{anuncio.descricao ?? "Descrição indisponível no momento."}</p>
        <dl className="health-grid compact public-detail-meta">
          <div>
            <dt>Status</dt>
            <dd>{status}</dd>
          </div>
          <div>
            <dt>Valor</dt>
            <dd>{formatPrice(anuncio.preco)}</dd>
          </div>
          <div>
            <dt>Local</dt>
            <dd>{formatLocation(anuncio.localizacao)}</dd>
          </div>
          <div>
            <dt>Contato</dt>
            <dd>{anuncio.contatoPublico ? "mediado pelo sistema" : "não exposto"}</dd>
          </div>
        </dl>
        {anuncio.beneficiosPublicos.length > 0 ? (
          <ul className="public-benefit-list" aria-label="Beneficios publicos">
            {anuncio.beneficiosPublicos.map((beneficio) => (
              <li key={beneficio}>{beneficio}</li>
            ))}
          </ul>
        ) : null}
        <p className="public-policy-note">
          {anuncio.pendenciaContatoPublico ?? "Contato público sujeito à política de segurança."}
        </p>
      </article>
    </section>
  );
}

function formatPrice(preco: number | null): string {
  if (preco === null) {
    return "Consultar";
  }
  return new Intl.NumberFormat("pt-BR", {
    style: "currency",
    currency: "BRL",
    maximumFractionDigits: 0
  }).format(preco);
}

function formatLocation(item: AnuncioDetalhePublicoDto["localizacao"]): string {
  if (!item) {
    return "Localidade a confirmar";
  }
  return [item.bairro, item.cidade, item.uf].filter(Boolean).join(", ") || "Localidade a confirmar";
}
