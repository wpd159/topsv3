import type { AnuncioDetalhePublicoDto } from "../../../lib/api/publicTypes";
import { PublicMidiaPlaceholder } from "./PublicMidiaPlaceholder";

type PublicAnuncioDetalheProps = {
  anuncio: AnuncioDetalhePublicoDto;
  status: string;
};

export function PublicAnuncioDetalhe({ anuncio, status }: PublicAnuncioDetalheProps) {
  const badges = [
    anuncio.destaque ? "Destaque" : null,
    anuncio.topo ? "Topo" : null,
    anuncio.midiaExtra ? "Mídia extra" : null,
    anuncio.story ? "Stories" : null
  ].filter((badge): badge is string => Boolean(badge));

  return (
    <section className="public-detail-layout" aria-label="Detalhe público do anúncio">
      <div className="public-detail-gallery">
        <PublicMidiaPlaceholder midias={anuncio.midias} />
        <div className="public-detail-thumb-row" aria-label="Prévia de mídia">
          <span>Foto principal</span>
          <span>Galeria</span>
          <span>Stories</span>
        </div>
      </div>
      <aside className="public-detail-sidebar" aria-label="Resumo do perfil">
        <span className="status">Perfil público</span>
        <h2>{anuncio.titulo ?? "Anúncio"}</h2>
        <strong className="public-detail-price">{formatPrice(anuncio.preco)}</strong>
        <p>{formatLocation(anuncio.localizacao)}</p>
        {badges.length > 0 ? (
          <ul className="public-badge-list" aria-label="Marcadores do anúncio">
            {badges.map((badge) => (
              <li key={badge}>{badge}</li>
            ))}
          </ul>
        ) : null}
        <div className="public-detail-cta" aria-label="Contato mediado">
          {anuncio.contatoPublico ? "Contato mediado pelo Tops do Job" : "Contato protegido"}
        </div>
        <p className="public-policy-note">{formatarContatoPublico(anuncio.pendenciaContatoPublico)}</p>
      </aside>
      <article className="public-detail-copy">
        <section className="public-detail-section">
          <h3>Sobre o perfil</h3>
          <p>{anuncio.descricao ?? "Descrição indisponível no momento."}</p>
        </section>
        <dl className="health-grid compact public-detail-meta">
          <div>
            <dt>Status</dt>
            <dd>{formatarStatusPublico(status)}</dd>
          </div>
          <div>
            <dt>Valor</dt>
            <dd>{formatPrice(anuncio.preco)}</dd>
          </div>
          <div>
            <dt>Localização</dt>
            <dd>{formatLocation(anuncio.localizacao)}</dd>
          </div>
          <div>
            <dt>Contato</dt>
            <dd>{anuncio.contatoPublico ? "mediado pelo sistema" : "não exposto"}</dd>
          </div>
        </dl>
        {anuncio.beneficiosPublicos.length > 0 ? (
          <section className="public-detail-section">
            <h3>Benefícios</h3>
            <ul className="public-benefit-list" aria-label="Benefícios públicos">
              {anuncio.beneficiosPublicos.map((beneficio) => (
                <li key={beneficio}>{beneficio}</li>
              ))}
            </ul>
          </section>
        ) : null}
      </article>
    </section>
  );
}

function formatarStatusPublico(status: string): string {
  switch (status) {
    case "conteudo_autorizado":
      return "Conteúdo disponível";
    case "aguardando_idade":
      return "Confirmação de idade necessária";
    case "confirmando":
      return "Confirmando idade";
    case "idade_negada":
      return "Idade não confirmada";
    case "indisponivel":
      return "Conteúdo indisponível";
    default:
      return "Perfil público";
  }
}

function formatarContatoPublico(pendencia: string | null): string {
  if (!pendencia) {
    return "Contato mediado pelo Tops do Job.";
  }
  return "Contato mediado pelo Tops do Job.";
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
