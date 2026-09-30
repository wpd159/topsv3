'use client'

import Link from 'next/link'
import { useParams, useSearchParams } from 'next/navigation'
import { useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { ContractState } from '@/components/feedback/contract-state'
import {
  baixarMidiaPublicidade, baixarMidiaStory, detalharRegistroPublicidade, detalharRegistroStory,
  exportarRegistroPublicidade, exportarRegistroStory,
  type PublicidadeRegistroDetalhe, type StoryRegistroDetalhe,
} from '@/lib/admin-registros-api'
import { RegistroContent, SessionNotice, useRegistroSession } from './record-content'
import { clearedSelectionReturn, errorStatus, queryState, safeReturn } from './record-utils'

function saveFile(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url; anchor.download = name
  document.body.appendChild(anchor); anchor.click(); anchor.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 30_000)
}

export function AdminRegistroDetail() {
  const { tipo, id } = useParams<{ tipo: string; id: string }>()
  const search = useSearchParams()
  const { finalidade } = queryState(new URLSearchParams(search.toString()))
  const { state: session, retry, canExport, expire } = useRegistroSession()
  const [detail, setDetail] = useState<PublicidadeRegistroDetalhe | StoryRegistroDetalhe | null>(null)
  const [error, setError] = useState<unknown>(null), [loading, setLoading] = useState(false)
  const [fileError, setFileError] = useState<unknown>(null), [busy, setBusy] = useState(false)
  const validType = tipo === 'publicidade' || tipo === 'stories'
  useEffect(() => {
    setDetail(null); setError(null)
    if (session.status !== 'READY' || !finalidade || !validType) return
    const controller = new AbortController()
    setLoading(true)
    const load = tipo === 'publicidade' ? detalharRegistroPublicidade : detalharRegistroStory
    void load(id, finalidade, controller.signal).then((value) => { if (!controller.signal.aborted) setDetail(value) })
      .catch((cause) => { if (!controller.signal.aborted) { setError(cause); if (errorStatus(cause) === 401) expire() } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [session.status, tipo, id, finalidade, validType, expire])
  async function exportFile(media?: { id: string; mimeType: string }) {
    if (!canExport || !finalidade || !detail || busy) return
    setBusy(true); setFileError(null)
    try {
      const download = tipo === 'publicidade' ? baixarMidiaPublicidade : baixarMidiaStory
      const exportJson = tipo === 'publicidade' ? exportarRegistroPublicidade : exportarRegistroStory
      const extension = media ? ({ 'image/jpeg': 'jpg', 'image/png': 'png', 'image/webp': 'webp', 'video/mp4': 'mp4', 'video/webm': 'webm' }[media.mimeType] || 'bin') : 'json'
      const blob = media ? await download(detail.id, media.id, finalidade) : await exportJson(detail.id, finalidade)
      saveFile(blob, `registro-${tipo}-${media?.id || detail.id}.${extension}`)
    } catch (cause) { setFileError(cause); if (errorStatus(cause) === 401) expire() }
    finally { setBusy(false) }
  }
  return <section className="space-y-4">
    <Link href={session.status === 'EXPIRED' || session.status === 'DENIED' ? clearedSelectionReturn(search.get('retorno')) : safeReturn(search.get('retorno'))} className="text-pink-700 underline">Voltar à consulta com filtros</Link>
    <h1 className="text-2xl font-bold">Detalhe da captura de {tipo === 'stories' ? 'Story' : 'anúncio'}</h1>
    <SessionNotice state={session} retry={retry} />
    {!validType ? <p role="status">Tipo de registro inválido. Nenhuma consulta foi enviada.</p> : null}
    {!finalidade ? <p role="status">Escolha a finalidade na consulta de registros antes de abrir o detalhe privado.</p> : null}
    {loading ? <p role="status">Carregando captura...</p> : null}
    {error ? <ContractState error={error} /> : null}
    {detail && session.status === 'READY' ? <>
      <p className="text-sm text-gray-600">Consulta operacional redigida. Capturas privadas completas e fundamentos de preservação estão disponíveis apenas nas ações explícitas de exportação autorizada.</p>
      <RegistroContent detail={detail} permissions={session.session.permissoes} />
      {canExport ? <section className="space-y-3 rounded-lg border bg-white p-4">
        <h2 className="font-semibold">Exportação explícita</h2>
        <p className="text-xs text-gray-600">Estas ações solicitam arquivos ao servidor com autorização e auditoria. Iniciar a transferência não comprova recebimento ou impressão.</p>
        <Button type="button" variant="outline" disabled={busy} onClick={() => void exportFile()}>Exportar JSON completo deste registro</Button>
        <div className="flex flex-wrap gap-2">{detail.versoes.map((version) => version.midias.map((media) => <Button key={`${version.id}:${media.id}`} type="button" variant="outline" disabled={busy} onClick={() => void exportFile(media)}>Baixar mídia da versão {version.numero}, ordem {media.ordem}</Button>))}</div>
        {fileError ? <ContractState error={fileError} /> : null}
      </section> : <p className="text-sm text-amber-800">Consulta permitida. Exportação e download não autorizados para esta sessão.</p>}
    </> : null}
  </section>
}
