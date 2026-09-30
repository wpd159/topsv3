'use client'

import Link from 'next/link'
import { useCallback, useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { getAdminSession } from '@/lib/admin-auth-api'
import type { PublicidadeRegistroDetalhe, RegistroFiltros, StoryRegistroDetalhe } from '@/lib/admin-registros-api'
import { benefitLabel, calendarDate, captureLabel, dateTime, endReasonLabel, errorStatus, label, sessionState, snapshotText, type RegistroSessionState } from './record-utils'

export function useRegistroSession() {
  const [state, setState] = useState<RegistroSessionState>({ status: 'LOADING' })
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    let active = true
    setState({ status: 'LOADING' })
    void getAdminSession().then((session) => { if (active) setState(sessionState(session)) })
      .catch((error) => { if (active) setState(errorStatus(error) === 401 ? { status: 'EXPIRED' } : { status: 'ERROR', error }) })
    return () => { active = false }
  }, [attempt])
  const expire = useCallback(() => setState({ status: 'EXPIRED' }), [])
  return { state, retry: () => setAttempt((value) => value + 1), expire,
    canExport: state.status === 'READY' && state.session.permissoes.includes('ARQUIVO_PUBLICIDADE_EXPORTAR') }
}

export function SessionNotice({ state, retry }: { state: RegistroSessionState; retry: () => void }) {
  if (state.status === 'READY') return null
  const message = state.status === 'LOADING' ? 'Conferindo sessão e permissões...'
    : state.status === 'EXPIRED' ? 'Sessão expirada ou não autenticada. Entre novamente para consultar os registros.'
      : state.status === 'DENIED' ? 'A sessão foi confirmada, mas não possui permissão para consultar este arquivo.'
        : 'Não foi possível consultar a sessão. Isso não comprova falta de permissão.'
  return <div className="rounded-lg border bg-amber-50 p-4 text-sm" role="status"><p>{message}</p>
    {state.status === 'EXPIRED' ? <Link href="/admin/login" className="mt-2 inline-block underline">Entrar no painel</Link> : null}
    {state.status === 'ERROR' ? <Button type="button" variant="outline" className="mt-2" onClick={retry}>Conferir sessão novamente</Button> : null}
  </div>
}

export function IdField({ name, value }: { name: string; value: string | null | undefined }) {
  const [message, setMessage] = useState('')
  async function copy() {
    try {
      if (!value || !navigator.clipboard?.writeText) throw new Error('Clipboard unavailable')
      await navigator.clipboard.writeText(value); setMessage('Identificador copiado.')
    } catch { setMessage('Não foi possível copiar. Selecione o identificador exibido.') }
  }
  return <div><dt className="text-xs text-gray-600">{name}</dt><dd className="break-all font-mono text-xs">{value || 'Não registrado'}
    {value ? <button type="button" className="no-print ml-2 rounded px-1 font-sans text-pink-700 underline focus-visible:outline focus-visible:outline-2" aria-label={`Copiar ${name}`} onClick={() => void copy()}>Copiar</button> : null}
    <span className="no-print block font-sans" role="status">{message}</span>
  </dd></div>
}

export function CurrentLinks({ adId, ownerId, permissions }: { adId: string | null; ownerId: string | null; permissions: string[] }) {
  return <div className="no-print flex flex-wrap gap-3 text-sm">
    {adId && permissions.includes('ANUNCIO_LER') ? <Link href={`/admin/anuncios/${encodeURIComponent(adId)}`} className="text-pink-700 underline">Cadastro atual do anúncio</Link> : null}
    {ownerId && permissions.includes('ANUNCIO_LER') ? <Link href={`/admin/usuarios/${encodeURIComponent(ownerId)}`} className="text-pink-700 underline">Cadastro atual do proprietário</Link> : null}
    <p className="w-full text-xs text-gray-600">Esses links consultam cadastros atuais; não substituem as capturas históricas abaixo. O destino mantém sua própria autorização.</p>
  </div>
}

