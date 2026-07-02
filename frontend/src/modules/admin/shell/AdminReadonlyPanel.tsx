"use client";

import { useEffect, useState } from "react";

import {
  getAdminAnunciosResumo,
  getAdminMetricasResumo,
  getAdminMidiasResumo,
  getAdminModeracaoResumo,
  getAdminSistemaStatus,
  getAdminVisaoGeral
} from "../../../lib/api/adminReadonlyApi";
import type {
  AdminResumoAnunciosDto,
  AdminResumoMetricasDto,
  AdminResumoMidiasDto,
  AdminResumoModeracaoDto,
  AdminStatusSistemaDto,
  AdminVisaoGeralDto
} from "../../../lib/api/adminReadonlyTypes";

type ReadonlyState = "carregando" | "disponivel" | "indisponivel";

type ReadonlyData = {
  visaoGeral: AdminVisaoGeralDto | null;
  anuncios: AdminResumoAnunciosDto | null;
  moderacao: AdminResumoModeracaoDto | null;
  midias: AdminResumoMidiasDto | null;
  metricas: AdminResumoMetricasDto | null;
  sistema: AdminStatusSistemaDto | null;
};

export function AdminReadonlyPanel() {
  const [state, setState] = useState<ReadonlyState>("carregando");
  const [message, setMessage] = useState("consultando resumos administrativos locais");
  const [data, setData] = useState<ReadonlyData>({
    visaoGeral: null,
    anuncios: null,
    moderacao: null,
    midias: null,
    metricas: null,
    sistema: null
  });

  useEffect(() => {
    void loadReadonlyData();
  }, []);

  async function loadReadonlyData() {
    setState("carregando");
    const [visaoGeral, anuncios, moderacao, midias, metricas, sistema] = await Promise.all([
      getAdminVisaoGeral(),
      getAdminAnunciosResumo(),
      getAdminModeracaoResumo(),
      getAdminMidiasResumo(),
      getAdminMetricasResumo(),
      getAdminSistemaStatus()
    ]);

    setData({
      visaoGeral: visaoGeral.ok ? visaoGeral.data : null,
      anuncios: anuncios.ok ? anuncios.data : null,
      moderacao: moderacao.ok ? moderacao.data : null,
      midias: midias.ok ? midias.data : null,
      metricas: metricas.ok ? metricas.data : null,
      sistema: sistema.ok ? sistema.data : null
    });

    const available = [visaoGeral, anuncios, moderacao, midias, metricas, sistema].filter((item) => item.ok).length;
    setState(available > 0 ? "disponivel" : "indisponivel");
    setMessage(available > 0 ? "resumos locais somente leitura carregados" : "resumos exigem sessao/permissao admin");
  }

  return (
    <section className="admin-panel" aria-label="Resumos administrativos locais">
      <h2>Resumos locais</h2>
      <p>{message}</p>
      <dl className="health-grid compact">
        <ReadonlyMetric label="Estado" value={state} />
        <ReadonlyMetric label="Anuncios publicados" value={data.anuncios?.publicados} />
        <ReadonlyMetric label="Revisoes abertas" value={data.moderacao?.revisoesAbertas} />
        <ReadonlyMetric label="Midias pendentes" value={data.midias?.midiasPendentes} />
        <ReadonlyMetric label="Visualizacoes" value={data.metricas?.visualizacoesTotal} />
        <ReadonlyMetric label="Sistema local" value={data.sistema?.local ? "sim" : data.sistema ? "nao" : "sem sessao"} />
      </dl>
      {data.visaoGeral?.contadores.length ? (
        <ul className="admin-permission-list" aria-label="Contadores administrativos locais">
          {data.visaoGeral.contadores.map((contador) => (
            <li key={contador.codigo}>
              <span>{contador.rotulo}</span>
              <small>{contador.total}</small>
            </li>
          ))}
        </ul>
      ) : null}
      <div className="admin-notice">
        Somente leitura local. Nenhuma acao de aprovacao, rejeicao, exclusao, pagamento, credito ou upload esta
        disponivel.
      </div>
    </section>
  );
}

function ReadonlyMetric({ label, value }: { label: string; value: number | string | undefined }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value ?? "sem permissao"}</dd>
    </div>
  );
}
