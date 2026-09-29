'use client'

import Link from 'next/link'
import { useRouter, useSearchParams } from 'next/navigation'
import { useEffect, useMemo, useState } from 'react'
import { ContractState } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import {
  FINALIDADES_ACESSO_ARQUIVO, listarRegistrosPublicidade, listarRegistrosStory,
  type FinalidadeAcessoArquivo, type PublicidadeRegistrosPagina, type RegistroFamilia, type RegistroFiltros, type StoryRegistrosPagina,
} from '@/lib/admin-registros-api'
import { CurrentLinks, IdField, SessionNotice, useRegistroSession } from './record-content'
import { dateTime, label, listQuery, queryState } from './record-utils'

const INPUTS = [
  ['termo', 'Busca em título, slug, nome público ou identificadores'], ['anuncioId', 'ID do anúncio'],
  ['anuncianteId', 'ID do proprietário'], ['beneficio', 'Código do benefício'],
  ['inicio', 'Início do intervalo (ISO com fuso)'], ['fim', 'Fim do intervalo (ISO com fuso)'],
] as const
const inputClass = 'mt-1 w-full rounded border border-gray-300 bg-white px-3 py-2 text-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-pink-600'
const TRILHAS = [
  ['/admin/anuncios', 'Anúncios'], ['/admin/usuarios', 'Usuários'],
  ['/admin/creditos', 'Créditos e benefícios'], ['/admin/compliance#visitor-logs', 'Eventos de visitantes'],
]