export function Benefit({ code }: { code: string | null | undefined }) {
  return <div><p>{benefitLabel(code)}</p>{code ? <p className="text-xs text-gray-600">Código capturado: <span className="break-all font-mono">{code}</span>. Rótulo de apresentação, não nome histórico capturado.</p> : null}</div>
}

export function FilterSummary({ filters }: { filters: RegistroFiltros }) {
  const fields = { termo: 'Busca histórica', anuncioId: 'ID do anúncio', anuncianteId: 'ID do proprietário', beneficio: 'Benefício (código)', situacao: 'Situação', ordenacao: 'Ordenação' }
  return <dl className="grid gap-2 text-sm">
    {Object.entries(fields).map(([name, text]) => { const value = filters[name as keyof RegistroFiltros]; return value ? <div key={name}><dt className="text-xs text-gray-600">{text}</dt><dd className="[overflow-wrap:anywhere]">{name === 'beneficio' ? <Benefit code={value} /> : name === 'situacao' || name === 'ordenacao' ? label(value) : value}</dd></div> : null })}
    {filters.inicio || filters.fim ? <div><dt className="text-xs text-gray-600">Dias consultados (America/Sao_Paulo)</dt><dd>De {calendarDate(filters.inicio) || 'sem data inicial'} até {calendarDate(filters.fim, true) || 'sem data final'} (dia final inclusive).</dd>
      <dd className="text-xs">Fronteiras efetivas: {dateTime(filters.inicio)} até {dateTime(filters.fim)} (fim exclusivo).</dd></div> : null}
    {!Object.values(filters).some(Boolean) ? <div><dt className="text-xs text-gray-600">Filtros</dt><dd>Sem filtros adicionais; todas as veiculações do tipo e finalidade escolhidos.</dd></div> : null}
    <TechnicalFields value={filters} known={['termo', 'anuncioId', 'anuncianteId', 'beneficio', 'situacao', 'ordenacao', 'inicio', 'fim']} />
  </dl>
}

export function TechnicalFields({ value, known }: { value: object; known: string[] }) {
  const extra = Object.fromEntries(Object.entries(value).filter(([key]) => !known.includes(key)))
  return Object.keys(extra).length ? <section className="technical-fields mt-3"><h5 className="font-medium">Campos adicionais preservados (seção técnica)</h5>
    <pre className="mt-1 whitespace-pre-wrap rounded bg-gray-50 p-2 text-xs [overflow-wrap:anywhere]">{JSON.stringify(extra, null, 2)}</pre></section> : null
}

const CAPTURE_FIELDS: Record<string, string> = {
  estado: 'Estado registrado', proveniencia: 'Proveniência', anuncioId: 'ID do anúncio', storyId: 'ID do Story',
  modoConteudo: 'Modo de conteúdo', titulo: 'Título', nomePublico: 'Nome público', slug: 'Slug', descricao: 'Descrição integral',
  categoria: 'Categoria', preco: 'Preço registrado', whatsapp_normalizado: 'WhatsApp na captura', link_conteudo: 'Link de conteúdo na captura',
  atendimento_exclusivamente_virtual: 'Atendimento exclusivamente virtual', urlPublicaNaCaptura: 'Endereço público na captura',
  urlAnuncioNaCaptura: 'Endereço do anúncio na captura', urlMidiaNaCaptura: 'Endereço da mídia na captura', produtoServicoMarca: 'Produto, serviço ou marca',
  localizacao: 'Localização registrada', estado_id: 'ID do estado', cidade_id: 'ID da cidade', bairro_id: 'ID do bairro',
  uf: 'UF', cidade: 'Cidade', bairro: 'Bairro', endereco_resumido: 'Endereço resumido na captura',
  servicos: 'Serviços registrados', locaisAtendimento: 'Locais de atendimento', apresentacaoStory: 'Apresentação do Story', resumo: 'Resumo registrado',
  midias: 'Referências das mídias na apresentação', anuncioMidiaId: 'ID da mídia do anúncio', arquivoMidiaId: 'ID do arquivo de mídia',
  tipo: 'Tipo', finalidade: 'Finalidade registrada', ordem: 'Ordem', visibilidade: 'Visibilidade registrada',
  usuarioId: 'ID do contratante', nome: 'Nome registrado', nomeCivil: 'Nome civil na captura', cpf: 'CPF na captura', email: 'E-mail na captura',
  terceiroBeneficiario: 'Terceiro beneficiário', classificacao: 'Classificação registrada', relacaoMaterial: 'Relação material registrada', cobertura: 'Cobertura registrada',
  ativacaoBeneficioId: 'ID da ativação', grupoAtivacaoId: 'ID do grupo da ativação', beneficioCodigo: 'Benefício registrado', beneficioEscopo: 'Escopo do benefício',
  origem: 'Origem registrada', atorAdministrativoId: 'ID do ator administrativo', movimentoCreditoId: 'ID do movimento de crédito', pagamentoId: 'ID do pagamento',
  vinculoPagamento: 'Vínculo de pagamento registrado', destinatariosUnicos: 'Destinatários únicos',
}

