"use client";

import { useEffect, useState } from "react";

import {
  getAdminCreditoConsistencia,
  getAdminCreditoInconsistencias,
  getAdminCreditoMovimentos,
  getAdminCreditoSaldo
} from "../../../lib/api/adminReadonlyApi";
import type {
  AdminCreditoConsistenciaResumoDto,
  AdminCreditoMovimentoDto,
  AdminCreditoPaginaDto,
  AdminCreditoSaldoDto
} from "../../../lib/api/adminReadonlyTypes";
import { AdminShell } from "./AdminShell";

const USUARIO_CREDITO_SINTETICO_ID = "00000000-0000-4000-8000-000000000101";

type CreditosState = {
  saldo: AdminCreditoSaldoDto | null;
  movimentos: AdminCreditoPaginaDto<AdminCreditoMovimentoDto> | null;
  consistencia: AdminCreditoConsistenciaResumoDto | null;
  inconsistencias: AdminCreditoConsistenciaResumoDto | null;
  mensagem: string;
};

export function AdminCreditosPanel() {
  const [data, setData] = useState<CreditosState>({
    saldo: null,
    movimentos: null,
    consistencia: null,
    inconsistencias: null,
    mensagem: "consultando ledger local"
  });

  useEffect(() => {
    void loadCreditos();
  }, []);

  async function loadCreditos() {
    const [saldo, movimentos, consistencia, inconsistencias] = await Promise.all([
      getAdminCreditoSaldo(USUARIO_CREDITO_SINTETICO_ID),
      getAdminCreditoMovimentos(USUARIO_CREDITO_SINTETICO_ID),
      getAdminCreditoConsistencia(),
      getAdminCreditoInconsistencias()
    ]);
    setData({
      saldo: saldo.ok ? saldo.data : null,
      movimentos: movimentos.ok ? movimentos.data : null,
      consistencia: consistencia.ok ? consistencia.data : null,
      inconsistencias: inconsistencias.ok ? inconsistencias.data : null,
      mensagem: saldo.ok ? "creditos locais somente leitura carregados" : "creditos exigem sessao ADMIN"
    });
  }

  return (
    <AdminShell title="Creditos">
      <section className="admin-panel" aria-label="Creditos e ledger locais">
        <h2>Creditos locais</h2>
        <p>{data.mensagem}</p>
        <dl className="health-grid compact">
          <ReadonlyMetric label="Saldo projetado" value={data.saldo?.saldoProjetado} />
          <ReadonlyMetric label="Saldo calculado" value={data.saldo?.saldoCalculadoMovimentos} />
          <ReadonlyMetric label="Movimentos" value={data.saldo?.totalMovimentos} />
          <ReadonlyMetric label="Entradas" value={data.saldo?.totalEntradas} />
          <ReadonlyMetric label="Saidas" value={data.saldo?.totalSaidas} />
          <ReadonlyMetric label="Inconsistencias" value={data.inconsistencias?.total} />
        </dl>
        <div className="admin-readonly-columns">
          <div className="admin-readonly-list">
            <h3>Ledger</h3>
            {data.movimentos?.itens.length ? (
              <ul>
                {data.movimentos.itens.map((item) => (
                  <li key={item.id}>
                    <span>{`${item.tipo ?? "MOVIMENTO"} / ${item.direcao ?? "DIRECAO"}`}</span>
                    <small>{`${item.quantidade ?? 0} creditos, ${item.saldoAntes ?? 0} -> ${item.saldoDepois ?? 0}`}</small>
                    <small>{`${item.origem ?? "origem local"} / ${item.referenciaTipo ?? "sem referencia"}`}</small>
                  </li>
                ))}
              </ul>
            ) : (
              <p>sem movimento visivel para esta sessao</p>
            )}
          </div>
          <div className="admin-readonly-list">
            <h3>Consistencia</h3>
            {data.consistencia?.itens.length ? (
              <ul>
                {data.consistencia.itens.slice(0, 8).map((item) => (
                  <li key={`${item.codigo}-${item.movimentoId ?? item.pagamentoId ?? item.usuarioId}`}>
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
          Somente leitura local. Sem compra, checkout, ajuste, estorno, conciliacao real, Pix, Efi, worker,
          scheduler, credito real ou alteracao de saldo.
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
