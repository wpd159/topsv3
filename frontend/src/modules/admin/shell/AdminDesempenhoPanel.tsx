"use client";

import { useEffect, useState } from "react";

import {
  getAdminDesempenhoAnunciante,
  getAdminDesempenhoAnuncio,
  getAdminDesempenhoDiario,
  getAdminDesempenhoOrigens,
  getAdminDesempenhoResumo
} from "../../../lib/api/adminReadonlyApi";
import type {
  AdminDesempenhoAnuncianteDto,
  AdminDesempenhoAnuncioDto,
  AdminDesempenhoDiarioDto,
  AdminDesempenhoOrigemDto,
  AdminDesempenhoResumoDto
} from "../../../lib/api/adminReadonlyTypes";
import { AdminShell } from "./AdminShell";

const ANUNCIO_DESEMPENHO_ID = "00000000-0000-4000-8000-000000000501";
const ANUNCIO_SEM_METRICA_ID = "00000000-0000-4000-8000-000000000511";
const USUARIO_ANUNCIANTE_ID = "00000000-0000-4000-8000-000000000101";

type DesempenhoState = {
  anuncio: AdminDesempenhoAnuncioDto | null;
  diario: readonly AdminDesempenhoDiarioDto[];
  origens: readonly AdminDesempenhoOrigemDto[];
  anunciante: AdminDesempenhoAnuncianteDto | null;
  resumo: AdminDesempenhoResumoDto | null;
  vazio: AdminDesempenhoAnuncioDto | null;
  mensagem: string;
};

export function AdminDesempenhoPanel() {
  const [data, setData] = useState<DesempenhoState>({
    anuncio: null,
    diario: [],
    origens: [],
    anunciante: null,
    resumo: null,
    vazio: null,
    mensagem: "consultando prova de resultado local"
  });

  useEffect(() => {
    void loadDesempenho();
  }, []);

  async function loadDesempenho() {
    const [anuncio, diario, origens, anunciante, resumo, vazio] = await Promise.all([
      getAdminDesempenhoAnuncio(ANUNCIO_DESEMPENHO_ID),
      getAdminDesempenhoDiario(ANUNCIO_DESEMPENHO_ID),
      getAdminDesempenhoOrigens(ANUNCIO_DESEMPENHO_ID),
      getAdminDesempenhoAnunciante(USUARIO_ANUNCIANTE_ID),
      getAdminDesempenhoResumo(),
      getAdminDesempenhoAnuncio(ANUNCIO_SEM_METRICA_ID)
    ]);
    setData({
      anuncio: anuncio.ok ? anuncio.data : null,
      diario: diario.ok ? diario.data : [],
      origens: origens.ok ? origens.data : [],
      anunciante: anunciante.ok ? anunciante.data : null,
      resumo: resumo.ok ? resumo.data : null,
      vazio: vazio.ok ? vazio.data : null,
      mensagem: anuncio.ok ? "prova de resultado local carregada" : "desempenho exige sessao/permissao admin"
    });
  }

  return (
    <AdminShell title="Desempenho">
      <section className="admin-panel" aria-label="Prova de resultado local">
        <h2>Prova de resultado</h2>
        <p>{data.mensagem}</p>
        <dl className="health-grid compact">
          <ReadonlyMetric label="Visualizacoes" value={data.anuncio?.visualizacoesTotal} />
          <ReadonlyMetric label="Cliques WhatsApp" value={data.anuncio?.cliquesWhatsappTotal} />
          <ReadonlyMetric label="Taxa clique/view" value={formatPercent(data.anuncio?.taxaCliqueView)} />
          <ReadonlyMetric label="Anuncios com metricas" value={data.resumo?.anunciosComMetricas} />
          <ReadonlyMetric label="Premium views" value={data.anuncio?.comparativoPremium.visualizacoesComPremium} />
          <ReadonlyMetric label="Organico views" value={data.anuncio?.comparativoPremium.visualizacoesOrganicas} />
        </dl>
        <div className="admin-readonly-columns">
          <div className="admin-readonly-list">
            <h3>Diario</h3>
            {data.diario.length ? (
              <ul>
                {data.diario.slice(0, 6).map((item) => (
                  <li key={item.dataReferencia ?? "sem-data"}>
                    <span>{item.dataReferencia ?? "sem data"}</span>
                    <small>{`${item.visualizacoes} views, ${item.cliquesWhatsapp} cliques`}</small>
                    <small>{item.premiumAtivo ? "periodo com Premium" : "periodo organico"}</small>
                  </li>
                ))}
              </ul>
            ) : (
              <p>sem dados diarios para esta sessao</p>
            )}
          </div>
          <div className="admin-readonly-list">
            <h3>Origens agregadas</h3>
            {data.origens.length ? (
              <ul>
                {data.origens.slice(0, 6).map((item) => (
                  <li key={`${item.uf ?? "uf"}-${item.cidade ?? "cidade"}-${item.bairro ?? "bairro"}`}>
                    <span>{[item.uf, item.cidade, item.bairro].filter(Boolean).join(" / ") || "origem sintetica"}</span>
                    <small>{`${item.visualizacoes} views, ${item.cliquesWhatsapp} cliques`}</small>
                    <small>{formatPercent(item.taxaCliqueView)}</small>
                  </li>
                ))}
              </ul>
            ) : (
              <p>sem origem agregada visivel</p>
            )}
          </div>
        </div>
        <div className="admin-readonly-list">
          <h3>Organico e Premium</h3>
          <ul>
            <li>
              <span>Organico</span>
              <small>{`${data.anuncio?.comparativoPremium.visualizacoesOrganicas ?? 0} views, ${data.anuncio?.comparativoPremium.cliquesOrganicos ?? 0} cliques`}</small>
            </li>
            <li>
              <span>Premium</span>
              <small>{`${data.anuncio?.comparativoPremium.visualizacoesComPremium ?? 0} views, ${data.anuncio?.comparativoPremium.cliquesComPremium ?? 0} cliques`}</small>
              <small>{data.anuncio?.comparativoPremium.beneficiosExposicaoAtivos.join(", ") || "sem beneficio ativo"}</small>
            </li>
          </ul>
        </div>
        <div className="admin-readonly-list">
          <h3>Fallback sem dados</h3>
          <p>
            {data.vazio && data.vazio.visualizacoesTotal === 0
              ? "anuncio sintetico sem metricas retorna estado vazio estavel"
              : "fallback indisponivel para esta sessao"}
          </p>
        </div>
        <div className="admin-notice">
          Somente leitura local. Sem tracking externo, pixel, exportacao de dado real, promessa de contratacao, limite do
          gratuito, pagamento, credito, Pix/Efi, compra ou impulsionamento.
        </div>
        <p>{data.anuncio?.avisoResultado ?? data.resumo?.mensagemSegura}</p>
        <p>{data.anunciante ? `${data.anunciante.anunciosTotal} anuncios sinteticos do anunciante local` : "painel real da anunciante fica para fase futura"}</p>
      </section>
    </AdminShell>
  );
}

function ReadonlyMetric({ label, value }: { label: string; value: number | string | undefined | null }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value ?? "sem permissao"}</dd>
    </div>
  );
}

function formatPercent(value: number | undefined | null): string {
  if (typeof value !== "number") {
    return "sem permissao";
  }
  return `${(value * 100).toFixed(1)}%`;
}
