'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { RefreshCw, RotateCcw, Save } from 'lucide-react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { ApiContractError } from '@/lib/api-contract'
import {
  AdminCreditosApi,
  type AdminPremiumCatalogo as AdminPremiumCatalogoDto,
  type AdminPremiumCatalogoWrite,
} from '@/lib/admin-creditos-operacionais-api'
import { dataHora, mensagemErro } from './admin-monetizacao-utils'

const DURACOES = [1, 7, 14, 30] as const

type DuracaoCanonica = (typeof DURACOES)[number]

function duracaoCanonica(value: number): value is DuracaoCanonica {
  return DURACOES.includes(value as DuracaoCanonica)
}

type StatusFiltro = 'TODOS' | 'ATIVOS' | 'INATIVOS'
type OpcaoForm = {
  id: string | null
  cadastrada: boolean
  duracaoDias: number
  custoCreditos: string
  ativo: boolean
  ordemExibicao: string
}
type CatalogoForm = {
  id: string
  codigo: string
  nome: string
  descricao: string
  ativo: boolean
  ordemExibicao: string
  atualizadoEm: string
  opcoes: OpcaoForm[]
  opcoesLegadas: AdminPremiumCatalogoDto['opcoes']
}

function toForm(item: AdminPremiumCatalogoDto): CatalogoForm {
  const porDuracao = new Map(item.opcoes.map((opcao) => [opcao.duracaoDias, opcao]))
  return {
    id: item.id,
    codigo: item.codigo,
    nome: item.nome,
    descricao: item.descricao,
    ativo: item.ativo,
    ordemExibicao: String(item.ordemExibicao),
    atualizadoEm: item.atualizadoEm,
    opcoesLegadas: item.opcoes.filter((opcao) => !duracaoCanonica(opcao.duracaoDias)),
    opcoes: DURACOES.map((duracao) => {
      const opcao = porDuracao.get(duracao)
      return opcao
        ? {
            id: opcao.id,
            cadastrada: true,
            duracaoDias: duracao,
            custoCreditos: String(opcao.custoCreditos),
            ativo: opcao.ativo,
            ordemExibicao: String(opcao.ordemExibicao),
          }
        : {
            id: null,
            cadastrada: false,
            duracaoDias: duracao,
            custoCreditos: '',
            ativo: false,
            ordemExibicao: '',
          }
    }),
  }
}

function inteiro(value: string, minimo: number, label: string) {
  const parsed = Number(value)
  if (!value.trim() || !Number.isInteger(parsed) || parsed < minimo || parsed > 1_000_000) {
    throw new Error(`${label} deve ser um número inteiro entre ${minimo} e 1.000.000.`)
  }
  return parsed
}

function toWrite(item: CatalogoForm): AdminPremiumCatalogoWrite {
  const nome = item.nome.trim()
  const descricao = item.descricao.trim()
  if (nome.length < 2 || nome.length > 120) {
    throw new Error('O nome deve ter entre 2 e 120 caracteres.')
  }
  if (descricao.length < 5 || descricao.length > 500) {
    throw new Error('A descrição deve ter entre 5 e 500 caracteres.')
  }
  const opcoes = item.opcoes
    .filter((opcao) => opcao.cadastrada || opcao.custoCreditos.trim() || opcao.ordemExibicao.trim() || opcao.ativo)
    .map((opcao) => ({
      duracaoDias: opcao.duracaoDias,
      custoCreditos: inteiro(opcao.custoCreditos, 0, `Custo de ${opcao.duracaoDias} dias`),
      ativo: opcao.ativo,
      ordemExibicao: inteiro(opcao.ordemExibicao, 0, `Ordem de ${opcao.duracaoDias} dias`),
    }))
  if (opcoes.length === 0) {
    throw new Error('Cadastre ao menos uma opção comercial antes de salvar.')
  }
  return {
    nome,
    descricao,
    ativo: item.ativo,
    ordemExibicao: inteiro(item.ordemExibicao, 0, 'Ordem do benefício'),
    atualizadoEm: item.atualizadoEm,
    opcoes,
  }
}

function snapshot(item: CatalogoForm) {
  return JSON.stringify(item)
}

