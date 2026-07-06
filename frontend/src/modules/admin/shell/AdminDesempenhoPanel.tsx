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
    mensagem: "consultando prova de resultado"
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
      mensagem: anuncio.ok ? "prova de resultado carregada" : "desempenho exige sessão/permissão admin"
    });
  }

  return (
    <AdminShell title="Desempenho">
      <section className="admin-panel" aria-label="Prova de resultado">
        <h2>Prova de resultado</h2>
        <p>{data.mensagem}</p>
        <dl className="health-grid compact">
          <ReadonlyMetric label="Visualizações" value={data.anuncio?.visualizacoesTotal} />
          <ReadonlyMetric label="Cliques WhatsApp" value={data.anuncio?.cliquesWhatsappTotal} />
          <ReadonlyMetric label="Taxa clique/view" value={formatPercent(data.anuncio?.taxaCliqueView)} />
          <ReadonlyMetric label="Anúncios com métricas" value={data.resumo?.anunciosComMetricas} />
          <ReadonlyMetric label="Visualizações Premium" value={data.anuncio?.comparativoPremium.visualizacoesComPremium} />
          <ReadonlyMetric label="Visualizações orgânicas" value={data.anuncio?.comparativoPremium.visualizacoesOrganicas} />
        </dl>
        <div className="admin-readonly-columns">
          <div className="admin-readonly-list">
            <h3>Diário</h3>
            {data.diario.length ? (
              <ul>
                {data.diario.slice(0, 6).map((item) => (
                  <li key={item.dataReferencia ?? "sem-data"}>
                    <span>{item.dataReferencia ?? "sem data"}</span>
                    <small>{`${item.visualizacoes} views, ${item.cliquesWhatsapp} cliques`}</small>
                    <small>{item.premiumAtivo ? "período com Premium" : "período orgânico"}</small>
                  </li>
                ))}
              </ul>
            ) : (
              <p>sem dados diários para esta sessão</p>
            )}
          </div>
          <div className="admin-readonly-list">
            <h3>Origens agregadas</h3>
            {data.origens.length ? (
              <ul>
                {data.origens.slice(0, 6).map((item) => (
                  <li key={`${item.uf ?? "uf"}-${item.cidade ?? "cidade"}-${item.bairro ?? "bairro"}`}>
                    <span>{[item.uf, item.cidade, item.bairro].filter(Boolean).join(" / ") || "origem agregada"}</span>
                    <small>{`${item.visualizacoes} views, ${item.cliquesWhatsapp} cliques`}</small>
                    <small>{formatPercent(item.taxaCliqueView)}</small>
                  </li>
                ))}
              </ul>
            ) : (
              <p>sem origem agregada visível</p>
            )}
          </div>
        </div>
        <div className="admin-readonly-list">
          <h3>Orgânico e Premium</h3>
          <ul>
            <li>
              <span>Orgânico</span>
              <small>{`${data.anuncio?.comparativoPremium.visualizacoesOrganicas ?? 0} views, ${data.anuncio?.comparativoPremium.cliquesOrganicos ?? 0} cliques`}</small>
            </li>
            <li>
              <span>Premium</span>
              <small>{`${data.anuncio?.comparativoPremium.visualizacoesComPremium ?? 0} views, ${data.anuncio?.comparativoPremium.cliquesComPremium ?? 0} cliques`}</small>
              <small>{data.anuncio?.comparativoPremium.beneficiosExposicaoAtivos.join(", ") || "sem benefício ativo"}</small>
            </li>
          </ul>
        </div>
        <div className="admin-readonly-list">
          <h3>Fallback sem dados</h3>
          <p>
            {data.vazio && data.vazio.visualizacoesTotal === 0
              ? "anúncio sem métricas retorna estado vazio estável"
              : "fallback indisponível para esta sessão"}
          </p>
        </div>
        <div className="admin-notice">
          Somente leitura. Sem tracking externo, pixel, exportação de dado, promessa de contratação, limite do
          gratuito, pagamento, crédito, Pix/Efi, compra ou impulsionamento.
        </div>
        <p>{data.anuncio?.avisoResultado ?? data.resumo?.mensagemSegura}</p>
        <p>{data.anunciante ? `${data.anunciante.anunciosTotal} anúncios do anunciante` : "painel da anunciante fica para fase futura"}</p>
      </section>
    </AdminShell>
  );
}

function ReadonlyMetric({ label, value }: { label: string; value: number | string | undefined | null }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value ?? "sem permissão"}</dd>
    </div>
  );
}

function formatPercent(value: number | undefined | null): string {
  if (typeof value !== "number") {
    return "sem permissão";
  }
  return `${(value * 100).toFixed(1)}%`;
}
