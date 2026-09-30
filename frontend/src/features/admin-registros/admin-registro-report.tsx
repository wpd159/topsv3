'use client'

import Link from 'next/link'
import { useRouter, useSearchParams } from 'next/navigation'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { ContractState } from '@/components/feedback/contract-state'
import { FINALIDADES_ACESSO_ARQUIVO, listarRegistrosPublicidade, listarRegistrosStory, prepararRelatorioRegistros, type RegistroRelatorio } from '@/lib/admin-registros-api'
import { FilterSummary, RegistroContent, SessionNotice, TechnicalFields, useRegistroSession } from './record-content'
import { dateTime, emptySelection, errorStatus, listQuery, queryState, reportReturn, selectionAllowed, selectionState } from './record-utils'

export function AdminRegistroReport() {
  const search = useSearchParams(), router = useRouter(), rawQuery = search.toString()
  const { finalidade, filtros, familia, page } = useMemo(() => queryState(new URLSearchParams(rawQuery)), [rawQuery])
  const selection = useMemo(() => selectionState(new URLSearchParams(rawQuery)), [rawQuery]), ids = selection.ids
  const { state: session, retry, canExport, expire } = useRegistroSession()
  const [total, setTotal] = useState<number | null>(null), [loading, setLoading] = useState(false)
  const [error, setError] = useState<unknown>(null), [report, setReport] = useState<RegistroRelatorio | null>(null)
  const generation = useRef(0), preparing = useRef<AbortController | null>(null)
  const validType = search.get('tipo') === 'publicidade' || search.get('tipo') === 'stories'
  const scopeCount = selection.escopo === 'selecionados' ? ids.length : total || 0
  const validSelection = selectionAllowed(selection, session)
  useEffect(() => {
    if ((session.status === 'EXPIRED' || session.status === 'DENIED') && (selection.ids.length || selection.usuarioId)) {
      router.replace('/admin/registros/relatorio?' + listQuery(familia, page, finalidade, filtros, emptySelection()))
    }
  }, [session.status, selection, familia, page, finalidade, filtros, router])
  useEffect(() => {
    generation.current += 1; preparing.current?.abort()
    setTotal(null); setReport(null); setError(null)
    if (session.status !== 'READY' || !canExport || !finalidade || !validType) return
    const controller = new AbortController()
    setLoading(true)
    const list = familia === 'publicidade' ? listarRegistrosPublicidade : listarRegistrosStory
    void list(0, finalidade, controller.signal, filtros).then((value) => { if (!controller.signal.aborted) setTotal(value.totalElements) })
      .catch((cause) => { if (!controller.signal.aborted) { setError(cause); if (errorStatus(cause) === 401) expire() } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => { controller.abort(); preparing.current?.abort(); generation.current += 1 }
  }, [session.status, canExport, finalidade, familia, filtros, validType, expire])
  async function prepare() {
    if (!canExport || !finalidade || total == null || scopeCount > 100 || loading || !validType || !validSelection) return
    setLoading(true); setError(null); setReport(null)
    const current = generation.current, controller = new AbortController()
    preparing.current = controller
    try {
      const value = await prepararRelatorioRegistros(familia, finalidade, filtros, ids, controller.signal)
      if (!controller.signal.aborted && current === generation.current) setReport(value)
    } catch (cause) { if (!controller.signal.aborted && current === generation.current) { setError(cause); if (errorStatus(cause) === 401) expire() } }
    finally { if (current === generation.current) setLoading(false) }
  }
  function saveJson() {
    if (!report || !canExport) return
    const url = URL.createObjectURL(new Blob([JSON.stringify(report, null, 2)], { type: 'application/json' }))
    const anchor = document.createElement('a')
    anchor.href = url; anchor.download = `relatorio-${familia}.json`
    document.body.appendChild(anchor); anchor.click(); anchor.remove()
    window.setTimeout(() => URL.revokeObjectURL(url), 30_000)
  }
  return <section className="space-y-4">
    <div className="no-print"><Link href={reportReturn(new URLSearchParams(rawQuery), session.status === 'EXPIRED' || session.status === 'DENIED')} className="text-pink-700 underline">Voltar à consulta com filtros</Link></div>
    <h1 className="text-2xl font-bold">Relatório privado do arquivo publicitário</h1>
    <SessionNotice state={session} retry={retry} />
    {session.status === 'READY' && !canExport ? <p role="status">Sessão confirmada, mas sem permissão de exportação. O relatório não foi solicitado.</p> : null}
    {!finalidade || !validType ? <p role="status">Selecione tipo e finalidade válidos na consulta. Nenhum relatório foi solicitado.</p> : null}
    {canExport && finalidade && validType ? <div className="no-print space-y-2 rounded-lg border bg-white p-4">
      <h2 className="font-semibold">Conferência do escopo antes da geração</h2>
      <p>Tipo: {familia === 'stories' ? 'Stories' : 'Anúncios'} · Finalidade: {FINALIDADES_ACESSO_ARQUIVO.find((item) => item.codigo === finalidade)?.rotulo} · Fuso: America/Sao_Paulo</p>
      <FilterSummary filters={filtros} />
      <p>{total == null ? 'Conferindo quantidade no servidor...' : `${total} registros correspondem ao filtro; o relatório abrangerá ${scopeCount} ${selection.escopo === 'selecionados' ? 'selecionados explicitamente' : 'registros (escopo completo)'}.`}</p>
      {ids.length ? <pre className="whitespace-pre-wrap break-all text-xs">IDs selecionados: {ids.join(', ')}</pre> : null}
      <p className="text-xs text-gray-600">Limite de 100 registros completos, com todas as versões. O servidor revalida seleção e filtros; crescimento do escopo não produz truncamento. Não baixa mídias nem documentos KYC.</p>
      {scopeCount > 100 ? <p role="status">Escopo acima de 100. Refine a consulta; nenhum relatório parcial será gerado.</p> : null}
      {!validSelection ? <p role="status">Seleção vazia, inválida ou incompatível com a sessão. Volte à consulta e escolha o escopo; uma seleção inválida nunca será tratada como todos os registros.</p> : null}
      <Button type="button" disabled={total == null || scopeCount > 100 || loading || !validSelection} onClick={() => void prepare()}>Preparar relatório completo</Button>
    </div> : null}
    {loading ? <p className="no-print" role="status">Consultando o servidor...</p> : null}
    {error ? <div className="no-print"><ContractState error={error} /></div> : null}
    {report && canExport ? <>
      <div className="no-print flex flex-wrap items-center gap-3"><p role="status">Relatório preparado. Impressão ou recebimento não foram comprovados.</p><Button type="button" onClick={() => window.print()}>Imprimir / salvar PDF</Button><Button type="button" variant="outline" onClick={saveJson}>Baixar JSON do relatório preparado</Button></div>
      <div className="record-report space-y-4">
        <div className="rounded-lg border bg-white p-4"><h2 className="text-xl font-semibold">Relatório de {report.tipo === 'STORY' ? 'Stories' : 'anúncios'} preservados</h2>
          <p>Gerado pelo servidor: {dateTime(report.geradoEm, report.fusoHorario)} · Fuso: {report.fusoHorario}</p>
          <p>Responsável: <span className="break-all">{report.responsavelId}</span></p>
          <p>Finalidade: {FINALIDADES_ACESSO_ARQUIVO.find((item) => item.codigo === report.finalidade)?.rotulo || report.finalidade}</p>
          <p>Escopo completo: {report.quantidade} registro(s), limite {report.limiteRegistros}. {report.idsSelecionados.length ? 'Seleção explícita.' : 'Todos os registros do filtro.'}</p>
          <h3 className="mt-3 font-semibold">Filtros efetivos e seleção</h3><FilterSummary filters={report.filtros} />
          {report.idsSelecionados.length ? <ul className="mt-2 space-y-1 text-xs">{report.idsSelecionados.map((id) => <li key={id} className="break-all">Registro selecionado: {id}</li>)}</ul> : <p className="text-sm">Todos os registros do filtro, sem truncamento.</p>}
          <h3 className="mt-3 font-semibold">Lacunas declaradas</h3><ul className="list-inside list-disc">{report.lacunas.map((gap, index) => <li key={index}>{gap}</li>)}</ul>
          <p className="mt-2 text-xs">Este relatório organiza capturas históricas disponíveis. Não certifica fatos ausentes, identidade, alcance não aferido, exclusão ou recebimento de arquivos.</p>
          <TechnicalFields value={report} known={['tipo', 'geradoEm', 'fusoHorario', 'responsavelId', 'finalidade', 'filtros', 'idsSelecionados', 'quantidade', 'limiteRegistros', 'lacunas', 'registros']} />
        </div>
        {report.registros.map((detail) => <RegistroContent key={detail.id} detail={detail} printable privateSnapshots timeZone={report.fusoHorario} />)}
      </div>
      <style>{`@page { size: A4; margin: 12mm; } @media print {
        .no-print, aside, nav { display: none !important; }
        .fixed:has(.record-report) { position: static !important; inset: auto !important; display: block !important; overflow: visible !important; color: #000 !important; background: #fff !important; }
        .fixed:has(.record-report) > div, .fixed:has(.record-report) > div > div { height: auto !important; overflow: visible !important; margin: 0 !important; padding: 0 !important; }
        .record-report { color: #000; } .record-version { break-inside: auto; }
        pre { overflow-wrap: anywhere; white-space: pre-wrap; } h3, h4, h5, h6, dt { break-after: avoid; } button { display: none !important; }
      }`}</style>
    </> : null}
  </section>
}
