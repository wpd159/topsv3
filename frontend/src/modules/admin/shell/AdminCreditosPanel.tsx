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
import { formatAdminText, formatAdminValue } from "./adminDisplay";

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
    mensagem: "consultando ledger"
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
      mensagem: saldo.ok ? "créditos somente leitura carregados" : "créditos exigem sessão ADMIN"
    });
  }

  return (
    <AdminShell title="Créditos">
      <section className="admin-panel" aria-label="Créditos e ledger">
        <h2>Créditos</h2>
        <p>{data.mensagem}</p>
        <dl className="health-grid compact">
          <ReadonlyMetric label="Saldo projetado" value={data.saldo?.saldoProjetado} />
          <ReadonlyMetric label="Saldo calculado" value={data.saldo?.saldoCalculadoMovimentos} />
          <ReadonlyMetric label="Movimentos" value={data.saldo?.totalMovimentos} />
          <ReadonlyMetric label="Entradas" value={data.saldo?.totalEntradas} />
          <ReadonlyMetric label="Saídas" value={data.saldo?.totalSaidas} />
          <ReadonlyMetric label="Inconsistências" value={data.inconsistencias?.total} />
        </dl>
        <div className="admin-readonly-columns">
          <div className="admin-readonly-list">
            <h3>Ledger</h3>
            {data.movimentos?.itens.length ? (
              <ul>
                {data.movimentos.itens.map((item) => (
                  <li key={item.id}>
                    <span>{`${formatAdminValue(item.tipo, "movimento")} / ${formatAdminValue(item.direcao, "direção")}`}</span>
                    <small>{`${item.quantidade ?? 0} créditos, ${item.saldoAntes ?? 0} -> ${item.saldoDepois ?? 0}`}</small>
                    <small>{`${formatAdminValue(item.origem, "origem")} / ${formatAdminValue(item.referenciaTipo, "sem referência")}`}</small>
                  </li>
                ))}
              </ul>
            ) : (
              <p>sem movimento visível para esta sessão</p>
            )}
          </div>
          <div className="admin-readonly-list">
            <h3>Consistência</h3>
            {data.consistencia?.itens.length ? (
              <ul>
                {data.consistencia.itens.slice(0, 8).map((item) => (
                  <li key={`${item.codigo}-${item.movimentoId ?? item.pagamentoId ?? item.usuarioId}`}>
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
          Somente leitura. Sem compra, checkout, ajuste, estorno, conciliação, Pix, Efi, worker,
          scheduler, crédito ou alteração de saldo.
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