export function AdminPremiumCatalogo() {
  const [catalogo, setCatalogo] = useState<CatalogoForm[]>([])
  const [originais, setOriginais] = useState<Record<string, CatalogoForm>>({})
  const [busca, setBusca] = useState('')
  const [status, setStatus] = useState<StatusFiltro>('TODOS')
  const [loading, setLoading] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [salvando, setSalvando] = useState<Set<string>>(new Set())
  const [erros, setErros] = useState<Record<string, string>>({})
  const [sucessos, setSucessos] = useState<Record<string, string>>({})
  const locks = useRef(new Set<string>())

  const carregar = useCallback(async () => {
    setLoading(true)
    setErro(null)
    try {
      const itens = (await AdminCreditosApi.catalogo())
        .filter((item) => item.codigo !== 'STORIES')
        .map(toForm)
      setCatalogo(itens)
      setOriginais(Object.fromEntries(itens.map((item) => [item.id, structuredClone(item)])))
    } catch (error) {
      setErro(mensagemErro(error, 'Não foi possível carregar os benefícios Premium.'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void carregar()
  }, [carregar])

  const filtrados = useMemo(() => {
    const termo = busca.trim().toLocaleLowerCase('pt-BR')
    return catalogo.filter((item) => {
      const corresponde = !termo
        || item.nome.toLocaleLowerCase('pt-BR').includes(termo)
        || item.codigo.toLocaleLowerCase('pt-BR').includes(termo)
      const correspondeStatus = status === 'TODOS'
        || (status === 'ATIVOS' ? item.ativo : !item.ativo)
      return corresponde && correspondeStatus
    })
  }, [busca, catalogo, status])

  const atualizar = (id: string, patch: Partial<CatalogoForm>) => {
    setErros((atual) => ({ ...atual, [id]: '' }))
    setSucessos((atual) => ({ ...atual, [id]: '' }))
    setCatalogo((atual) => atual.map((item) => item.id === id ? { ...item, ...patch } : item))
  }

  const atualizarOpcao = (id: string, duracaoDias: number, patch: Partial<OpcaoForm>) => {
    setErros((atual) => ({ ...atual, [id]: '' }))
    setSucessos((atual) => ({ ...atual, [id]: '' }))
    setCatalogo((atual) => atual.map((item) => item.id === id
      ? {
          ...item,
          opcoes: item.opcoes.map((opcao) => opcao.duracaoDias === duracaoDias
            ? { ...opcao, ...patch }
            : opcao),
        }
      : item))
  }

  const restaurar = (id: string) => {
    const original = originais[id]
    if (!original || locks.current.has(id)) return
    setCatalogo((atual) => atual.map((item) => item.id === id ? structuredClone(original) : item))
    setErros((atual) => ({ ...atual, [id]: '' }))
    setSucessos((atual) => ({ ...atual, [id]: '' }))
  }

  const recarregarCard = async (id: string) => {
    const atual = (await AdminCreditosApi.catalogo())
      .filter((item) => item.codigo !== 'STORIES')
      .find((item) => item.id === id)
    if (!atual) throw new Error('O benefício não está mais disponível no catálogo.')
    const form = toForm(atual)
    setCatalogo((itens) => itens.map((item) => item.id === id ? form : item))
    setOriginais((itens) => ({ ...itens, [id]: structuredClone(form) }))
  }

  const salvar = async (item: CatalogoForm) => {
    if (locks.current.has(item.id)) return
    locks.current.add(item.id)
    setSalvando((atual) => new Set(atual).add(item.id))
    setErros((atual) => ({ ...atual, [item.id]: '' }))
    setSucessos((atual) => ({ ...atual, [item.id]: '' }))
    try {
      const payload = toWrite(item)
      const original = originais[item.id]
      const disponibilidadeAlterada = original && (
        original.ativo !== item.ativo
        || original.opcoes.some((opcao, index) => opcao.ativo !== item.opcoes[index]?.ativo)
      )
      if (disponibilidadeAlterada && !window.confirm(
        `Confirmar a alteração de disponibilidade de ${item.nome}?`,
      )) return
      const atualizado = toForm(await AdminCreditosApi.atualizarCatalogo(item.id, payload))
      setCatalogo((itens) => itens.map((atual) => atual.id === item.id ? atualizado : atual))
      setOriginais((itens) => ({ ...itens, [item.id]: structuredClone(atualizado) }))
      setSucessos((atual) => ({ ...atual, [item.id]: `${atualizado.nome} foi atualizado.` }))
      toast.success(`${atualizado.nome} foi atualizado.`)
    } catch (error) {
      if (error instanceof ApiContractError && error.status === 409) {
        try {
          await recarregarCard(item.id)
          const message = 'Os dados deste benefício mudaram em outra sessão. O card foi recarregado; revise e confirme novamente.'
          setErros((atual) => ({ ...atual, [item.id]: message }))
          toast.error(message)
        } catch (reloadError) {
          setErros((atual) => ({
            ...atual,
            [item.id]: mensagemErro(reloadError, 'Houve conflito e não foi possível recarregar o benefício.'),
          }))
        }
      } else {
        const message = mensagemErro(error, `Não foi possível salvar ${item.nome}.`)
        setErros((atual) => ({ ...atual, [item.id]: message }))
        toast.error(message)
      }
    } finally {
      locks.current.delete(item.id)
      setSalvando((atual) => {
        const next = new Set(atual)
        next.delete(item.id)
        return next
      })
    }
  }

  if (loading) {
    return <p className="py-10 text-center text-sm text-gray-500" role="status">Carregando benefícios Premium...</p>
  }

  if (erro) {
    return (
      <div className="rounded-md border border-red-200 bg-red-50 p-4" role="alert">
        <p className="text-sm text-red-800">{erro}</p>
        <Button className="mt-3" size="sm" variant="outline" onClick={() => void carregar()}>
          <RefreshCw className="mr-2 size-4" aria-hidden="true" />
          Tentar novamente
        </Button>
      </div>
    )
  }

  return (
    <section className="min-w-0 space-y-5" aria-labelledby="premium-catalogo-title">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 id="premium-catalogo-title" className="text-base font-semibold text-gray-900">Benefícios Premium</h2>
          <p className="mt-1 text-sm text-gray-500">Cada benefício é salvo separadamente. Stories possui configuração própria.</p>
        </div>
        <Button type="button" variant="outline" size="icon" aria-label="Atualizar benefícios" title="Atualizar benefícios" onClick={() => void carregar()}>
          <RefreshCw className="size-4" aria-hidden="true" />
        </Button>
      </div>

      <div className="grid gap-3 md:grid-cols-[minmax(0,1fr)_200px]">
        <Input value={busca} onChange={(event) => setBusca(event.target.value)} placeholder="Buscar benefício ou código" aria-label="Buscar benefícios Premium" />
        <select value={status} onChange={(event) => setStatus(event.target.value as StatusFiltro)} className="h-10 rounded-md border border-gray-200 bg-white px-3 text-sm" aria-label="Filtrar benefícios por status">
          <option value="TODOS">Todos os status</option>
          <option value="ATIVOS">Ativos</option>
          <option value="INATIVOS">Inativos</option>
        </select>
      </div>

      {filtrados.length === 0 ? (
        <p className="rounded-md border border-gray-200 bg-white px-4 py-10 text-center text-sm text-gray-500">Nenhum benefício Premium encontrado.</p>
      ) : (
        <div className="grid min-w-0 gap-4 lg:grid-cols-2">
          {filtrados.map((item) => {
            const dirty = snapshot(item) !== snapshot(originais[item.id])
            const isSaving = salvando.has(item.id)
            const possuiOpcaoCanonica = item.opcoes.some((opcao) => opcao.cadastrada)
            return (
              <details key={item.id} className="min-w-0 rounded-lg border border-gray-200 bg-white">
                <summary className="cursor-pointer list-none px-4 py-4 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-pink-500">
                  <div className="flex min-w-0 items-start justify-between gap-3">
                    <div className="min-w-0">
                      <h3 className="truncate font-semibold text-gray-900">{item.nome}</h3>
                      <p className="mt-0.5 truncate text-xs text-gray-500">{item.codigo}</p>
                    </div>
                    <Badge variant={item.ativo ? 'default' : 'secondary'}>{item.ativo ? 'Ativo' : 'Inativo'}</Badge>
                  </div>
                  <p className="mt-2 line-clamp-2 text-sm text-gray-600">{item.descricao}</p>
                </summary>

                <div className="border-t border-gray-100 px-4 py-4">
                  <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_100px]">
                    <label className="text-xs font-medium text-gray-600">Nome comercial
                      <Input className="mt-1" value={item.nome} maxLength={120} onChange={(event) => atualizar(item.id, { nome: event.target.value })} />
                    </label>
                    <label className="text-xs font-medium text-gray-600">Ordem
                      <Input className="mt-1" type="number" min={0} value={item.ordemExibicao} onChange={(event) => atualizar(item.id, { ordemExibicao: event.target.value })} />
                    </label>
                  </div>
                  <label className="mt-3 block text-xs font-medium text-gray-600">Descrição
                    <Textarea className="mt-1 min-h-20" value={item.descricao} maxLength={500} onChange={(event) => atualizar(item.id, { descricao: event.target.value })} />
                  </label>
                  <label className="mt-3 flex min-h-10 items-center gap-2 text-sm text-gray-700">
                    <input type="checkbox" checked={item.ativo} onChange={(event) => atualizar(item.id, { ativo: event.target.checked })} className="size-4 accent-[#C51683]" />
                    Benefício disponível para novas ativações
                  </label>

                  <div className="mt-4 border-t border-gray-100 pt-4">
                    <h4 className="text-sm font-semibold text-gray-900">Opções comerciais</h4>
                    {!possuiOpcaoCanonica ? <p className="mt-2 text-sm text-gray-500">Nenhuma opção canônica cadastrada.</p> : null}
                    <div className="mt-3 grid gap-3 sm:grid-cols-2">
                      {item.opcoes.map((opcao) => (
                        <fieldset key={opcao.duracaoDias} className="min-w-0 rounded-md border border-gray-200 p-3">
                          <legend className="px-1 text-sm font-semibold text-gray-900">{opcao.duracaoDias} {opcao.duracaoDias === 1 ? 'dia' : 'dias'}</legend>
                          {!opcao.cadastrada && !opcao.custoCreditos.trim() ? <p className="mb-2 text-xs text-gray-500">Ainda não cadastrada.</p> : null}
                          <label className="block text-xs font-medium text-gray-600">Custo em créditos
                            <Input className="mt-1" type="number" min={0} inputMode="numeric" value={opcao.custoCreditos} placeholder="Sem valor" onChange={(event) => atualizarOpcao(item.id, opcao.duracaoDias, { custoCreditos: event.target.value })} />
                          </label>
                          <label className="mt-2 block text-xs font-medium text-gray-600">Ordem
                            <Input className="mt-1" type="number" min={0} inputMode="numeric" value={opcao.ordemExibicao} placeholder="Sem ordem" onChange={(event) => atualizarOpcao(item.id, opcao.duracaoDias, { ordemExibicao: event.target.value })} />
                          </label>
                          <label className="mt-2 flex min-h-10 items-center gap-2 text-sm text-gray-700">
                            <input type="checkbox" checked={opcao.ativo} onChange={(event) => atualizarOpcao(item.id, opcao.duracaoDias, { ativo: event.target.checked })} className="size-4 accent-[#C51683]" />
                            Disponível
                          </label>
                        </fieldset>
                      ))}
                    </div>
                    {item.opcoesLegadas.length > 0 ? (
                      <div className="mt-4 rounded-md border border-amber-200 bg-amber-50 p-3">
                        <h5 className="text-sm font-semibold text-amber-900">Opções legadas preservadas</h5>
                        <p className="mt-1 text-xs text-amber-800">
                          Estas durações históricas permanecem inalteradas e não são convertidas ou excluídas por este formulário.
                        </p>
                        <div className="mt-3 grid gap-2 sm:grid-cols-2">
                          {item.opcoesLegadas.map((opcao) => (
                            <article key={opcao.id} className="rounded-md border border-amber-200 bg-white p-3 text-sm">
                              <div className="flex flex-wrap items-center justify-between gap-2">
                                <strong>{opcao.duracaoDias} dias</strong>
                                <Badge variant={opcao.ativo ? 'default' : 'secondary'}>
                                  {opcao.ativo ? 'Ativa' : 'Inativa'}
                                </Badge>
                              </div>
                              <p className="mt-2 text-gray-700">{opcao.custoCreditos} créditos</p>
                              <p className="mt-1 text-xs text-gray-500">Ordem {opcao.ordemExibicao}</p>
                            </article>
                          ))}
                        </div>
                      </div>
                    ) : null}
                  </div>

                  <div className="mt-4 min-h-5 text-sm" aria-live="polite">
                    {dirty ? <p className="font-medium text-amber-700" role="status">Alterações não salvas.</p> : null}
                    {!dirty && sucessos[item.id] ? <p className="font-medium text-emerald-700" role="status">{sucessos[item.id]}</p> : null}
                    {!dirty && !sucessos[item.id] ? <p className="text-gray-500">Última atualização: {dataHora(item.atualizadoEm)}</p> : null}
                  </div>
                  {erros[item.id] ? <p className="mt-3 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{erros[item.id]}</p> : null}

                  <div className="mt-4 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
                    <Button type="button" variant="outline" disabled={!dirty || isSaving} onClick={() => restaurar(item.id)}>
                      <RotateCcw className="mr-2 size-4" aria-hidden="true" />
                      Restaurar valores
                    </Button>
                    <Button type="button" disabled={!dirty || isSaving} aria-busy={isSaving} onClick={() => void salvar(item)}>
                      <Save className="mr-2 size-4" aria-hidden="true" />
                      {isSaving ? 'Salvando...' : 'Salvar benefício'}
                    </Button>
                  </div>
                </div>
              </details>
            )
          })}
        </div>
      )}
    </section>
  )
}