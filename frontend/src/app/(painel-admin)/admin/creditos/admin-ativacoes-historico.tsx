'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { RefreshCw, Search } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  AdminCreditosApi,
  adminOperationKey,
  type AdminAuditoriaFinanceira,
  type AdminCreditoUsuario,
  type AdminPremiumAtivacao,
} from '@/lib/admin-creditos-operacionais-api'
import { dataHora, mensagemErro, rotuloOperacional } from './admin-monetizacao-utils'

const ATIVACOES_POR_PAGINA = 10
const DATA_SAO_PAULO = new Intl.DateTimeFormat('pt-BR', {
  timeZone: 'America/Sao_Paulo',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

function dataSaoPaulo(value: string) {
  const partes = Object.fromEntries(
    DATA_SAO_PAULO.formatToParts(new Date(value)).map((parte) => [parte.type, parte.value]),
  )
  return `${partes.year}-${partes.month}-${partes.day}`
}

export function AdminAtivacoesHistorico() {
  const [query, setQuery] = useState('')
  const [usuarios, setUsuarios] = useState<AdminCreditoUsuario[]>([])
  const [usuario, setUsuario] = useState<AdminCreditoUsuario | null>(null)
  const [ativacoes, setAtivacoes] = useState<AdminPremiumAtivacao[]>([])
  const [auditoria, setAuditoria] = useState<AdminAuditoriaFinanceira[]>([])
  const [filtroBeneficio, setFiltroBeneficio] = useState('')
  const [filtroAnuncio, setFiltroAnuncio] = useState('')
  const [filtroOrigem, setFiltroOrigem] = useState('TODAS')
  const [filtroStatus, setFiltroStatus] = useState('TODOS')
  const [periodoInicio, setPeriodoInicio] = useState('')
  const [periodoFim, setPeriodoFim] = useState('')
  const [pagina, setPagina] = useState(1)
  const [buscando, setBuscando] = useState(false)
  const [carregandoAtivacoes, setCarregandoAtivacoes] = useState(false)
  const [carregandoAuditoria, setCarregandoAuditoria] = useState(true)
  const [cancelandoId, setCancelandoId] = useState<string | null>(null)
  const [erroBusca, setErroBusca] = useState<string | null>(null)
  const [erroAtivacoes, setErroAtivacoes] = useState<string | null>(null)
  const [erroAuditoria, setErroAuditoria] = useState<string | null>(null)
  const buscaSeq = useRef(0)
  const ativacaoSeq = useRef(0)
  const cancelamentoLock = useRef(new Set<string>())
  const cancelamentoTentativas = useRef(new Map<string, string>())

  const carregarAuditoria = useCallback(async () => {
    setCarregandoAuditoria(true)
    setErroAuditoria(null)
    try {
      setAuditoria(await AdminCreditosApi.auditoria())
    } catch (error) {
      setErroAuditoria(mensagemErro(error, 'Não foi possível carregar a auditoria operacional.'))
    } finally {
      setCarregandoAuditoria(false)
    }
  }, [])

  useEffect(() => {
    void carregarAuditoria()
  }, [carregarAuditoria])

  const buscar = async () => {
    const termo = query.trim()
    if (termo.length < 2) {
      setErroBusca('Informe ao menos dois caracteres para localizar um usuário.')
      return
    }
    const seq = ++buscaSeq.current
    setBuscando(true)
    setErroBusca(null)
    try {
      const encontrados = await AdminCreditosApi.buscarUsuarios(termo)
      if (seq === buscaSeq.current) setUsuarios(encontrados)
    } catch (error) {
      if (seq === buscaSeq.current) setErroBusca(mensagemErro(error, 'Não foi possível buscar usuários.'))
    } finally {
      if (seq === buscaSeq.current) setBuscando(false)
    }
  }

  const selecionarUsuario = async (item: AdminCreditoUsuario) => {
    const seq = ++ativacaoSeq.current
    setUsuario(item)
    setAtivacoes([])
    setCarregandoAtivacoes(true)
    setErroAtivacoes(null)
    try {
      const itens = await AdminCreditosApi.ativacoes(item.id)
      if (seq === ativacaoSeq.current) setAtivacoes(itens)
    } catch (error) {
      if (seq === ativacaoSeq.current) {
        setErroAtivacoes(mensagemErro(error, 'Não foi possível carregar as ativações deste usuário.'))
      }
    } finally {
      if (seq === ativacaoSeq.current) setCarregandoAtivacoes(false)
    }
  }

  const cancelar = async (ativacao: AdminPremiumAtivacao) => {
    if (!usuario || cancelamentoLock.current.has(ativacao.id)) return
    const motivo = window.prompt('Motivo do cancelamento (mínimo de cinco caracteres):')?.trim() || ''
    if (motivo.length < 5) return
    cancelamentoLock.current.add(ativacao.id)
    setCancelandoId(ativacao.id)
    setErroAtivacoes(null)
    const assinatura = JSON.stringify([ativacao.id, motivo])
    const chave = cancelamentoTentativas.current.get(assinatura) ?? adminOperationKey('cancelamento')
    cancelamentoTentativas.current.set(assinatura, chave)
    try {
      await AdminCreditosApi.cancelarAtivacao(ativacao.id, motivo, chave)
      await Promise.all([selecionarUsuario(usuario), carregarAuditoria()])
      toast.success('Ativação cancelada; eventual estorno seguiu a regra canônica.')
      cancelamentoTentativas.current.delete(assinatura)
    } catch (error) {
      const message = mensagemErro(error, 'Não foi possível cancelar a ativação.')
      setErroAtivacoes(message)
      toast.error(message)
    } finally {
      cancelamentoLock.current.delete(ativacao.id)
      setCancelandoId(null)
    }
  }

  const filtradas = useMemo(() => {
    const beneficio = filtroBeneficio.trim().toLocaleLowerCase('pt-BR')
    const anuncio = filtroAnuncio.trim().toLocaleLowerCase('pt-BR')
    return ativacoes.filter((item) => {
      const correspondeBeneficio = !beneficio
        || (item.beneficioNome || '').toLocaleLowerCase('pt-BR').includes(beneficio)
        || (item.beneficioCodigo || '').toLocaleLowerCase('pt-BR').includes(beneficio)
      const correspondeAnuncio = !anuncio
        || item.anuncioId.toLocaleLowerCase('pt-BR').includes(anuncio)
      const correspondeOrigem = filtroOrigem === 'TODAS' || item.origem === filtroOrigem
      const correspondeStatus = filtroStatus === 'TODOS' || item.status === filtroStatus
      const dataInicio = dataSaoPaulo(item.inicioEm)
      const correspondePeriodo = (!periodoInicio || dataInicio >= periodoInicio)
        && (!periodoFim || dataInicio <= periodoFim)
      return correspondeBeneficio
        && correspondeAnuncio
        && correspondeOrigem
        && correspondeStatus
        && correspondePeriodo
    })
  }, [ativacoes, filtroAnuncio, filtroBeneficio, filtroOrigem, filtroStatus, periodoFim, periodoInicio])

  useEffect(() => {
    setPagina(1)
  }, [filtroAnuncio, filtroBeneficio, filtroOrigem, filtroStatus, periodoFim, periodoInicio, usuario?.id])

  const statusDisponiveis = useMemo(
    () => Array.from(new Set(ativacoes.map((item) => item.status))).sort(),
    [ativacoes],
  )
  const origensDisponiveis = useMemo(
    () => Array.from(new Set(ativacoes.map((item) => item.origem))).sort(),
    [ativacoes],
  )
  const totalPaginas = Math.max(1, Math.ceil(filtradas.length / ATIVACOES_POR_PAGINA))
  const paginaAtual = Math.min(pagina, totalPaginas)
  const inicioPagina = (paginaAtual - 1) * ATIVACOES_POR_PAGINA
  const paginadas = filtradas.slice(inicioPagina, inicioPagina + ATIVACOES_POR_PAGINA)

  return (
    <section className="min-w-0 space-y-6" aria-labelledby="ativacoes-title">
      <div>
        <h2 id="ativacoes-title" className="text-base font-semibold text-gray-900">Ativações e histórico</h2>
        <p className="mt-1 text-sm text-gray-500">Ativações, concessões e auditoria permanecem separadas dos movimentos do ledger.</p>
      </div>

      <section className="min-w-0 rounded-lg border border-gray-200 bg-white p-4 sm:p-5">
        <h3 className="font-semibold text-gray-900">Ativações Premium por usuário</h3>
        <form className="mt-4 grid min-w-0 gap-3 md:grid-cols-[minmax(0,1fr)_auto]" onSubmit={(event) => { event.preventDefault(); void buscar() }}>
          <Input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Buscar usuário por nome ou e-mail" aria-label="Buscar usuário para ativações" />
          <Button type="submit" disabled={buscando} aria-busy={buscando}><Search className="mr-2 size-4" aria-hidden="true" />{buscando ? 'Buscando...' : 'Buscar'}</Button>
        </form>
        {erroBusca ? <p className="mt-3 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{erroBusca}</p> : null}
        {!buscando && !erroBusca && query.trim().length >= 2 && usuarios.length === 0 ? <p className="mt-4 text-sm text-gray-500">Nenhum usuário localizado.</p> : null}
        {usuarios.length > 0 ? <div className="mt-3 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">{usuarios.map((item) => <button key={item.id} type="button" aria-pressed={usuario?.id === item.id} onClick={() => void selecionarUsuario(item)} className="min-w-0 rounded-md border border-gray-200 p-3 text-left transition hover:border-pink-300 hover:bg-pink-50 aria-pressed:border-pink-400 aria-pressed:bg-pink-50"><p className="truncate text-sm font-semibold text-gray-900">{item.nome}</p><p className="truncate text-xs text-gray-500">{item.email}</p></button>)}</div> : null}

        {usuario ? (
          <div className="mt-5 border-t border-gray-100 pt-5">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div><p className="font-medium text-gray-900">{usuario.nome}</p><p className="text-sm text-gray-500">As concessões ADMIN são operacionais e não constituem receita.</p></div>
              <Button type="button" variant="outline" size="icon" title="Atualizar ativações" aria-label="Atualizar ativações" disabled={carregandoAtivacoes} onClick={() => void selecionarUsuario(usuario)}><RefreshCw className={`size-4 ${carregandoAtivacoes ? 'animate-spin' : ''}`} aria-hidden="true" /></Button>
            </div>
            <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              <Input value={filtroBeneficio} onChange={(event) => setFiltroBeneficio(event.target.value)} placeholder="Filtrar por benefício" aria-label="Filtrar ativações por benefício" />
              <Input value={filtroAnuncio} onChange={(event) => setFiltroAnuncio(event.target.value)} placeholder="Filtrar por ID do anúncio" aria-label="Filtrar ativações por anúncio" />
              <select value={filtroOrigem} onChange={(event) => setFiltroOrigem(event.target.value)} className="h-10 rounded-md border border-gray-200 bg-white px-3 text-sm" aria-label="Filtrar ativações por origem"><option value="TODAS">Todas as origens</option>{origensDisponiveis.map((origem) => <option key={origem} value={origem}>{rotuloOperacional(origem)}</option>)}</select>
              <select value={filtroStatus} onChange={(event) => setFiltroStatus(event.target.value)} className="h-10 rounded-md border border-gray-200 bg-white px-3 text-sm" aria-label="Filtrar ativações por status"><option value="TODOS">Todos os status</option>{statusDisponiveis.map((status) => <option key={status} value={status}>{rotuloOperacional(status)}</option>)}</select>
              <label className="text-xs font-medium text-gray-600">Início do período
                <Input className="mt-1" type="date" value={periodoInicio} onChange={(event) => setPeriodoInicio(event.target.value)} />
              </label>
              <label className="text-xs font-medium text-gray-600">Fim do período
                <Input className="mt-1" type="date" value={periodoFim} onChange={(event) => setPeriodoFim(event.target.value)} />
              </label>
            </div>
            {carregandoAtivacoes ? <p className="mt-5 text-sm text-gray-500" role="status">Carregando ativações...</p> : null}
            {erroAtivacoes ? <div className="mt-4 rounded-md border border-red-200 bg-red-50 p-3" role="alert"><p className="text-sm text-red-700">{erroAtivacoes}</p><Button className="mt-3" size="sm" variant="outline" onClick={() => void selecionarUsuario(usuario)}>Tentar novamente</Button></div> : null}
            {!carregandoAtivacoes && !erroAtivacoes && ativacoes.length === 0 ? <p className="mt-5 text-sm text-gray-500">Nenhuma ativação registrada para este usuário.</p> : null}
            {!carregandoAtivacoes && !erroAtivacoes && ativacoes.length > 0 && filtradas.length === 0 ? <p className="mt-5 text-sm text-gray-500">Nenhuma ativação corresponde aos filtros.</p> : null}

            {paginadas.length > 0 ? <div className="mt-4 grid gap-3 lg:grid-cols-2">{paginadas.map((item) => <article key={item.id} className="min-w-0 rounded-md border border-gray-200 p-3"><div className="flex items-start justify-between gap-3"><div className="min-w-0"><h4 className="truncate font-semibold text-gray-900">{item.beneficioNome}</h4><p className="truncate text-xs text-gray-500">{item.beneficioCodigo}</p></div><span className="rounded-full bg-gray-100 px-2 py-1 text-xs font-medium text-gray-700">{rotuloOperacional(item.status)}</span></div><dl className="mt-3 grid grid-cols-2 gap-2 text-sm"><div><dt className="text-xs text-gray-500">Origem</dt><dd>{rotuloOperacional(item.origem)}{item.origem === 'ADMIN' ? ' (não é receita)' : ''}</dd></div><div><dt className="text-xs text-gray-500">Custo</dt><dd>{item.custoCreditos} créditos</dd></div><div><dt className="text-xs text-gray-500">Início</dt><dd>{dataHora(item.inicioEm)}</dd></div><div><dt className="text-xs text-gray-500">Fim</dt><dd>{dataHora(item.fimEm)}</dd></div></dl><p className="mt-2 truncate text-xs text-gray-500">Anúncio: {item.anuncioId}</p>{item.status === 'ATIVA' ? <Button className="mt-3 w-full sm:w-auto" size="sm" variant="outline" disabled={cancelandoId !== null} aria-busy={cancelandoId === item.id} onClick={() => void cancelar(item)}>{cancelandoId === item.id ? 'Cancelando...' : 'Cancelar/estornar'}</Button> : null}</article>)}</div> : null}
            {filtradas.length > 0 ? (
              <nav className="mt-4 flex flex-wrap items-center justify-between gap-3" aria-label="Paginação das ativações">
                <p className="text-xs text-gray-500">
                  {inicioPagina + 1}-{Math.min(inicioPagina + ATIVACOES_POR_PAGINA, filtradas.length)} de {filtradas.length}
                </p>
                <div className="flex gap-2">
                  <Button type="button" size="sm" variant="outline" disabled={paginaAtual === 1} onClick={() => setPagina((atual) => Math.max(1, atual - 1))}>Anterior</Button>
                  <Button type="button" size="sm" variant="outline" disabled={paginaAtual === totalPaginas} onClick={() => setPagina((atual) => Math.min(totalPaginas, atual + 1))}>Próxima</Button>
                </div>
              </nav>
            ) : null}
          </div>
        ) : <p className="mt-5 text-sm text-gray-500">Selecione um usuário para consultar suas ativações.</p>}
      </section>

      <section className="min-w-0 rounded-lg border border-gray-200 bg-white p-4 sm:p-5">
        <div className="flex flex-wrap items-start justify-between gap-3"><div><h3 className="font-semibold text-gray-900">Auditoria operacional</h3><p className="mt-1 text-sm text-gray-500">Compras, movimentos de crédito e concessões administrativas mantêm naturezas distintas.</p></div><Button type="button" variant="outline" size="icon" title="Atualizar auditoria" aria-label="Atualizar auditoria" disabled={carregandoAuditoria} onClick={() => void carregarAuditoria()}><RefreshCw className={`size-4 ${carregandoAuditoria ? 'animate-spin' : ''}`} aria-hidden="true" /></Button></div>
        {carregandoAuditoria ? <p className="mt-4 text-sm text-gray-500" role="status">Carregando auditoria...</p> : null}
        {erroAuditoria ? <div className="mt-4 rounded-md border border-red-200 bg-red-50 p-3" role="alert"><p className="text-sm text-red-700">{erroAuditoria}</p><Button className="mt-3" size="sm" variant="outline" onClick={() => void carregarAuditoria()}>Tentar novamente</Button></div> : null}
        {!carregandoAuditoria && !erroAuditoria && auditoria.length === 0 ? <p className="mt-4 text-sm text-gray-500">Nenhum evento de auditoria disponível.</p> : null}
        {auditoria.length > 0 ? <div className="mt-4 divide-y divide-gray-100">{auditoria.map((item) => <article key={item.id} className="min-w-0 py-3"><div className="flex flex-wrap items-start justify-between gap-2"><div className="min-w-0"><p className="font-medium text-gray-900">{rotuloOperacional(item.acao)}</p><p className="truncate text-sm text-gray-500">{rotuloOperacional(item.recursoTipo)}</p></div><time className="text-xs text-gray-500">{dataHora(item.criadoEm)}</time></div><p className="mt-1 truncate text-xs text-gray-500">Referência: {item.requestId || '-'}</p></article>)}</div> : null}
      </section>
    </section>
  )
}