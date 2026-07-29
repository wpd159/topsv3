'use client'

import Link from 'next/link'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  BadgeCheck,
  Clock3,
  FileClock,
  FileX2,
  RefreshCw,
  Send,
  ShieldCheck,
  Users,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useLocalidades } from '@/hooks/useLocalidades'
import {
  fetchWizardProgressDashboard,
  type WizardProgressDashboardResponse,
  type WizardProgressFilters,
} from '@/lib/admin-wizard-progress-api'
import { cn } from '@/lib/utils'

const statusLabels: Record<string, string> = {
  EM_PREENCHIMENTO: 'Em preenchimento',
  AGUARDANDO_MODERACAO: 'Aguardando moderação',
  PUBLICADO: 'Publicado',
  REJEITADO: 'Rejeitado',
}

const modeLabels: Record<string, string> = {
  CREATE: 'Criação',
  EDIT: 'Edição',
}

const kycLabels: Record<string, string> = {
  NAO_INICIADO: 'Não iniciado',
  PENDENTE: 'Pendente',
  APROVADO: 'Aprovado',
  REJEITADO: 'Rejeitado',
}

function currentFilters(params: URLSearchParams): WizardProgressFilters {
  return {
    periodo: (params.get('periodo') as WizardProgressFilters['periodo']) || '30_DIAS',
    inicio: params.get('inicio') || undefined,
    fim: params.get('fim') || undefined,
    termo: params.get('termo') || undefined,
    modo: params.get('modo') || 'TODOS',
    status: params.get('status') || 'TODOS',
    uf: params.get('uf') || undefined,
    cidade: params.get('cidade') || undefined,
    kyc: params.get('kyc') || 'TODOS',
    anuncioStatus: params.get('anuncioStatus') || 'TODOS',
    page: Number(params.get('page') || 0),
    size: Number(params.get('size') || 20),
  }
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'short',
    timeZone: 'America/Sao_Paulo',
  }).format(new Date(value))
}

function statusTone(status: string) {
  if (status === 'PUBLICADO' || status === 'APROVADO') return 'border-emerald-200 bg-emerald-50 text-emerald-800'
  if (status === 'REJEITADO') return 'border-red-200 bg-red-50 text-red-800'
  if (status === 'AGUARDANDO_MODERACAO' || status === 'PENDENTE') return 'border-amber-200 bg-amber-50 text-amber-800'
  return 'border-zinc-200 bg-zinc-50 text-zinc-700'
}

