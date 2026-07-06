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
    mensagem: "consultando benefícios"
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
      mensagem: status.ok ? "premium somente leitura carregado" : "premium exige sessão/permissão admin"
    });
  }

  return (
    <AdminShell title="Premium">
      <AdminPremiumWizard />
      <section className="admin-panel" aria-label="Premium e benefícios">
        <h2>Premium</h2>
        <p>{data.mensagem}</p>
        <dl className="health-grid compact">
          <ReadonlyMetric label="Premium ativo" value={data.status?.premiumAtivo ? "sim" : data.status ? "não" : "sem sessão"} />
          <ReadonlyMetric label="Benefícios ativos" value={data.status?.beneficiosAtivos} />
          <ReadonlyMetric label="Vencendo" value={data.status?.beneficiosVencendo} />
          <ReadonlyMetric label="Inconsistências" value={data.consistencia?.total} />
          <ReadonlyMetric label="Ativação" value={data.status?.compraOuAtivacaoRealDisponivel ? "disponível" : "indisponível"} />
          <ReadonlyMetric label="Limite gratuito" value={data.status?.gratuitoLimitadoPorContato ? "existe" : "não existe"} />
        </dl>
        <div className="admin-readonly-columns">
          <div className="admin-readonly-list">
            <h3>Benefícios do anúncio</h3>
            {data.beneficios.length ? (
              <ul>
                {data.beneficios.map((item) => (
                  <li key={item.id}>
                    <span>{formatAdminValue(item.beneficioCodigo, "benefício")}</span>
                    <small>{`${formatAdminValue(item.statusCalculado, "status")} / ${formatAdminValue(item.grupoStatus, "sem grupo")}`}</small>
                    <small>{formatAdminValues(item.codigosConsistencia, "premium ok")}</small>
                  </li>
                ))}
              </ul>
            ) : (
              <p>sem benefício visível para esta sessão</p>
            )}
          </div>
          <div className="admin-readonly-list">
            <h3>Consistência</h3>
            {data.consistencia?.itens.length ? (
              <ul>
                {data.consistencia.itens.slice(0, 5).map((item) => (
                  <li key={`${item.codigo}-${item.ativacaoId ?? item.grupoId ?? item.slug}`}>
                    <span>{formatAdminValue(item.codigo)}</span>
                    <small>{item.slug ? "referência de demonstração" : "sem referência pública"}</small>
                    <small>{formatAdminText(item.mensagem)}</small>
                  </li>
                ))}
              </ul>
            ) : (
              <p>sem inconsistência visível</p>
            )}
          </div>
        </div>
        <div className="admin-readonly-list">
          <h3>Vencendo</h3>
          {data.vencendo?.itens.length ? (
            <ul>
              {data.vencendo.itens.slice(0, 5).map((item) => (
                <li key={item.ativacaoId}>
                  <span>{formatAdminValue(item.beneficioCodigo, "benefício")}</span>
                  <small>{`${item.diasRestantes} dias restantes`}</small>
                  <small>{item.slug ? "referência de demonstração" : "sem referência pública"}</small>
                </li>
              ))}
            </ul>
          ) : (
            <p>sem benefício vencendo na janela</p>
          )}
        </div>
        <div className="admin-notice">
          Somente leitura. Sem compra, ativação, ajuste financeiro, Pix, Efi, checkout, webhook, crédito
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
      <dd>{value ?? "sem permissão"}</dd>
    </div>
  );
}
