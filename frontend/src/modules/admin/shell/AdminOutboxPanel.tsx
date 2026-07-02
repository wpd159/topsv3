"use client";

import { useEffect, useState } from "react";

import { getAdminOutbox, getAdminOutboxDetalhe } from "../../../lib/api/adminReadonlyApi";
import type {
  AdminOutboxDetalheDto,
  AdminOutboxListaItemDto,
  AdminPaginaDto
} from "../../../lib/api/adminReadonlyTypes";

type OutboxState = {
  pagina: AdminPaginaDto<AdminOutboxListaItemDto> | null;
  detalhe: AdminOutboxDetalheDto | null;
  status: string;
};

export function AdminOutboxPanel() {
  const [data, setData] = useState<OutboxState>({
    pagina: null,
    detalhe: null,
    status: "consultando outbox local"
  });

  useEffect(() => {
    void loadOutbox();
  }, []);

  async function loadOutbox() {
    const pagina = await getAdminOutbox();
    const first = pagina.ok ? pagina.data.itens[0] : null;
    const detalhe = first ? await getAdminOutboxDetalhe(first.id) : null;
    setData({
      pagina: pagina.ok ? pagina.data : null,
      detalhe: detalhe && detalhe.ok ? detalhe.data : null,
      status: pagina.ok ? "outbox local somente leitura carregado" : "outbox exige sessao/permissao"
    });
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
                  <span>{item.tipoEvento ?? "evento local"}</span>
                  <small>{`${item.status ?? "status"} / ${item.entidadeTipo ?? "entidade"}`}</small>
                  <small>{item.resumoSanitizado ?? "resumo sanitizado indisponivel"}</small>
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
              <span>{data.detalhe?.previa?.assuntoSanitizado ?? "sem detalhe selecionado"}</span>
              <small>{data.detalhe?.previa?.destinoLogicoSanitizado ?? "destino logico sanitizado"}</small>
              <small>{data.detalhe?.previa?.corpoSanitizado ?? "nenhuma comunicacao real enviada"}</small>
            </li>
          </ul>
        </div>
      </div>
      <dl className="health-grid compact">
        <ReadonlyMetric label="Itens" value={data.pagina?.totalElements} />
        <ReadonlyMetric label="Detalhe" value={data.detalhe?.status ?? "sem permissao"} />
        <ReadonlyMetric label="Somente leitura" value={data.detalhe?.somenteLeitura ? "sim" : "sem detalhe"} />
        <ReadonlyMetric
          label="Envio externo"
          value={data.detalhe?.envioExternoExecutado ? "executado" : "nao executado"}
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