export default function AdminWizardProgressPage() {
  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const query = searchParams.toString()
  const applied = useMemo(() => currentFilters(new URLSearchParams(query)), [query])
  const [draft, setDraft] = useState<WizardProgressFilters>(applied)
  const [data, setData] = useState<WizardProgressDashboardResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const {
    estados,
    cidades,
    setCidades,
    loadCidades,
  } = useLocalidades()

  useEffect(() => {
    setDraft(applied)
  }, [applied])

  useEffect(() => {
    if (!applied.uf) return
    void loadCidades(applied.uf)
  }, [applied.uf, loadCidades])

  const load = useCallback(async (signal?: AbortSignal) => {
    setLoading(true)
    setError(null)
    try {
      setData(await fetchWizardProgressDashboard(applied, signal))
    } catch (loadError) {
      if (signal?.aborted) return
      setError(loadError instanceof Error ? loadError.message : 'Não foi possível carregar o progresso.')
    } finally {
      if (!signal?.aborted) setLoading(false)
    }
  }, [applied])

  useEffect(() => {
    const controller = new AbortController()
    void load(controller.signal)
    return () => controller.abort()
  }, [load, reloadKey])

  const navigate = (next: WizardProgressFilters) => {
    const params = new URLSearchParams()
    Object.entries(next).forEach(([key, value]) => {
      if (value === undefined || value === null || value === '') return
      params.set(key, String(value))
    })
    router.push(`${pathname}?${params.toString()}`)
  }

  const applyFilters = () => {
    if (draft.periodo === 'PERSONALIZADO' && (!draft.inicio || !draft.fim)) {
      setError('Informe as datas inicial e final do período personalizado.')
      return
    }
    navigate({ ...draft, page: 0 })
  }

  const metrics = data ? [
    { label: 'Sessões observadas', value: data.indicadores.sessoesObservadas, icon: Clock3 },
    { label: 'Usuários observados', value: data.indicadores.usuariosObservados, icon: Users },
    { label: 'KYC não iniciado', value: data.indicadores.kycNaoIniciado, icon: ShieldCheck },
    { label: 'KYC pendente', value: data.indicadores.kycPendente, icon: FileClock },
    { label: 'KYC aprovado', value: data.indicadores.kycAprovado, icon: BadgeCheck },
    { label: 'Em preenchimento', value: data.indicadores.emPreenchimento, icon: Clock3 },
    { label: 'Aguardando moderação', value: data.indicadores.aguardandoModeracao, icon: Send },
    { label: 'Anúncios em rascunho', value: data.indicadores.anunciosRascunho, icon: FileClock },
    { label: 'Anúncios rejeitados', value: data.indicadores.anunciosRejeitados, icon: FileX2 },
    { label: 'Anúncios publicados', value: data.indicadores.anunciosPublicados, icon: BadgeCheck },
  ] : []

  return (
    <section className="space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-zinc-950">Progresso do wizard</h1>
          <p className="mt-1 text-sm text-zinc-600">
            Jornadas reais de criação e edição, calculadas no fuso America/Sao_Paulo.
          </p>
        </div>
        <Button
          type="button"
          variant="outline"
          disabled={loading}
          onClick={() => setReloadKey((value) => value + 1)}
        >
          <RefreshCw className={cn('size-4', loading && 'animate-spin')} />
          Atualizar
        </Button>
      </header>

      <div className="border-y border-zinc-200 py-4">
        <div className="grid gap-3 lg:grid-cols-4 xl:grid-cols-6">
          <Input
            className="lg:col-span-2"
            value={draft.termo || ''}
            onChange={(event) => setDraft((current) => ({ ...current, termo: event.target.value }))}
            placeholder="Buscar por usuário, e-mail, telefone ou slug"
          />
          <Select
            value={draft.periodo}
            onValueChange={(value) => setDraft((current) => ({
              ...current,
              periodo: value as WizardProgressFilters['periodo'],
            }))}
          >
            <SelectTrigger><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="HOJE">Hoje</SelectItem>
              <SelectItem value="7_DIAS">7 dias</SelectItem>
              <SelectItem value="30_DIAS">30 dias</SelectItem>
              <SelectItem value="PERSONALIZADO">Personalizado</SelectItem>
            </SelectContent>
          </Select>
          <Select
            value={draft.modo || 'TODOS'}
            onValueChange={(value) => setDraft((current) => ({ ...current, modo: value }))}
          >
            <SelectTrigger><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="TODOS">Todos os modos</SelectItem>
              <SelectItem value="CREATE">Criação</SelectItem>
              <SelectItem value="EDIT">Edição</SelectItem>
            </SelectContent>
          </Select>
          <Select
            value={draft.status || 'TODOS'}
            onValueChange={(value) => setDraft((current) => ({ ...current, status: value }))}
          >
            <SelectTrigger><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="TODOS">Todos os progressos</SelectItem>
              <SelectItem value="EM_PREENCHIMENTO">Em preenchimento</SelectItem>
              <SelectItem value="AGUARDANDO_MODERACAO">Aguardando moderação</SelectItem>
              <SelectItem value="PUBLICADO">Publicado</SelectItem>
              <SelectItem value="REJEITADO">Rejeitado</SelectItem>
            </SelectContent>
          </Select>
          <Select
            value={draft.kyc || 'TODOS'}
            onValueChange={(value) => setDraft((current) => ({ ...current, kyc: value }))}
          >
            <SelectTrigger><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="TODOS">Todos os KYC</SelectItem>
              <SelectItem value="NAO_INICIADO">KYC não iniciado</SelectItem>
              <SelectItem value="PENDENTE">KYC pendente</SelectItem>
              <SelectItem value="APROVADO">KYC aprovado</SelectItem>
              <SelectItem value="REJEITADO">KYC rejeitado</SelectItem>
            </SelectContent>
          </Select>
          <Select
            value={draft.uf || 'TODAS'}
            onValueChange={(value) => {
              const uf = value === 'TODAS' ? undefined : value
              setDraft((current) => ({ ...current, uf, cidade: undefined }))
              setCidades([])
              if (uf) void loadCidades(uf)
            }}
          >
            <SelectTrigger><SelectValue placeholder="Todas as UFs" /></SelectTrigger>
            <SelectContent>
              <SelectItem value="TODAS">Todas as UFs</SelectItem>
              {estados.map((estado) => (
                <SelectItem key={estado.id} value={estado.uf}>{estado.nome} · {estado.uf}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select
            value={draft.cidade || 'TODAS'}
            disabled={!draft.uf}
            onValueChange={(value) => setDraft((current) => ({
              ...current,
              cidade: value === 'TODAS' ? undefined : value,
            }))}
          >
            <SelectTrigger><SelectValue placeholder="Todas as cidades" /></SelectTrigger>
            <SelectContent>
              <SelectItem value="TODAS">Todas as cidades</SelectItem>
              {cidades.map((cidade) => (
                <SelectItem key={cidade.id} value={cidade.id}>{cidade.nome}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select
            value={draft.anuncioStatus || 'TODOS'}
            onValueChange={(value) => setDraft((current) => ({ ...current, anuncioStatus: value }))}
          >
            <SelectTrigger><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="TODOS">Todos os anúncios</SelectItem>
              <SelectItem value="SEM_ANUNCIO">Sem anúncio vinculado</SelectItem>
              <SelectItem value="RASCUNHO">Rascunho</SelectItem>
              <SelectItem value="PENDENTE_REVISAO">Pendente de revisão</SelectItem>
              <SelectItem value="PUBLICADO">Publicado</SelectItem>
              <SelectItem value="PAUSADO">Pausado</SelectItem>
              <SelectItem value="REJEITADO">Rejeitado</SelectItem>
              <SelectItem value="BLOQUEADO">Bloqueado</SelectItem>
              <SelectItem value="REMOVIDO">Removido</SelectItem>
            </SelectContent>
          </Select>
          <Select
            value={String(draft.size || 20)}
            onValueChange={(value) => setDraft((current) => ({ ...current, size: Number(value) }))}
          >
            <SelectTrigger><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="20">20 por página</SelectItem>
              <SelectItem value="30">30 por página</SelectItem>
              <SelectItem value="50">50 por página</SelectItem>
            </SelectContent>
          </Select>
          <Button type="button" onClick={applyFilters}>Aplicar filtros</Button>
        </div>
        {draft.periodo === 'PERSONALIZADO' ? (
          <div className="mt-3 grid max-w-xl gap-3 sm:grid-cols-2">
            <Input
              type="date"
              aria-label="Data inicial"
              value={draft.inicio || ''}
              onChange={(event) => setDraft((current) => ({ ...current, inicio: event.target.value }))}
            />
            <Input
              type="date"
              aria-label="Data final"
              value={draft.fim || ''}
              onChange={(event) => setDraft((current) => ({ ...current, fim: event.target.value }))}
            />
          </div>
        ) : null}
      </div>

      {error ? (
        <div className="flex flex-wrap items-center justify-between gap-3 border border-red-200 bg-red-50 p-4 text-sm text-red-800">
          <span>{error}</span>
          <Button type="button" variant="outline" onClick={() => setReloadKey((value) => value + 1)}>
            Tentar novamente
          </Button>
        </div>
      ) : null}

      {!data && loading ? (
        <div className="py-16 text-center text-sm text-zinc-600">Carregando progresso real...</div>
      ) : null}

      {data ? (
        <>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
            {metrics.map((metric) => {
              const Icon = metric.icon
              return (
                <article key={metric.label} className="rounded-md border border-zinc-200 bg-white p-4">
                  <div className="flex items-center justify-between gap-2">
                    <p className="text-xs font-medium text-zinc-600">{metric.label}</p>
                    <Icon className="size-4 text-pink-600" />
                  </div>
                  <p className="mt-2 text-2xl font-bold text-zinc-950">{metric.value.toLocaleString('pt-BR')}</p>
                </article>
              )
            })}
          </div>

          <div className="grid gap-8 border-y border-zinc-200 py-6 xl:grid-cols-2">
            <section>
              <h2 className="text-base font-semibold text-zinc-950">Funil por maior etapa alcançada</h2>
              <p className="mt-1 text-xs text-zinc-600">
                Conversão entre etapas realmente sincronizadas no período.
              </p>
              <ol className="mt-4 space-y-3">
                {data.funil.map((step) => {
                  const max = data.funil[0]?.quantidade || 0
                  const width = max === 0 ? 0 : Math.max(4, (step.quantidade / max) * 100)
                  return (
                    <li key={step.codigo}>
                      <div className="flex items-center justify-between gap-3 text-sm">
                        <span className="font-medium text-zinc-800">{step.rotulo}</span>
                        <span className="text-zinc-600">
                          {step.quantidade.toLocaleString('pt-BR')} · {step.conversaoPercentual.toFixed(1)}%
                        </span>
                      </div>
                      <div className="mt-1 h-2 overflow-hidden rounded-sm bg-zinc-100">
                        <div className="h-full bg-pink-500" style={{ width: `${width}%` }} />
                      </div>
                      {step.perda > 0 ? (
                        <p className="mt-1 text-xs text-zinc-500">Perda nesta transição: {step.perda}</p>
                      ) : null}
                    </li>
                  )
                })}
              </ol>
            </section>

            <section>
              <h2 className="text-base font-semibold text-zinc-950">Última etapa registrada</h2>
              <p className="mt-1 text-xs text-zinc-600">
                Distribuição das sessões pela atividade mais recente.
              </p>
              <div className="mt-4 divide-y divide-zinc-200 border-y border-zinc-200">
                {data.etapasAtuais.map((step) => (
                  <div key={step.codigo} className="flex items-center justify-between gap-3 py-3 text-sm">
                    <span className="text-zinc-700">{step.rotulo}</span>
                    <strong className="text-zinc-950">{step.quantidade.toLocaleString('pt-BR')}</strong>
                  </div>
                ))}
              </div>
              <p className="mt-4 text-xs text-zinc-500">
                Período: {data.periodo.inicio} a {data.periodo.fim}. Tempo médio de conclusão:{' '}
                {data.indicadores.tempoMedioConclusaoMinutos == null
                  ? 'não calculável no período'
                  : `${data.indicadores.tempoMedioConclusaoMinutos} min`}.
              </p>
            </section>
          </div>

          <section>
            <div className="flex flex-wrap items-end justify-between gap-3">
              <div>
                <h2 className="text-base font-semibold text-zinc-950">Jornadas observadas</h2>
                <p className="mt-1 text-xs text-zinc-600">
                  {data.progresso.totalElements.toLocaleString('pt-BR')} registro(s), sem dados pessoais completos.
                </p>
              </div>
              {loading ? <span className="text-xs text-zinc-500">Atualizando...</span> : null}
            </div>
            <div className="mt-4 overflow-x-auto border-y border-zinc-200">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Usuário</TableHead>
                    <TableHead>Modo</TableHead>
                    <TableHead>Progresso</TableHead>
                    <TableHead>Última etapa</TableHead>
                    <TableHead>KYC</TableHead>
                    <TableHead>Última atividade</TableHead>
                    <TableHead className="text-right">Ações</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.progresso.itens.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={7} className="py-12 text-center text-sm text-zinc-500">
                        Nenhuma jornada encontrada para os filtros selecionados.
                      </TableCell>
                    </TableRow>
                  ) : data.progresso.itens.map((item) => (
                    <TableRow key={item.id}>
                      <TableCell>
                        <p className="font-medium text-zinc-900">{item.usuario}</p>
                        {item.emailMascarado ? <p className="text-xs text-zinc-500">{item.emailMascarado}</p> : null}
                        {item.anuncioTitulo ? <p className="mt-1 text-xs text-zinc-500">{item.anuncioTitulo}</p> : null}
                      </TableCell>
                      <TableCell>{modeLabels[item.modo] || item.modo}</TableCell>
                      <TableCell>
                        <span className={cn('inline-flex rounded-sm border px-2 py-1 text-xs font-medium', statusTone(item.status))}>
                          {statusLabels[item.status] || item.status}
                        </span>
                      </TableCell>
                      <TableCell>{data.etapasAtuais.find((step) => step.codigo === item.ultimoStep)?.rotulo || item.ultimoStep}</TableCell>
                      <TableCell>
                        <span className={cn('inline-flex rounded-sm border px-2 py-1 text-xs font-medium', statusTone(item.kycStatus))}>
                          {kycLabels[item.kycStatus] || item.kycStatus}
                        </span>
                      </TableCell>
                      <TableCell>{formatDateTime(item.atualizadoEm)}</TableCell>
                      <TableCell>
                        <div className="flex min-w-max justify-end gap-2">
                          {item.status === 'PUBLICADO' && item.anuncioSlug ? (
                            <Button asChild size="sm" variant="outline">
                              <Link href={`/anuncios/${encodeURIComponent(item.anuncioSlug)}`}>Abrir anúncio</Link>
                            </Button>
                          ) : null}
                          {item.anuncioId ? (
                            <Button asChild size="sm" variant="outline">
                              <Link href={`/admin/anuncios/${item.anuncioId}`}>Abrir moderação</Link>
                            </Button>
                          ) : null}
                          <Button asChild size="sm" variant="outline">
                            <Link href={`/admin/usuarios/${item.usuarioId}`}>Editar usuário</Link>
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>

            {data.progresso.totalPages > 1 ? (
              <div className="mt-4 flex items-center justify-between gap-3">
                <Button
                  type="button"
                  variant="outline"
                  disabled={data.progresso.page <= 0}
                  onClick={() => navigate({ ...applied, page: Math.max(0, data.progresso.page - 1) })}
                >
                  Anterior
                </Button>
                <span className="text-sm text-zinc-600">
                  Página {data.progresso.page + 1} de {data.progresso.totalPages}
                </span>
                <Button
                  type="button"
                  variant="outline"
                  disabled={data.progresso.last}
                  onClick={() => navigate({ ...applied, page: data.progresso.page + 1 })}
                >
                  Próxima
                </Button>
              </div>
            ) : null}
          </section>
        </>
      ) : null}
    </section>
  )
}
