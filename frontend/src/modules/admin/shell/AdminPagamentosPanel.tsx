"use client";

import { useEffect, useState } from "react";

import {
  getAdminPagamentoConsistencia,
  getAdminPagamentoDetalhe,
  getAdminPagamentoInconsistencias,
  getAdminPagamentos
} from "../../../lib/api/adminReadonlyApi";
import type {
  AdminPagamentoConsistenciaResumoDto,
  AdminPagamentoDetalheDto,
  AdminPagamentoListaItemDto,
  AdminPagamentoPaginaDto
} from "../../../lib/api/adminReadonlyTypes";
import { AdminShell } from "./AdminShell";

const PAGAMENTO_SINTETICO_ID = "00000000-0000-4000-8000-000000000721";

type PagamentosState = {
  lista: AdminPagamentoPaginaDto<AdminPagamentoListaItemDto> | null;
  detalhe: AdminPagamentoDetalheDto | null;
  consistencia: AdminPagamentoConsistenciaResumoDto | null;
  inconsistencias: AdminPagamentoConsistenciaResumoDto | null;
  mensagem: string;
};

export function AdminPagamentosPanel() {
  const [data, setData] = useState<PagamentosState>({
    lista: null,
    detalhe: null,
    consistencia: null,
    inconsistencias: null,
    mensagem: "consultando pagamentos locais"
  });

  useEffect(() => {
    void loadPagamentos();
  }, []);

  async function loadPagamentos() {
    const [lista, detalhe, consistencia, inconsistencias] = await Promise.all([
      getAdminPagamentos(),
      getAdminPagamentoDetalhe(PAGAMENTO_SINTETICO_ID),
      getAdminPagamentoConsistencia(),
      getAdminPagamentoInconsistencias()
    ]);
    setData({
      lista: lista.ok ? lista.data : null,
      detalhe: detalhe.ok ? detalhe.data : null,
      consistencia: consistencia.ok ? consistencia.data : null,
      inconsistencias: inconsistencias.ok ? inconsistencias.data : null,
      mensagem: lista.ok ? "pagamentos locais somente leitura carregados" : "pagamentos exigem sessao ADMIN"
    });
  }

  return (
    <AdminShell title="Financeiro">
      <section className="admin-panel" aria-label="Pagamentos locais">
        <h2>Pagamentos locais</h2>
        <p>{data.mensagem}</p>
        <dl className="health-grid compact">
          <ReadonlyMetric label="Pagamentos" value={data.lista?.total} />
          <ReadonlyMetric label="Inconsistencias" value={data.inconsistencias?.total} />
          <ReadonlyMetric label="Eventos" value={data.detalhe?.eventosSanitizadosTotal} />
          <ReadonlyMetric label="Webhooks" value={data.detalhe?.webhooksSanitizadosTotal} />
          <ReadonlyMetric label="Creditos" value={data.detalhe?.quantidadeCreditos} />
          <ReadonlyMetric label="Provedor" value={data.detalhe?.provedorClassificado} />
        </dl>
        <div className="admin-readonly-columns">
          <div className="admin-readonly-list">
            <h3>Ultimos registros</h3>
            {data.lista?.itens.length ? (
              <ul>
                {data.lista.itens.map((item) => (
                  <li key={item.id}>
                    <span>{`${item.provedorClassificado ?? "DESCONHECIDO"} / ${item.statusInterno ?? "STATUS"}`}</span>
                    <small>{`${item.quantidadeCreditos ?? 0} creditos, ${item.moeda ?? "BRL"}`}</small>
                    <small>{item.creditoVinculado ? "credito vinculado" : "sem credito vinculado"}</small>
                  </li>
                ))}
              </ul>
            ) : (
              <p>sem pagamento visivel para esta sessao</p>
            )}
          </div>
          <div className="admin-readonly-list">
            <h3>Consistencia</h3>
            {data.consistencia?.itens.length ? (
              <ul>
                {data.consistencia.itens.slice(0, 8).map((item) => (
                  <li key={`${item.codigo}-${item.pagamentoId ?? item.movimentoCreditoId ?? "geral"}`}>
                    <span>{item.codigo}</span>
                    <small>{item.mensagem}</small>
                    <small>{item.severidade}</small>
                  </li>
                ))}
              </ul>
            ) : (
              <p>sem inconsistencia local visivel</p>
            )}
          </div>
        </div>
        <div className="admin-notice">
          Somente leitura local. Sem cobranca, checkout, Pix/Efi real, webhook real, conciliacao real, credito,
          estorno, worker, scheduler ou mutation financeira.
        </div>
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
