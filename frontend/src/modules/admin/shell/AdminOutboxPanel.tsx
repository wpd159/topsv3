"use client";

import { useEffect, useState } from "react";

import { getAdminMe } from "../../../lib/api/adminAuthApi";
import {
  getAdminOutbox,
  getAdminOutboxDetalhe,
  getAdminOutboxPreview,
  simularAdminOutboxProcessamentoLocal
} from "../../../lib/api/adminReadonlyApi";
import type {
  AdminOutboxDetalheDto,
  AdminOutboxListaItemDto,
  AdminOutboxPreviewRenderizadaDto,
  AdminPaginaDto
} from "../../../lib/api/adminReadonlyTypes";
import { formatAdminText, formatAdminValue } from "./adminDisplay";

type OutboxState = {
  pagina: AdminPaginaDto<AdminOutboxListaItemDto> | null;
  detalhe: AdminOutboxDetalheDto | null;
  preview: AdminOutboxPreviewRenderizadaDto | null;
  status: string;
  previewStatus: string;
  podeSimular: boolean;
  simulando: boolean;
  simulacaoStatus: string;
};

export function AdminOutboxPanel() {
  const [data, setData] = useState<OutboxState>({
    pagina: null,
    detalhe: null,
    preview: null,
    status: "consultando outbox local",
    previewStatus: "previa local nao carregada",
    podeSimular: false,
    simulando: false,
    simulacaoStatus: "simulacao local, sem envio externo"
  });

  useEffect(() => {
    void loadOutbox();
  }, []);

  async function loadOutbox(statusOverride?: string) {
    const me = await getAdminMe();
    const pagina = await getAdminOutbox();
    const first = pagina.ok ? pagina.data.itens[0] : null;
    const detalhe = first ? await getAdminOutboxDetalhe(first.id) : null;
    setData({
      pagina: pagina.ok ? pagina.data : null,
      detalhe: detalhe && detalhe.ok ? detalhe.data : null,
      preview: null,
      status: statusOverride ?? (pagina.ok ? "outbox local carregado para simulacao segura" : "outbox exige sessao/permissao"),
      previewStatus: "previa local aguardando selecao",
      podeSimular: me.ok && me.data.papeis.includes("ADMIN"),
      simulando: false,
      simulacaoStatus: "simulacao local, sem envio externo"
    });
  }

  async function carregarPreview(id: string) {
    setData((current) => ({
      ...current,
      previewStatus: "renderizando previa local sanitizada"
    }));
    const [detalhe, preview] = await Promise.all([getAdminOutboxDetalhe(id), getAdminOutboxPreview(id)]);
    setData((current) => ({
      ...current,
      detalhe: detalhe.ok ? detalhe.data : current.detalhe,
      preview: preview.ok ? preview.data : null,
      previewStatus: preview.ok
        ? "previa local, nenhuma comunicacao foi enviada"
        : "previa indisponivel para esta sessao/permissao"
    }));
  }

  async function simularProcessamentoLocal() {
    if (!data.detalhe || data.detalhe.status !== "PENDENTE") {
      return;
    }
    setData((current) => ({
      ...current,
      simulando: true,
      simulacaoStatus: "simulando processamento local sem envio externo"
    }));
    const response = await simularAdminOutboxProcessamentoLocal(data.detalhe.id, {
      observacao: "simulacao local acionada pelo painel admin",
      requestIdCliente: "reservado-sem-idempotencia-real"
    });
    if (response.ok) {
      await loadOutbox(response.data.mensagem);
      return;
    }
    setData((current) => ({
      ...current,
      simulando: false,
      simulacaoStatus: "simulacao local nao executada"
    }));
  }

  return (
    <section className="admin-panel" aria-label="Outbox administrativo local">
      <h2>Outbox local</h2>
      <p>{data.status}</p>
      <div className="admin-readonly-columns">
        <div className="admin-readonly-list">
          <h3>Pendencias</h3>
          {data.pagina?.itens.length ? (
            <ul>
              {data.pagina.itens.map((item) => (
                <li key={item.id}>
                  <span>{formatAdminValue(item.tipoEvento, "evento local")}</span>
                  <small>{`${formatAdminValue(item.status, "status")} / ${formatAdminValue(item.entidadeTipo, "entidade")}`}</small>
                  <small>{formatAdminText(item.resumoSanitizado, "resumo sanitizado indisponivel")}</small>
                  <button type="button" className="local-action" onClick={() => void carregarPreview(item.id)}>
                    Ver previa
                  </button>
                </li>
              ))}
            </ul>
          ) : (
            <p>sem pendencias visiveis</p>
          )}
        </div>
        <div className="admin-readonly-list">
          <h3>Previa</h3>
          <ul>
            <li>
              <span>
                {formatAdminText(data.preview?.assuntoSanitizado ?? data.detalhe?.previa?.assuntoSanitizado, "sem previa selecionada")}
              </span>
              <small>{formatAdminText(data.preview?.canalPrevisto ?? data.detalhe?.previa?.destinoLogicoSanitizado, "canal local")}</small>
              <small>
                {formatAdminText(
                  data.preview?.corpoSanitizado ?? data.detalhe?.previa?.corpoSanitizado,
                  "previa local, nenhuma comunicacao foi enviada"
                )}
              </small>
              <small>{data.previewStatus}</small>
              <small>
                {data.preview?.camposMascarados.length
                  ? `campos mascarados: ${data.preview.camposMascarados.map((item) => formatAdminValue(item)).join(", ")}`
                  : "campos mascarados aparecem apenas quando detectados"}
              </small>
            </li>
          </ul>
          {data.podeSimular ? (
            <button
              type="button"
              className="local-action"
              disabled={!data.detalhe || data.detalhe.status !== "PENDENTE" || data.simulando}
              onClick={() => void simularProcessamentoLocal()}
            >
              Simular processamento local
            </button>
          ) : null}
          <small>{data.simulacaoStatus}</small>
        </div>
      </div>
      <dl className="health-grid compact">
        <ReadonlyMetric label="Itens" value={data.pagina?.totalElements} />
        <ReadonlyMetric label="Detalhe" value={formatAdminValue(data.detalhe?.status, "sem permissao")} />
        <ReadonlyMetric label="Somente leitura" value={data.detalhe?.somenteLeitura ? "sim" : "sem detalhe"} />
        <ReadonlyMetric label="Somente previa" value={data.preview?.somentePreview ? "sim" : "sem previa"} />
        <ReadonlyMetric
          label="Envio externo"
          value={data.preview?.envioExternoExecutado || data.detalhe?.envioExternoExecutado ? "executado" : "nao executado"}
        />
      </dl>
      <div className="admin-notice">
        Nenhuma comunicacao real enviada. Sem envio, reenvio, worker, scheduler, upload, pagamento, credito ou Pix.
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
