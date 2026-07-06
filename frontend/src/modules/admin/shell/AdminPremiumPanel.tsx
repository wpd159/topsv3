"use client";

import { useEffect, useState } from "react";

import {
  getAdminPremiumAnuncioStatus,
  getAdminPremiumBeneficios,
  getAdminPremiumConsistencia,
  getAdminPremiumVencendo
} from "../../../lib/api/adminReadonlyApi";
import type {
  AdminBeneficioAnuncioDto,
  AdminPremiumAnuncioStatusDto,
  AdminPremiumConsistenciaResumoDto,
  AdminPremiumVencendoResumoDto
} from "../../../lib/api/adminReadonlyTypes";
import { AdminPremiumWizard } from "../premium/AdminPremiumWizard";
import { formatAdminText, formatAdminValue, formatAdminValues } from "./adminDisplay";
import { AdminShell } from "./AdminShell";

const ANUNCIO_PREMIUM_SINTETICO_ID = "00000000-0000-4000-8000-000000000501";

type PremiumState = {
  status: AdminPremiumAnuncioStatusDto | null;
  beneficios: readonly AdminBeneficioAnuncioDto[];
  consistencia: AdminPremiumConsistenciaResumoDto | null;
  vencendo: AdminPremiumVencendoResumoDto | null;
  mensagem: string;
};

export function AdminPremiumPanel() {
  const [data, setData] = useState<PremiumState>({
    status: null,
    beneficios: [],
    consistencia: null,
    vencendo: null,
    mensagem: "consultando beneficios locais"
  });

  useEffect(() => {
    void loadPremium();
  }, []);

  async function loadPremium() {
    const [status, beneficios, consistencia, vencendo] = await Promise.all([
      getAdminPremiumAnuncioStatus(ANUNCIO_PREMIUM_SINTETICO_ID),
      getAdminPremiumBeneficios(ANUNCIO_PREMIUM_SINTETICO_ID),
      getAdminPremiumConsistencia(),
      getAdminPremiumVencendo()
    ]);
    setData({
      status: status.ok ? status.data : null,
      beneficios: beneficios.ok ? beneficios.data : [],
      consistencia: consistencia.ok ? consistencia.data : null,
      vencendo: vencendo.ok ? vencendo.data : null,
      mensagem: status.ok ? "premium local somente leitura carregado" : "premium exige sessao/permissao admin"
    });
  }

  return (
    <AdminShell title="Premium">
      <AdminPremiumWizard />
      <section className="admin-panel" aria-label="Premium e beneficios locais">
        <h2>Premium local</h2>
        <p>{data.mensagem}</p>
        <dl className="health-grid compact">
          <ReadonlyMetric label="Premium ativo" value={data.status?.premiumAtivo ? "sim" : data.status ? "nao" : "sem sessao"} />
          <ReadonlyMetric label="Beneficios ativos" value={data.status?.beneficiosAtivos} />
          <ReadonlyMetric label="Vencendo" value={data.status?.beneficiosVencendo} />
          <ReadonlyMetric label="Inconsistencias" value={data.consistencia?.total} />
          <ReadonlyMetric label="Compra real" value={data.status?.compraOuAtivacaoRealDisponivel ? "disponivel" : "bloqueada"} />
          <ReadonlyMetric label="Limite gratuito" value={data.status?.gratuitoLimitadoPorContato ? "existe" : "nao existe"} />
        </dl>
        <div className="admin-readonly-columns">
          <div className="admin-readonly-list">
            <h3>Beneficios do anuncio sintetico</h3>
            {data.beneficios.length ? (
              <ul>
                {data.beneficios.map((item) => (
                  <li key={item.id}>
                    <span>{formatAdminValue(item.beneficioCodigo, "beneficio local")}</span>
                    <small>{`${formatAdminValue(item.statusCalculado, "status")} / ${formatAdminValue(item.grupoStatus, "sem grupo")}`}</small>
                    <small>{formatAdminValues(item.codigosConsistencia, "premium ok")}</small>
                  </li>
                ))}
              </ul>
            ) : (
              <p>sem beneficio visivel para esta sessao</p>
            )}
          </div>
          <div className="admin-readonly-list">
            <h3>Consistencia</h3>
            {data.consistencia?.itens.length ? (
              <ul>
                {data.consistencia.itens.slice(0, 5).map((item) => (
                  <li key={`${item.codigo}-${item.ativacaoId ?? item.grupoId ?? item.slug}`}>
                    <span>{formatAdminValue(item.codigo)}</span>
                    <small>{item.slug ?? "sem slug publico"}</small>
                    <small>{formatAdminText(item.mensagem)}</small>
                  </li>
                ))}
              </ul>
            ) : (
              <p>sem inconsistencia local visivel</p>
            )}
          </div>
        </div>
        <div className="admin-readonly-list">
          <h3>Vencendo</h3>
          {data.vencendo?.itens.length ? (
            <ul>
              {data.vencendo.itens.slice(0, 5).map((item) => (
                <li key={item.ativacaoId}>
                  <span>{formatAdminValue(item.beneficioCodigo, "beneficio local")}</span>
                  <small>{`${item.diasRestantes} dias restantes`}</small>
                  <small>{item.slug ?? "sem slug publico"}</small>
                </li>
              ))}
            </ul>
          ) : (
            <p>sem beneficio vencendo na janela local</p>
          )}
        </div>
        <div className="admin-notice">
          Somente leitura local. Sem compra, ativacao real, ajuste financeiro, Pix, Efi, checkout, webhook, credito real
          ou limite comercial de WhatsApp/clique/contato no gratuito.
        </div>
      </section>
    </AdminShell>
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
