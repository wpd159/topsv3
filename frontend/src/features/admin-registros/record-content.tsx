'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { getAdminSession } from '@/lib/admin-auth-api'
import type { PublicidadeRegistroDetalhe, StoryRegistroDetalhe } from '@/lib/admin-registros-api'
import { dateTime, errorStatus, label, sessionState, snapshotText, type RegistroSessionState } from './record-utils'

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
  return { state, retry: () => setAttempt((value) => value + 1),
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

export function RegistroContent({ detail, permissions = [], printable = false, privateSnapshots = false, timeZone = 'America/Sao_Paulo' }: {
  detail: PublicidadeRegistroDetalhe | StoryRegistroDetalhe; permissions?: string[]; printable?: boolean; privateSnapshots?: boolean; timeZone?: string
}) {
  const latest = detail.versoes.reduce<typeof detail.versoes[number] | undefined>((current, version) =>
    !current || version.numero > current.numero ? version : current, undefined)
  const title = snapshotText(latest?.conteudo, 'titulo') || snapshotText(latest?.conteudo, 'nomePublico') || 'Conteúdo sem título na captura'
  return <article className="record-detail space-y-4 rounded-lg border bg-white p-4">
    <div><h2 className="text-lg font-semibold">{title}</h2><p className="text-sm text-gray-600">Dados históricos preservados; não são uma leitura do anúncio atual.</p></div>
    {!printable ? <CurrentLinks adId={detail.anuncioId} ownerId={detail.contratanteUsuarioId} permissions={permissions} /> : null}
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
      <div><dt className="text-gray-600">Motivo do encerramento</dt><dd>{label(detail.encerramentoMotivo)}</dd></div>
      <div><dt className="text-gray-600">Guarda mínima até</dt><dd>{dateTime(detail.retencaoAte, timeZone)}</dd></div>
    </dl>
    <p className="text-xs text-gray-600">O fim previsto não comprova encerramento efetivo. A data de guarda mínima não comprova exclusão; preservações específicas podem continuar impedindo o descarte.</p>
    {privateSnapshots ? <section><h3 className="font-semibold">Preservações específicas ({detail.preservacoes?.length ?? 0})</h3>
      {(detail.preservacoes ?? []).map((preservation) => <div key={preservation.id} className="mt-2 rounded border p-3 text-sm">
        <dl><IdField name="Preservação" value={preservation.id} /><IdField name="Responsável pela preservação" value={preservation.responsavelUsuarioId} /></dl>
        <p>Fundamento: {preservation.fundamento}</p><p>Início: {dateTime(preservation.inicioEm, timeZone)} · Revisar em: {dateTime(preservation.revisarEm, timeZone)}</p>
      </div>)}
    </section> : <p className="text-sm text-gray-600">Preservações específicas: informação restrita, não incluída na consulta operacional. Uma lista redigida não comprova ausência de preservação.</p>}
    <section><h3 className="font-semibold">Todas as versões preservadas ({detail.versoes.length})</h3>
      {detail.versoes.length === 0 ? <p className="text-sm">Nenhuma versão capturada. Não foi reconstruída a partir do cadastro atual.</p> : null}
      <ol className="mt-2 space-y-3">{detail.versoes.map((version) => <li key={version.id} className="record-version rounded border p-3 text-sm">
        <h4 className="font-semibold">Versão {version.numero} · {snapshotText(version.conteudo, 'titulo') || snapshotText(version.conteudo, 'nomePublico') || 'Conteúdo capturado'}</h4>
        <dl className="mt-2 grid gap-2 sm:grid-cols-2"><IdField name="Versão" value={version.id} /><IdField name="SHA-256 da captura" value={version.conteudoSha256} /></dl>
        <p>Captura: {dateTime(version.capturadoEm, timeZone)} · Vigência: {dateTime(version.vigenteDesde, timeZone)} até {dateTime(version.vigenteAte, timeZone)} · Motivo: {label(version.motivo)}</p>
        {(['conteudo', 'contratante', 'comercial', 'segmentacao', 'alcance'] as const).filter((field) => privateSnapshots || (field !== 'contratante' && field !== 'comercial')).map((field) => <section key={field} className="mt-3">
          <h5 className="font-medium">{({ conteudo: 'Conteúdo e apresentação', contratante: 'Contratante na captura', comercial: 'Dados comerciais na captura', segmentacao: 'Segmentação registrada', alcance: 'Alcance registrado' })[field]}</h5>
          <pre className="mt-1 whitespace-pre-wrap break-words rounded bg-gray-50 p-2 text-xs [overflow-wrap:anywhere]">{version[field] == null ? 'Não registrado na captura' : JSON.stringify(version[field], null, 2)}</pre>
        </section>)}
        {!privateSnapshots ? <p className="mt-2 text-xs text-gray-600">Capturas de contratante e dados comerciais completos exigem permissão de exportação.</p> : null}
        <h5 className="mt-3 font-medium">Metadados de mídias ({version.midias.length})</h5>
        {version.midias.map((media) => <div key={media.id} className="mt-2 rounded border p-2">
          <dl className="grid gap-2 sm:grid-cols-2"><IdField name="Mídia arquivada" value={media.id} /><IdField name="SHA-256 da mídia" value={media.sha256} /></dl>
          <p>{label(media.variante)} · {media.mimeType} · {media.tamanhoBytes.toLocaleString('pt-BR')} bytes · Ordem {media.ordem}</p>
        </div>)}
        <p className="mt-2 text-xs text-gray-600">Metadados não equivalem ao conteúdo binário. Nenhuma mídia ou documento KYC é baixado automaticamente.</p>
      </li>)}</ol>
    </section>
  </article>
}
