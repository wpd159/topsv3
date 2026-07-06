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
import { formatAdminText, formatAdminValue } from "./adminDisplay";

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
    mensagem: "consultando pagamentos"
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
      mensagem: lista.ok ? "pagamentos somente leitura carregados" : "pagamentos exigem sessão ADMIN"
    });
  }

  return (
    <AdminShell title="Financeiro">
      <section className="admin-panel" aria-label="Pagamentos">
        <h2>Pagamentos</h2>
        <p>{data.mensagem}</p>
        <dl className="health-grid compact">
          <ReadonlyMetric label="Pagamentos" value={data.lista?.total} />
          <ReadonlyMetric label="Inconsistências" value={data.inconsistencias?.total} />
          <ReadonlyMetric label="Eventos" value={data.detalhe?.eventosSanitizadosTotal} />
          <ReadonlyMetric label="Webhooks" value={data.detalhe?.webhooksSanitizadosTotal} />
          <ReadonlyMetric label="Créditos" value={data.detalhe?.quantidadeCreditos} />
          <ReadonlyMetric label="Provedor" value={formatAdminValue(data.detalhe?.provedorClassificado, "sem provedor")} />
        </dl>
        <div className="admin-readonly-columns">
          <div className="admin-readonly-list">
            <h3>Últimos registros</h3>
            {data.lista?.itens.length ? (
              <ul>
                {data.lista.itens.map((item) => (
                  <li key={item.id}>
                    <span>{`${formatAdminValue(item.provedorClassificado, "desconhecido")} / ${formatAdminValue(item.statusInterno, "status")}`}</span>
                    <small>{`${item.quantidadeCreditos ?? 0} créditos, ${item.moeda ?? "BRL"}`}</small>
                    <small>{item.creditoVinculado ? "crédito vinculado" : "sem crédito vinculado"}</small>
                  </li>
                ))}
              </ul>
            ) : (
              <p>sem pagamento visível para esta sessão</p>
            )}
          </div>
          <div className="admin-readonly-list">
            <h3>Consistência</h3>
            {data.consistencia?.itens.length ? (
              <ul>
                {data.consistencia.itens.slice(0, 8).map((item) => (
                  <li key={`${item.codigo}-${item.pagamentoId ?? item.movimentoCreditoId ?? "geral"}`}>
                    <span>{formatAdminValue(item.codigo)}</span>
                    <small>{formatAdminText(item.mensagem)}</small>
                    <small>{formatAdminValue(item.severidade)}</small>
                  </li>
                ))}
              </ul>
            ) : (
              <p>sem inconsistência visível</p>
            )}
          </div>
        </div>
        <div className="admin-notice">
          Somente leitura. Sem cobrança, checkout, Pix/Efi, webhook, conciliação, crédito,
          estorno, worker, scheduler ou mutação financeira.
        </div>
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