// Translate only paths whose archive contract stores controlled values. Names, titles,
// descriptions, addresses and future free-text fields must remain literal.
const CONTROLLED_CAPTURE_PATHS = new Set([
  'conteudo.estado', 'conteudo.proveniencia', 'conteudo.modoConteudo',
  'conteudo.midias.tipo', 'conteudo.midias.finalidade', 'conteudo.midias.visibilidade',
  'contratante.terceiroBeneficiario',
  'comercial.classificacao', 'comercial.relacaoMaterial', 'comercial.cobertura',
  'comercial.beneficioEscopo', 'comercial.origem', 'comercial.vinculoPagamento',
  'segmentacao.estado', 'alcance.estado', 'alcance.destinatariosUnicos',
])

function CaptureValue({ value, path = '' }: { value: unknown; path?: string }) {
  if (value == null) return <span>Não registrado na captura</span>
  if (Array.isArray(value)) return value.length ? <ol className="space-y-2">{value.map((item, index) => <li key={index} className="rounded border p-2"><CaptureValue value={item} path={path} /></li>)}</ol> : <span>Lista vazia na captura</span>
  if (typeof value === 'object') {
    const entries = Object.entries(value), known = entries.filter(([name]) => name in CAPTURE_FIELDS)
    const unknown = Object.fromEntries(entries.filter(([name]) => !(name in CAPTURE_FIELDS)))
    return <>
      <dl className="grid gap-3">{known.map(([name, item]) => <div key={name}><dt className="text-xs text-gray-600">{CAPTURE_FIELDS[name]}</dt>
        <dd className="mt-1 whitespace-pre-wrap [overflow-wrap:anywhere]">{name === 'beneficioCodigo' ? <Benefit code={typeof item === 'string' ? item : null} /> : <CaptureValue value={item} path={path ? `${path}.${name}` : name} />}</dd></div>)}</dl>
      {!entries.length ? <p>Nenhum campo registrado nesta captura.</p> : null}
      {Object.keys(unknown).length ? <section className="technical-fields mt-3"><h6 className="font-medium">Campos adicionais da captura (seção técnica)</h6>
        <pre className="mt-1 whitespace-pre-wrap rounded bg-gray-50 p-2 text-xs [overflow-wrap:anywhere]">{JSON.stringify(unknown, null, 2)}</pre>
      </section> : null}
    </>
  }
  if (typeof value === 'boolean') return <span>{value ? 'Sim (valor registrado)' : 'Não (valor registrado)'}</span>
  if (typeof value === 'string') {
    const presented = CONTROLLED_CAPTURE_PATHS.has(path) ? captureLabel(value) : value
    return <span className="whitespace-pre-wrap [overflow-wrap:anywhere]">{presented}{presented !== value ? <span className="ml-1 text-xs text-gray-600">(código: {value})</span> : null}</span>
  }
  return <span>{String(value)}</span>
}