export function AdminRegistrosList() {
  const router = useRouter(), search = useSearchParams()
  const rawQuery = search.toString()
  const { finalidade, filtros, familia, page } = useMemo(() => queryState(new URLSearchParams(rawQuery)), [rawQuery])
  const { state: session, retry, canExport } = useRegistroSession()
  const [draft, setDraft] = useState<RegistroFiltros>(filtros)
  const [purpose, setPurpose] = useState<FinalidadeAcessoArquivo | ''>(finalidade)
  const [result, setResult] = useState<PublicidadeRegistrosPagina | StoryRegistrosPagina | null>(null)
  const [error, setError] = useState<unknown>(null), [loading, setLoading] = useState(false)
  const [attempt, setAttempt] = useState(0), [selected, setSelected] = useState<string[]>([])
  const scopeKey = JSON.stringify([familia, finalidade, filtros])
  useEffect(() => { setDraft(filtros); setPurpose(finalidade) }, [filtros, finalidade])
  useEffect(() => { setSelected([]) }, [scopeKey])
  useEffect(() => {
    setResult(null); setError(null)
    if (session.status !== 'READY' || !finalidade) { setLoading(false); return }
    const controller = new AbortController()
    setLoading(true)
    const list = familia === 'publicidade' ? listarRegistrosPublicidade : listarRegistrosStory
    void list(page, finalidade, controller.signal, filtros).then((value) => { if (!controller.signal.aborted) setResult(value) })
      .catch((cause) => { if (!controller.signal.aborted) setError(cause) })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [session.status, page, finalidade, filtros, familia, attempt])
  const returnTo = '/admin/registros?' + listQuery(familia, page, finalidade, filtros)
  const reportQuery = new URLSearchParams(listQuery(familia, 0, finalidade, filtros))
  reportQuery.set('retorno', returnTo)
  selected.forEach((id) => reportQuery.append('id', id))
  const count = selected.length || result?.totalElements || 0
  function navigate(nextFamily: RegistroFamilia, nextPage: number, nextPurpose = finalidade, nextFilters = filtros) {
    router.push('/admin/registros?' + listQuery(nextFamily, nextPage, nextPurpose, nextFilters))
  }
  return <section className="space-y-5">
    <div><h1 className="text-2xl font-bold">Registros e arquivo de publicidade</h1>
      <p className="mt-1 text-sm text-gray-600">Consulta privada prospectiva. Capturas históricas não são reconstruídas a partir dos cadastros atuais.</p>
    </div>
    <SessionNotice state={session} retry={retry} />
    {session.status === 'READY' ? <>
      {!canExport ? <p className="text-sm text-amber-800">Sessão confirmada: consulta permitida; exportação, relatório e download de mídias exigem a permissão adicional de exportação.</p> : null}
      <form className="rounded-lg border bg-white p-4" onSubmit={(event) => { event.preventDefault(); navigate(familia, 0, purpose, draft) }}>
        <div className="grid gap-3 md:grid-cols-3">
          <label className="text-sm">Finalidade do acesso privado<select className={inputClass} value={purpose} onChange={(event) => setPurpose(event.target.value as FinalidadeAcessoArquivo | '')}>
            <option value="">Selecione antes de consultar</option>{FINALIDADES_ACESSO_ARQUIVO.map((item) => <option key={item.codigo} value={item.codigo}>{item.rotulo}</option>)}
          </select></label>
          {INPUTS.map(([name, text]) => <label key={name} className="text-sm">{text}<input className={inputClass} value={draft[name] || ''} onChange={(event) => setDraft((value) => ({ ...value, [name]: event.target.value }))} placeholder={name === 'inicio' || name === 'fim' ? '2026-09-01T00:00:00-03:00' : undefined} /></label>)}
          <label className="text-sm">Situação<select className={inputClass} value={draft.situacao || ''} onChange={(event) => setDraft((value) => ({ ...value, situacao: event.target.value as RegistroFiltros['situacao'] }))}>
            <option value="">Todas</option><option value="EM_VEICULACAO">Em veiculação</option><option value="ENCERRADA">Encerrada</option>
          </select></label>
          <label className="text-sm">Ordenação<select className={inputClass} value={draft.ordenacao || 'RECENTES'} onChange={(event) => setDraft((value) => ({ ...value, ordenacao: event.target.value as RegistroFiltros['ordenacao'] }))}>
            <option value="RECENTES">Mais recentes</option><option value="ANTIGOS">Mais antigos</option>
          </select></label>
        </div>
        <p className="mt-3 text-xs text-gray-600">Busca e filtros são aplicados no servidor a todas as páginas e versões. Período: sobreposição com início inclusivo e fim exclusivo. A finalidade é auditada em cada consulta/exportação.</p>
        <div className="mt-3 flex gap-2"><Button type="submit">Consultar</Button><Button type="button" variant="outline" onClick={() => navigate(familia, 0, finalidade, {})}>Limpar filtros</Button></div>
      </form>
      <nav className="flex flex-wrap gap-2" aria-label="Tipo de registro">
        {(['publicidade', 'stories'] as const).map((type) => <Link key={type} aria-current={familia === type ? 'page' : undefined} className={`rounded-lg border px-4 py-2 text-sm focus-visible:outline focus-visible:outline-2 ${familia === type ? 'bg-pink-100 font-semibold' : 'bg-white'}`} href={'/admin/registros?' + listQuery(type, 0, finalidade, filtros)}>{type === 'publicidade' ? 'Anúncios' : 'Stories'}</Link>)}
      </nav>
      <section className="space-y-3 rounded-lg border bg-white p-4" aria-labelledby="archive-list-title">
        <div className="flex flex-wrap items-center justify-between gap-2"><h2 id="archive-list-title" className="text-lg font-semibold">{familia === 'publicidade' ? 'Veiculações de anúncios' : 'Veiculações de Stories'}</h2>
          <Button type="button" variant="outline" disabled={!finalidade || loading} onClick={() => setAttempt((value) => value + 1)}>Atualizar consulta</Button></div>
        {!finalidade ? <p>Selecione a finalidade e consulte para carregar os registros.</p> : null}
        {loading ? <p role="status">Carregando registros...</p> : null}
        {error ? <ContractState error={error} onRetry={() => setAttempt((value) => value + 1)} /> : null}
        {result ? <>
          <p className="text-sm">{result.totalElements} registro(s) no filtro · Página {result.page + 1} de {Math.max(1, result.totalPages)} · Cada veiculação/ativação é mantida separadamente.</p>
          <div className="overflow-x-auto rounded border"><Table><TableHeader><TableRow>
            {canExport ? <TableHead>Selecionar</TableHead> : null}<TableHead>Identificação histórica</TableHead><TableHead>Benefício / estado</TableHead><TableHead>Período / preservação</TableHead><TableHead>Acesso</TableHead>
          </TableRow></TableHeader><TableBody>
            {result.itens.length === 0 ? <TableRow><TableCell colSpan={canExport ? 5 : 4}>Nenhum registro neste filtro/página. Isso não comprova ausência de histórico fora do escopo.</TableCell></TableRow> : null}
            {result.itens.map((item) => <TableRow key={item.id}>
              {canExport ? <TableCell><input type="checkbox" aria-label={`Selecionar registro ${item.id}`} checked={selected.includes(item.id)} disabled={!selected.includes(item.id) && selected.length >= 100} onChange={(event) => setSelected((ids) => event.target.checked ? [...ids, item.id] : ids.filter((id) => id !== item.id))} /></TableCell> : null}
              <TableCell><p className="font-medium">{item.titulo || 'Sem título capturado'}</p><p className="text-xs">{item.slug || 'Slug não registrado'} · Nome público histórico: {item.anuncianteNome || 'Não registrado na captura'}</p>
                <dl className="mt-2"><IdField name="Registro" value={item.id} /><IdField name="Anúncio" value={item.anuncioId} /><IdField name="Proprietário" value={item.anuncianteId} /></dl>
                {'storyId' in item ? <dl><IdField name="Story" value={item.storyId} /></dl> : null}
              </TableCell>
              <TableCell><p>{item.beneficioCodigo || 'Benefício não identificado'}</p><p>{label(item.status)}</p><p>{label(item.natureza)}</p><p>{item.totalVersoes} versão(ões)</p>{'modoConteudo' in item ? <p>{label(item.modoConteudo)}</p> : null}</TableCell>
              <TableCell><p>Início: {dateTime(item.inicioEm)}</p><p>{label(item.fimTipo)}: {dateTime(item.fimEm)}</p><p>{label(item.cobertura)}</p><p>Guarda mínima: {dateTime(item.retencaoAte)}</p><p>{item.preservacaoAtiva ? 'Preservação específica ativa' : 'Sem preservação específica informada'}</p></TableCell>
              <TableCell><Link className="font-medium text-pink-700 underline" href={`/admin/registros/${familia}/${encodeURIComponent(item.id)}?finalidade=${finalidade}&retorno=${encodeURIComponent(returnTo)}`}>Consultar captura</Link>
                <CurrentLinks adId={item.anuncioId} ownerId={item.anuncianteId} permissions={session.session.permissoes} />
              </TableCell>
            </TableRow>)}
          </TableBody></Table></div>
          <nav className="flex flex-wrap items-center justify-between gap-3" aria-label="Paginação dos registros"><span className="text-sm">Filtros e ordem preservados na URL.</span><div className="flex gap-3">
            {page > 0 ? <Link className="text-pink-700 underline" href={'/admin/registros?' + listQuery(familia, page - 1, finalidade, filtros)}>Anterior</Link> : <span className="text-gray-500">Anterior</span>}
            {!result.last ? <Link className="text-pink-700 underline" href={'/admin/registros?' + listQuery(familia, page + 1, finalidade, filtros)}>Próxima</Link> : <span className="text-gray-500">Próxima</span>}
          </div></nav>
          {canExport ? <div className="space-y-2 rounded border bg-gray-50 p-3 text-sm"><p>Escopo do relatório: {selected.length ? `${selected.length} registros selecionados (incluindo outras páginas deste filtro)` : `todos os ${result.totalElements} registros do filtro`}. Limite: 100; não há truncamento.</p>
            {count > 100 ? <p role="status">Refine os filtros ou selecione até 100 registros. O escopo completo não cabe no limite.</p> : <Link className="inline-block font-medium text-pink-700 underline" href={'/admin/registros/relatorio?' + reportQuery.toString()}>Conferir escopo e preparar relatório imprimível</Link>}
          </div> : null}
        </> : null}
      </section>
    </> : null}
    <section className="grid gap-3 sm:grid-cols-2" aria-label="Outras trilhas disponíveis">{TRILHAS.map(([href, text]) => <Link key={href} href={href} className="rounded-lg border bg-white p-4 hover:border-pink-300">{text}</Link>)}</section>
  </section>
}
