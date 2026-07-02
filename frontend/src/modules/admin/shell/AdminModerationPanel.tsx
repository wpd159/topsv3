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
    status: "consultando listagens e acoes locais",
    actionStatus: "nenhuma acao local executada"
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
      status: okCount > 0 ? "listagens locais carregadas" : "listagens exigem sessao/permissao",
      actionStatus: current.actionStatus
    }));
  }

  async function handleDecidirRevisao(decisao: "APROVAR" | "REPROVAR" | "SOLICITAR_AJUSTE") {
    const revisao = data.revisaoDetalhe;
    if (!revisao || !data.canModerateAnuncio || !["ABERTA", "EM_ANALISE"].includes(revisao.status ?? "")) {
      setData((current) => ({ ...current, actionStatus: "acao local de moderacao indisponivel para revisao" }));
      return;
    }
    setData((current) => ({ ...current, actionStatus: "executando acao local de moderacao de revisao" }));
    const response = await decidirAdminRevisao(revisao.id, {
      decisao,
      classificacaoConteudo: decisao === "REPROVAR" ? "BLOQUEADO" : "LIVRE",
      motivo:
        decisao === "REPROVAR"
          ? "reprovacao local com motivo obrigatorio"
          : decisao === "SOLICITAR_AJUSTE"
            ? "solicitacao local de ajuste com motivo obrigatorio"
            : "acao local de moderacao",
      observacao: "execucao local sem e-mail real"
    });
    setData((current) => ({
      ...current,
      actionStatus: response.ok ? response.data.mensagem : `falha local ${response.status}`
    }));
    await loadDetailedData();
  }

  async function handleDecidirMidia(decisao: "APROVAR" | "REPROVAR") {
    const midia = data.midiaDetalhe;
    if (!midia || !data.canModerateMidia || midia.status !== "PENDENTE") {
      setData((current) => ({ ...current, actionStatus: "acao local de moderacao indisponivel para midia" }));
      return;
    }
    setData((current) => ({ ...current, actionStatus: "executando acao local de moderacao de midia" }));
    const response = await decidirAdminMidia(midia.id, {
      decisao,
      classificacaoConteudo: decisao === "APROVAR" ? "LIVRE" : "BLOQUEADO",
      motivo: decisao === "REPROVAR" ? "reprovacao local com motivo obrigatorio" : "acao local de moderacao",
      observacao: "execucao local sem upload ou exclusao"
    });
    setData((current) => ({
      ...current,
      actionStatus: response.ok ? response.data.mensagem : `falha local ${response.status}`
    }));
    await loadDetailedData();
  }

  async function handleRemeterRevisao() {
    const anuncio = data.anuncioDetalhe;
    if (!anuncio || !data.canModerateAnuncio || anuncio.status === "PENDENTE_REVISAO") {
      setData((current) => ({ ...current, actionStatus: "acao local de moderacao indisponivel para anuncio" }));
      return;
    }
    setData((current) => ({ ...current, actionStatus: "executando acao local de remeter revisao" }));
    const response = await remeterAdminAnuncioParaRevisao(anuncio.id, {
      motivo: "remeter anuncio para revisao local",
      observacao: "execucao local sem comunicacao real"
    });
    setData((current) => ({
      ...current,
      actionStatus: response.ok ? response.data.mensagem : `falha local ${response.status}`
    }));
    await loadDetailedData();
  }

  return (
    <section className="admin-panel" aria-label="Moderacao administrativa local">
      <h2>Moderacao local</h2>
      <p>{data.status}</p>
      <div className="admin-readonly-columns">
        <ReadonlyList
          title="Anuncios"
          emptyLabel="sem anuncios permitidos"
          items={data.anuncios?.itens.map((item) => ({
            key: item.id,
            title: item.titulo ?? item.slug ?? item.id,
            meta: `${item.status ?? "sem status"} / ${item.classificacaoConteudo ?? "sem classificacao"}`,
            detail: item.localizacao
              ? [item.localizacao.uf, item.localizacao.cidade, item.localizacao.bairro].filter(Boolean).join(" - ")
              : "sem localizacao"
          }))}
        />
        <ReadonlyList
          title="Midia"
          emptyLabel="sem midia permitida"
          items={data.midias?.itens.map((item) => ({
            key: item.id,
            title: item.slugAnuncio ?? item.id,
            meta: `${item.tipo ?? "tipo"} / ${item.status ?? "status"}`,
            detail: `${item.mimeType ?? "mime pendente"} / arquivo privado oculto`
          }))}
        />
        <ReadonlyList
          title="Moderacao"
          emptyLabel="sem revisoes permitidas"
          items={data.revisoes?.itens.map((item) => ({
            key: item.id,
            title: item.slugAnuncio ?? item.id,
            meta: `${item.tipo ?? "tipo"} / ${item.status ?? "status"}`,
            detail: item.conteudoSolicitadoPresente ? "conteudo solicitado oculto" : "sem conteudo solicitado"
          }))}
        />
      </div>
      <dl className="health-grid compact">
        <ReadonlyMetric label="Detalhe anuncio" value={data.anuncioDetalhe?.status ?? "sem permissao"} />
        <ReadonlyMetric label="Detalhe midia" value={data.midiaDetalhe?.status ?? "sem permissao"} />
        <ReadonlyMetric label="Detalhe revisao" value={data.revisaoDetalhe?.status ?? "sem permissao"} />
      </dl>
      <div className="admin-moderation-actions" aria-label="Acoes locais de moderacao">
        <div>
          <strong>Revisao</strong>
          <button
            type="button"
            className="local-action"
            disabled={!data.canModerateAnuncio || !["ABERTA", "EM_ANALISE"].includes(data.revisaoDetalhe?.status ?? "")}
            onClick={() => void handleDecidirRevisao("APROVAR")}
          >
            acao local de moderacao: aprovar
          </button>
          <button
            type="button"
            className="local-action"
            disabled={!data.canModerateAnuncio || !["ABERTA", "EM_ANALISE"].includes(data.revisaoDetalhe?.status ?? "")}
            onClick={() => void handleDecidirRevisao("REPROVAR")}
          >
            acao local de moderacao: reprovar
          </button>
          <button
            type="button"
            className="local-action"
            disabled={!data.canModerateAnuncio || !["ABERTA", "EM_ANALISE"].includes(data.revisaoDetalhe?.status ?? "")}
            onClick={() => void handleDecidirRevisao("SOLICITAR_AJUSTE")}
          >
            acao local intermediaria: solicitar ajuste
          </button>
          <button
            type="button"
            className="local-action"
            disabled={!data.canModerateAnuncio || !data.anuncioDetalhe || data.anuncioDetalhe.status === "PENDENTE_REVISAO"}
            onClick={() => void handleRemeterRevisao()}
          >
            acao local de moderacao: remeter para revisao
          </button>
        </div>
        <div>
          <strong>Midia</strong>
          <button
            type="button"
            className="local-action"
            disabled={!data.canModerateMidia || data.midiaDetalhe?.status !== "PENDENTE"}
            onClick={() => void handleDecidirMidia("APROVAR")}
          >
            acao local de moderacao: aprovar
          </button>
          <button
            type="button"
            className="local-action"
            disabled={!data.canModerateMidia || data.midiaDetalhe?.status !== "PENDENTE"}
            onClick={() => void handleDecidirMidia("REPROVAR")}
          >
            acao local de moderacao: reprovar
          </button>
        </div>
      </div>
      <div className="admin-notice">
        {data.actionStatus}. Sem exclusao, pausa, ativacao, pagamento, credito, Pix, upload ou e-mail real.
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
