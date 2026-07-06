"use client";

import { useEffect, useState } from "react";

import {
  decidirAdminMidia,
  decidirAdminRevisao,
  getAdminAnuncioDetalhe,
  getAdminAnunciosDetalhados,
  getAdminMidiaDetalhe,
  getAdminMidiasDetalhadas,
  getAdminRevisaoDetalhe,
  getAdminRevisoesDetalhadas,
  remeterAdminAnuncioParaRevisao
} from "../../../lib/api/adminReadonlyApi";
import { getAdminMe, getAdminPermissions } from "../../../lib/api/adminAuthApi";
import type {
  AdminAnuncioDetalheDto,
  AdminAnuncioListaItemDto,
  AdminMidiaDetalheDto,
  AdminMidiaListaItemDto,
  AdminPaginaDto,
  AdminRevisaoDetalheDto,
  AdminRevisaoListaItemDto
} from "../../../lib/api/adminReadonlyTypes";
import { formatAdminText, formatAdminValue } from "./adminDisplay";

type DetailedState = {
  anuncios: AdminPaginaDto<AdminAnuncioListaItemDto> | null;
  anuncioDetalhe: AdminAnuncioDetalheDto | null;
  midias: AdminPaginaDto<AdminMidiaListaItemDto> | null;
  midiaDetalhe: AdminMidiaDetalheDto | null;
  revisoes: AdminPaginaDto<AdminRevisaoListaItemDto> | null;
  revisaoDetalhe: AdminRevisaoDetalheDto | null;
  canModerateAnuncio: boolean;
  canModerateMidia: boolean;
  status: string;
  actionStatus: string;
};