export function RegistroContent({ detail, permissions = [], printable = false, privateSnapshots = false, timeZone = 'America/Sao_Paulo' }: {
  detail: PublicidadeRegistroDetalhe | StoryRegistroDetalhe; permissions?: string[]; printable?: boolean; privateSnapshots?: boolean; timeZone?: string
}) {
  const latest = detail.versoes.reduce<typeof detail.versoes[number] | undefined>((current, version) =>
    !current || version.numero > current.numero ? version : current, undefined)
  const title = snapshotText(latest?.conteudo, 'titulo') || snapshotText(latest?.conteudo, 'nomePublico') || 'Conteúdo sem título na captura'
  return <article className="record-detail space-y-4 rounded-lg border bg-white p-4">
    <div><h2 className="text-lg font-semibold">{title}</h2><p className="text-sm text-gray-600">Dados históricos preservados; não são uma leitura do anúncio atual.</p></div>
    {!printable ? <CurrentLinks adId={detail.anuncioId} ownerId={detail.contratanteUsuarioId} permissions={permissions} /> : null}
    <h3 className="font-semibold">Identificação, referências e período da peça</h3>
    <dl className="grid gap-3 text-sm sm:grid-cols-2">
      <IdField name="Registro" value={detail.id} /><IdField name="Anúncio" value={detail.anuncioId} />
      {'storyId' in detail ? <IdField name="Story" value={detail.storyId} /> : null}
      <IdField name="Contratante" value={detail.contratanteUsuarioId} />
      <IdField name="Ativação do benefício" value={detail.ativacaoBeneficioId} />
      <IdField name="Grupo da ativação" value={detail.grupoAtivacaoId} />
      <IdField name="Movimento de crédito" value={detail.movimentoCreditoId} />
      <IdField name="Pagamento" value={detail.pagamentoId} />
      <div><dt className="text-gray-600">Natureza / relação material</dt><dd>{label(detail.natureza)} · {label(detail.relacaoMaterial)}</dd></div>
      <div><dt className="text-gray-600">Cobertura</dt><dd>{label(detail.cobertura)}</dd></div>
      {'modoConteudo' in detail ? <div><dt className="text-gray-600">Modo do Story</dt><dd>{label(detail.modoConteudo)}</dd></div> : null}
      <div><dt className="text-gray-600">Início</dt><dd>{dateTime(detail.inicioEm, timeZone)}</dd></div>
      <div><dt className="text-gray-600">{label(detail.fimTipo)}</dt><dd>{dateTime(detail.fimEm, timeZone)}</dd></div>
      <div><dt className="text-gray-600">{endReasonLabel(detail.fimTipo)}</dt><dd>{label(detail.encerramentoMotivo)}</dd></div>
      <div><dt className="text-gray-600">Guarda mínima até</dt><dd>{dateTime(detail.retencaoAte, timeZone)}</dd></div>
    </dl>
    <p className="text-xs text-gray-600">O fim previsto não comprova encerramento efetivo. A data de guarda mínima não comprova exclusão; preservações específicas podem continuar impedindo o descarte.</p>
    <TechnicalFields value={detail} known={['id', 'anuncioId', 'storyId', 'modoConteudo', 'contratanteUsuarioId', 'ativacaoBeneficioId', 'grupoAtivacaoId', 'movimentoCreditoId', 'pagamentoId', 'natureza', 'relacaoMaterial', 'cobertura', 'inicioEm', 'fimEm', 'retencaoAte', 'encerramentoMotivo', 'fimTipo', 'preservacoes', 'versoes']} />
    {privateSnapshots ? <section><h3 className="font-semibold">Preservações específicas ({detail.preservacoes?.length ?? 0})</h3>
      {(detail.preservacoes ?? []).map((preservation) => <div key={preservation.id} className="mt-2 rounded border p-3 text-sm">
        <dl><IdField name="Preservação" value={preservation.id} /><IdField name="Responsável pela preservação" value={preservation.responsavelUsuarioId} /></dl>
        <p>Fundamento: {preservation.fundamento}</p><p>Início: {dateTime(preservation.inicioEm, timeZone)} · Revisar em: {dateTime(preservation.revisarEm, timeZone)}</p>
        <TechnicalFields value={preservation} known={['id', 'fundamento', 'responsavelUsuarioId', 'inicioEm', 'revisarEm']} />
      </div>)}
    </section> : <p className="text-sm text-gray-600">Preservações específicas: informação restrita, não incluída na consulta operacional. Uma lista redigida não comprova ausência de preservação.</p>}
    <section><h3 className="font-semibold">Todas as versões preservadas ({detail.versoes.length})</h3>
      {detail.versoes.length === 0 ? <p className="text-sm">Nenhuma versão capturada. Não foi reconstruída a partir do cadastro atual.</p> : null}
      <ol className="mt-2 space-y-3">{detail.versoes.map((version) => <li key={version.id} className="record-version rounded border p-3 text-sm">
        <h4 className="font-semibold">Versão {version.numero} · {snapshotText(version.conteudo, 'titulo') || snapshotText(version.conteudo, 'nomePublico') || 'Conteúdo capturado'}</h4>
        <dl className="mt-2 grid gap-2 sm:grid-cols-2"><IdField name="Versão" value={version.id} /><IdField name="SHA-256 da captura" value={version.conteudoSha256} /></dl>
        <p>Captura: {dateTime(version.capturadoEm, timeZone)} · Vigência: {dateTime(version.vigenteDesde, timeZone)} até {dateTime(version.vigenteAte, timeZone)} · Motivo: {label(version.motivo)}</p>
        <TechnicalFields value={version} known={['id', 'numero', 'capturadoEm', 'vigenteDesde', 'vigenteAte', 'motivo', 'conteudo', 'contratante', 'comercial', 'segmentacao', 'alcance', 'conteudoSha256', 'midias']} />
        {(['conteudo', 'contratante', 'comercial', 'segmentacao', 'alcance'] as const).filter((field) => privateSnapshots || (field !== 'contratante' && field !== 'comercial')).map((field) => <section key={field} className="mt-3">
          <h5 className="font-medium">{({ conteudo: 'Conteúdo e apresentação', contratante: 'Contratante na captura', comercial: 'Dados comerciais na captura', segmentacao: 'Segmentação registrada', alcance: 'Alcance registrado' })[field]}</h5>
          <div className="mt-2"><CaptureValue value={version[field]} path={field} /></div>
        </section>)}
        {!privateSnapshots ? <p className="mt-2 text-xs text-gray-600">Capturas de contratante e dados comerciais completos exigem permissão de exportação.</p> : null}
        <h5 className="mt-3 font-medium">Metadados de mídias ({version.midias.length})</h5>
        {version.midias.map((media) => <div key={media.id} className="mt-2 rounded border p-2">
          <dl className="grid gap-2 sm:grid-cols-2"><IdField name="Mídia arquivada" value={media.id} /><IdField name="SHA-256 da mídia" value={media.sha256} /></dl>
          <p>{label(media.variante)} · {media.mimeType} · {media.tamanhoBytes.toLocaleString('pt-BR')} bytes · Ordem {media.ordem}</p>
          <TechnicalFields value={media} known={['id', 'variante', 'mimeType', 'tamanhoBytes', 'ordem', 'sha256', 'arquivoUrl']} />
        </div>)}
        <p className="mt-2 text-xs text-gray-600">Metadados não equivalem ao conteúdo binário. Nenhuma mídia ou documento KYC é baixado automaticamente.</p>
      </li>)}</ol>
    </section>
  </article>
}