export function AdminModerationPanel() {
  const [data, setData] = useState<DetailedState>({
    anuncios: null,
    anuncioDetalhe: null,
    midias: null,
    midiaDetalhe: null,
    revisoes: null,
    revisaoDetalhe: null,
    canModerateAnuncio: false,
    canModerateMidia: false,
    status: "consultando listagens e ações",
    actionStatus: "nenhuma ação executada"
  });

  useEffect(() => {
    void loadDetailedData();
  }, []);

  async function loadDetailedData() {
    const [me, permissions, anuncios, midias, revisoes] = await Promise.all([
      getAdminMe(),
      getAdminPermissions(),
      getAdminAnunciosDetalhados(),
      getAdminMidiasDetalhadas(),
      getAdminRevisoesDetalhadas()
    ]);
    const papeis = me.ok ? me.data.papeis : [];
    const permissoes = permissions.ok ? permissions.data.permissoes.map((item) => item.codigo) : [];
    const canModerateAnuncio =
      (papeis.includes("ADMIN") || papeis.includes("MODERADOR")) && permissoes.includes("ANUNCIO_MODERAR");
    const canModerateMidia =
      (papeis.includes("ADMIN") || papeis.includes("MODERADOR")) && permissoes.includes("MIDIA_REVISAR");
    const firstAnuncio = anuncios.ok ? anuncios.data.itens[0] : null;
    const firstMidia = midias.ok ? midias.data.itens[0] : null;
    const firstRevisao = revisoes.ok ? revisoes.data.itens[0] : null;

    const [anuncioDetalhe, midiaDetalhe, revisaoDetalhe] = await Promise.all([
      firstAnuncio ? getAdminAnuncioDetalhe(firstAnuncio.id) : Promise.resolve(null),
      firstMidia ? getAdminMidiaDetalhe(firstMidia.id) : Promise.resolve(null),
      firstRevisao ? getAdminRevisaoDetalhe(firstRevisao.id) : Promise.resolve(null)
    ]);

    const okCount = [anuncios, midias, revisoes].filter((item) => item.ok).length;
    setData((current) => ({
      anuncios: anuncios.ok ? anuncios.data : null,
      anuncioDetalhe: anuncioDetalhe && anuncioDetalhe.ok ? anuncioDetalhe.data : null,
      midias: midias.ok ? midias.data : null,
      midiaDetalhe: midiaDetalhe && midiaDetalhe.ok ? midiaDetalhe.data : null,
      revisoes: revisoes.ok ? revisoes.data : null,
      revisaoDetalhe: revisaoDetalhe && revisaoDetalhe.ok ? revisaoDetalhe.data : null,
      canModerateAnuncio,
      canModerateMidia,
      status: okCount > 0 ? "listagens carregadas" : "listagens exigem sessão/permissão",
      actionStatus: current.actionStatus
    }));
  }

  async function handleDecidirRevisao(decisao: "APROVAR" | "REPROVAR" | "SOLICITAR_AJUSTE") {
    const revisao = data.revisaoDetalhe;
    if (!revisao || !data.canModerateAnuncio || !["ABERTA", "EM_ANALISE"].includes(revisao.status ?? "")) {
      setData((current) => ({ ...current, actionStatus: "ação de moderação indisponível para revisão" }));
      return;
    }
    setData((current) => ({ ...current, actionStatus: "executando ação de moderação de revisão" }));
    const response = await decidirAdminRevisao(revisao.id, {
      decisao,
      classificacaoConteudo: decisao === "REPROVAR" ? "BLOQUEADO" : "LIVRE",
      motivo:
        decisao === "REPROVAR"
          ? "reprovação com motivo obrigatório"
          : decisao === "SOLICITAR_AJUSTE"
            ? "solicitação de ajuste com motivo obrigatório"
            : "ação de moderação",
      observacao: "execução sem envio externo"
    });
    setData((current) => ({
      ...current,
      actionStatus: response.ok ? response.data.mensagem : `falha ${response.status}`
    }));
    await loadDetailedData();
  }

  async function handleDecidirMidia(decisao: "APROVAR" | "REPROVAR") {
    const midia = data.midiaDetalhe;
    if (!midia || !data.canModerateMidia || midia.status !== "PENDENTE") {
      setData((current) => ({ ...current, actionStatus: "ação de moderação indisponível para mídia" }));
      return;
    }
    setData((current) => ({ ...current, actionStatus: "executando ação de moderação de mídia" }));
    const response = await decidirAdminMidia(midia.id, {
      decisao,
      classificacaoConteudo: decisao === "APROVAR" ? "LIVRE" : "BLOQUEADO",
      motivo: decisao === "REPROVAR" ? "reprovação com motivo obrigatório" : "ação de moderação",
      observacao: "execução sem upload ou exclusão"
    });
    setData((current) => ({
      ...current,
      actionStatus: response.ok ? response.data.mensagem : `falha ${response.status}`
    }));
    await loadDetailedData();
  }

  async function handleRemeterRevisao() {
    const anuncio = data.anuncioDetalhe;
    if (!anuncio || !data.canModerateAnuncio || anuncio.status === "PENDENTE_REVISAO") {
      setData((current) => ({ ...current, actionStatus: "ação de moderação indisponível para anúncio" }));
      return;
    }
    setData((current) => ({ ...current, actionStatus: "remetendo anúncio para revisão" }));
    const response = await remeterAdminAnuncioParaRevisao(anuncio.id, {
      motivo: "remeter anúncio para revisão",
      observacao: "execução sem comunicação externa"
    });
    setData((current) => ({
      ...current,
      actionStatus: response.ok ? response.data.mensagem : `falha ${response.status}`
    }));
    await loadDetailedData();
  }

  return (
    <section className="admin-panel" aria-label="Moderação administrativa">
      <h2>Moderação</h2>
      <p>{data.status}</p>
      <div className="admin-readonly-columns">
        <ReadonlyList
          title="Anúncios"
          emptyLabel="sem anúncios permitidos"
          items={data.anuncios?.itens.map((item) => ({
            key: item.id,
            title: item.titulo ?? formatAdminText(item.slug, item.id),
            meta: `${formatAdminValue(item.status, "sem status")} / ${formatAdminValue(item.classificacaoConteudo, "sem classificação")}`,
            detail: item.localizacao
              ? [item.localizacao.uf, item.localizacao.cidade, item.localizacao.bairro].filter(Boolean).join(" - ")
              : "sem localização"
          }))}
        />
        <ReadonlyList
          title="Mídia"
          emptyLabel="sem mídia permitida"
          items={data.midias?.itens.map((item) => ({
            key: item.id,
            title: formatAdminText(item.slugAnuncio, item.id),
            meta: `${formatAdminValue(item.tipo, "tipo")} / ${formatAdminValue(item.status, "status")}`,
            detail: `${item.mimeType ?? "mime pendente"} / arquivo privado oculto`
          }))}
        />
        <ReadonlyList
          title="Moderação"
          emptyLabel="sem revisões permitidas"
          items={data.revisoes?.itens.map((item) => ({
            key: item.id,
            title: formatAdminText(item.slugAnuncio, item.id),
            meta: `${formatAdminValue(item.tipo, "tipo")} / ${formatAdminValue(item.status, "status")}`,
            detail: item.conteudoSolicitadoPresente ? "conteúdo solicitado oculto" : "sem conteúdo solicitado"
          }))}
        />
      </div>
      <dl className="health-grid compact">
        <ReadonlyMetric label="Detalhe anúncio" value={formatAdminValue(data.anuncioDetalhe?.status, "sem permissão")} />
        <ReadonlyMetric label="Detalhe mídia" value={formatAdminValue(data.midiaDetalhe?.status, "sem permissão")} />
        <ReadonlyMetric label="Detalhe revisão" value={formatAdminValue(data.revisaoDetalhe?.status, "sem permissão")} />
      </dl>
      <div className="admin-moderation-actions" aria-label="Ações de moderação">
        <div>
          <strong>Revisão</strong>
          <button
            type="button"
            className="local-action"
            disabled={!data.canModerateAnuncio || !["ABERTA", "EM_ANALISE"].includes(data.revisaoDetalhe?.status ?? "")}
            onClick={() => void handleDecidirRevisao("APROVAR")}
          >
            Aprovar revisão
          </button>
          <button
            type="button"
            className="local-action"
            disabled={!data.canModerateAnuncio || !["ABERTA", "EM_ANALISE"].includes(data.revisaoDetalhe?.status ?? "")}
            onClick={() => void handleDecidirRevisao("REPROVAR")}
          >
            Reprovar revisão
          </button>
          <button
            type="button"
            className="local-action"
            disabled={!data.canModerateAnuncio || !["ABERTA", "EM_ANALISE"].includes(data.revisaoDetalhe?.status ?? "")}
            onClick={() => void handleDecidirRevisao("SOLICITAR_AJUSTE")}
          >
            Solicitar ajuste
          </button>
          <button
            type="button"
            className="local-action"
            disabled={!data.canModerateAnuncio || !data.anuncioDetalhe || data.anuncioDetalhe.status === "PENDENTE_REVISAO"}
            onClick={() => void handleRemeterRevisao()}
          >
            Remeter para revisão
          </button>
        </div>
        <div>
          <strong>Mídia</strong>
          <button
            type="button"
            className="local-action"
            disabled={!data.canModerateMidia || data.midiaDetalhe?.status !== "PENDENTE"}
            onClick={() => void handleDecidirMidia("APROVAR")}
          >
            Aprovar mídia
          </button>
          <button
            type="button"
            className="local-action"
            disabled={!data.canModerateMidia || data.midiaDetalhe?.status !== "PENDENTE"}
            onClick={() => void handleDecidirMidia("REPROVAR")}
          >
            Reprovar mídia
          </button>
        </div>
      </div>
      <div className="admin-notice">
        {formatAdminText(data.actionStatus)}. Sem exclusão, pausa, ativação, pagamento, crédito, Pix, upload ou envio externo.
      </div>
    </section>
  );
}

function ReadonlyList({
  title,
  emptyLabel,
  items
}: {
  title: string;
  emptyLabel: string;
  items?: readonly { key: string; title: string; meta: string; detail: string }[];
}) {
  return (
    <div className="admin-readonly-list">
      <h3>{title}</h3>
      {items?.length ? (
        <ul>
          {items.map((item) => (
            <li key={item.key}>
              <span>{item.title}</span>
              <small>{item.meta}</small>
              <small>{item.detail}</small>
            </li>
          ))}
        </ul>
      ) : (
        <p>{emptyLabel}</p>
      )}
    </div>
  );
}

function ReadonlyMetric({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}
