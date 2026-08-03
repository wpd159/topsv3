'use client'

import { useEffect, useRef, useState } from 'react'
import { Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { PlanoCreditoManager } from '../components/plano-credito-manager'
import {
  AdminCreditosApi,
  type AdminAuditoriaFinanceira,
  type AdminCreditoMovimento,
  type AdminCreditoUsuario,
  type AdminPremiumAtivacao,
  type AdminPremiumCatalogo,
  type AdminPremiumCatalogoWrite,
} from '@/lib/admin-creditos-operacionais-api'

const ROTULOS: Record<string, string> = {
  AJUSTE_ADMIN_NEGATIVO: 'Ajuste negativo',
  AJUSTE_ADMIN_POSITIVO: 'Ajuste positivo',
  CREDITO: 'Credito',
  DEBITO: 'Debito',
  ESTORNO: 'Estorno',
  MIGRACAO_SALDO_INICIAL: 'Saldo inicial migrado',
  ATIVA: 'Ativa',
  CANCELADA: 'Cancelada',
  EXPIRADA: 'Expirada',
  ADMIN: 'Administrativa',
  COMPRA_CREDITOS: 'Compra com creditos',
  CORTESIA: 'Cortesia',
  CREDITO_ADMIN_AJUSTAR: 'Ajuste administrativo de creditos',
  CREDITO_ADMIN_ESTORNAR: 'Estorno administrativo de creditos',
  PREMIUM_CATALOGO_ATUALIZAR: 'Catalogo Premium atualizado',
  PREMIUM_CATALOGO_CRIAR: 'Catalogo Premium criado',
  PREMIUM_ATIVACAO_CANCELAR: 'Ativacao Premium cancelada',
  MOVIMENTO_CREDITO: 'Movimento de creditos',
  BENEFICIO_PREMIUM: 'Beneficio Premium',
  ATIVACAO_BENEFICIO: 'Ativacao de beneficio',
}

function rotuloOperacional(valor: string | null | undefined) {
  if (!valor) return '-'
  return ROTULOS[valor] ?? valor.toLowerCase().replaceAll('_', ' ').replace(/^./, (letra) => letra.toUpperCase())
}
const STORY_DRAFT_ID = 'catalogo-stories-novo'

type CatalogoOpcaoForm = {
  id: string
  persistida: boolean
  duracaoDias: string
  custoCreditos: string
  ativo: boolean
  ativoOriginal: boolean
  ordemExibicao: string
}

type CatalogoForm = {
  id: string
  codigo: string
  nome: string
  descricao: string
  ativo: boolean
  ativoOriginal: boolean
  ordemExibicao: string
  opcoes: CatalogoOpcaoForm[]
}

function catalogoForm(item: AdminPremiumCatalogo): CatalogoForm {
  return {
    ...item,
    ativoOriginal: item.ativo,
    ordemExibicao: String(item.ordemExibicao),
    opcoes: item.opcoes.map((opcao) => ({
      ...opcao,
      persistida: true,
      duracaoDias: String(opcao.duracaoDias),
      custoCreditos: String(opcao.custoCreditos),
      ativoOriginal: opcao.ativo,
      ordemExibicao: String(opcao.ordemExibicao),
    })),
  }
}

function storyDraft(): CatalogoForm {
  return {
    id: STORY_DRAFT_ID,
    codigo: 'STORIES',
    nome: 'Stories',
    descricao: '',
    ativo: false,
    ativoOriginal: false,
    ordemExibicao: '',
    opcoes: [],
  }
}

function catalogoComStory(items: AdminPremiumCatalogo[]) {
  const formularios = items.map(catalogoForm)
  return formularios.some((item) => item.codigo === 'STORIES')
    ? formularios
    : [...formularios, storyDraft()]
}

function novaOpcao(): CatalogoOpcaoForm {
  const sufixo = typeof crypto !== 'undefined' && crypto.randomUUID
    ? crypto.randomUUID()
    : `${Date.now()}`
  return {
    id: `nova-opcao-${sufixo}`,
    persistida: false,
    duracaoDias: '',
    custoCreditos: '',
    ativo: false,
    ativoOriginal: false,
    ordemExibicao: '',
  }
}

function inteiroFormulario(value: string, minimo: number, label: string) {
  if (!value.trim()) throw new Error(`${label} deve ser informado.`)
  const parsed = Number(value)
  if (!Number.isInteger(parsed) || parsed < minimo) {
    throw new Error(`${label} deve ser um numero inteiro maior ou igual a ${minimo}.`)
  }
  return parsed
}

function catalogoWrite(item: CatalogoForm): AdminPremiumCatalogoWrite {
  const nome = item.nome.trim()
  const descricao = item.descricao.trim()
  if (nome.length < 2) throw new Error('Informe um nome com ao menos dois caracteres.')
  if (descricao.length < 5) throw new Error('Informe uma descricao com ao menos cinco caracteres.')
  if (item.opcoes.length === 0) throw new Error('Cadastre ao menos uma opcao comercial.')

  const duracoes = new Set<number>()
  const opcoes = item.opcoes.map((opcao, index) => {
    const duracaoDias = inteiroFormulario(opcao.duracaoDias, 1, `Duracao da opcao ${index + 1}`)
    if (duracoes.has(duracaoDias)) {
      throw new Error(`Existe mais de uma opcao com ${duracaoDias} dias.`)
    }
    duracoes.add(duracaoDias)
    return {
      duracaoDias,
      custoCreditos: inteiroFormulario(opcao.custoCreditos, 0, `Custo da opcao ${index + 1}`),
      ativo: opcao.ativo,
      ordemExibicao: inteiroFormulario(opcao.ordemExibicao, 0, `Ordem da opcao ${index + 1}`),
    }
  })

  return {
    nome,
    descricao,
    ativo: item.ativo,
    ordemExibicao: inteiroFormulario(item.ordemExibicao, 0, 'Ordem do beneficio'),
    opcoes,
  }
}

function disponibilidadeCatalogoAlterada(item: CatalogoForm) {
  return item.ativo !== item.ativoOriginal
    || item.opcoes.some((opcao) => opcao.ativo !== opcao.ativoOriginal)
}

export default function AdminCreditosPage() {
  const [query, setQuery] = useState('')
  const [usuarios, setUsuarios] = useState<AdminCreditoUsuario[]>([])
  const [usuario, setUsuario] = useState<AdminCreditoUsuario | null>(null)
  const [movimentos, setMovimentos] = useState<AdminCreditoMovimento[]>([])
  const [ativacoes, setAtivacoes] = useState<AdminPremiumAtivacao[]>([])
  const [catalogo, setCatalogo] = useState<CatalogoForm[]>([])
  const [auditoria, setAuditoria] = useState<AdminAuditoriaFinanceira[]>([])
  const [direcao, setDirecao] = useState<'CREDITO' | 'DEBITO'>('CREDITO')
  const [quantidade, setQuantidade] = useState('')
  const [motivo, setMotivo] = useState('')
  const [busy, setBusy] = useState(false)
  const [catalogoSavingId, setCatalogoSavingId] = useState<string | null>(null)
  const [catalogoErros, setCatalogoErros] = useState<Record<string, string>>({})
  const catalogoSaveLock = useRef(false)
  const catalogoErrorRefs = useRef<Record<string, HTMLParagraphElement | null>>({})

  const carregarAdministracao = async () => {
    try {
      const [catalogoData, auditoriaData] = await Promise.all([
        AdminCreditosApi.catalogo(),
        AdminCreditosApi.auditoria(),
      ])
      setCatalogo(catalogoComStory(catalogoData))
      setAuditoria(auditoriaData)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Falha ao carregar administracao financeira.')
    }
  }

  useEffect(() => {
    void carregarAdministracao()
  }, [])

  const selecionarUsuario = async (item: AdminCreditoUsuario) => {
    setUsuario(item)
    try {
      const [saldo, pagina, ativacoesData] = await Promise.all([
        AdminCreditosApi.saldo(item.id),
        AdminCreditosApi.movimentos(item.id),
        AdminCreditosApi.ativacoes(item.id),
      ])
      setUsuario({ ...item, saldo: saldo.saldoCalculadoMovimentos })
      setMovimentos(pagina.itens)
      setAtivacoes(ativacoesData)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Falha ao carregar ledger do usuario.')
    }
  }

  const buscar = async () => {
    if (query.trim().length < 2) {
      toast.warning('Informe ao menos dois caracteres.')
      return
    }
    try {
      setUsuarios(await AdminCreditosApi.buscarUsuarios(query.trim()))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Falha ao buscar usuarios.')
    }
  }

  const ajustar = async () => {
    if (!usuario) return
    const valor = Number(quantidade)
    if (!Number.isInteger(valor) || valor <= 0 || motivo.trim().length < 5) {
      toast.warning('Informe quantidade positiva e motivo com ao menos cinco caracteres.')
      return
    }
    if (direcao === 'DEBITO' && !window.confirm(`Remover ${valor} creditos de ${usuario.nome}?`)) return
    try {
      setBusy(true)
      const resultado = await AdminCreditosApi.ajustar(usuario.id, direcao, valor, motivo.trim())
      toast.success(`Saldo atualizado para ${resultado.saldoPosterior} creditos.`)
      setQuantidade('')
      setMotivo('')
      await selecionarUsuario({ ...usuario, saldo: resultado.saldoPosterior })
      await carregarAdministracao()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Falha ao ajustar creditos.')
    } finally {
      setBusy(false)
    }
  }

  const estornar = async (movimento: AdminCreditoMovimento) => {
    const motivoEstorno = window.prompt('Motivo do estorno (minimo de cinco caracteres):')?.trim() || ''
    if (motivoEstorno.length < 5 || !usuario) return
    try {
      setBusy(true)
      await AdminCreditosApi.estornar(movimento.id, motivoEstorno)
      await selecionarUsuario(usuario)
      await carregarAdministracao()
      toast.success('Estorno registrado no ledger.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Falha ao estornar movimento.')
    } finally {
      setBusy(false)
    }
  }

  const cancelarAtivacao = async (ativacao: AdminPremiumAtivacao) => {
    const motivoCancelamento = window.prompt('Motivo do cancelamento (minimo de cinco caracteres):')?.trim() || ''
    if (motivoCancelamento.length < 5 || !usuario) return
    try {
      setBusy(true)
      await AdminCreditosApi.cancelarAtivacao(ativacao.id, motivoCancelamento)
      await selecionarUsuario(usuario)
      await carregarAdministracao()
      toast.success('Ativacao cancelada e estorno aplicado quando permitido.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Falha ao cancelar ativacao.')
    } finally {
      setBusy(false)
    }
  }

  const limparErroCatalogo = (id: string) => {
    setCatalogoErros((current) => {
      if (!current[id]) return current
      const next = { ...current }
      delete next[id]
      return next
    })
  }

  const atualizarCatalogoForm = (id: string, patch: Partial<CatalogoForm>) => {
    limparErroCatalogo(id)
    setCatalogo((current) => current.map((entry) => entry.id === id ? { ...entry, ...patch } : entry))
  }

  const atualizarOpcao = (
    itemId: string,
    opcaoId: string,
    patch: Partial<CatalogoOpcaoForm>,
  ) => {
    limparErroCatalogo(itemId)
    setCatalogo((current) => current.map((entry) => entry.id === itemId
      ? {
        ...entry,
        opcoes: entry.opcoes.map((opcao) => opcao.id === opcaoId ? { ...opcao, ...patch } : opcao),
      }
      : entry))
  }

  const adicionarOpcao = (itemId: string) => {
    limparErroCatalogo(itemId)
    setCatalogo((current) => current.map((entry) => entry.id === itemId
      ? { ...entry, opcoes: [...entry.opcoes, novaOpcao()] }
      : entry))
  }

  const removerOpcaoNova = (itemId: string, opcaoId: string) => {
    limparErroCatalogo(itemId)
    setCatalogo((current) => current.map((entry) => entry.id === itemId
      ? { ...entry, opcoes: entry.opcoes.filter((opcao) => opcao.id !== opcaoId || opcao.persistida) }
      : entry))
  }

  const salvarCatalogo = async (item: CatalogoForm) => {
    if (catalogoSaveLock.current) return
    catalogoSaveLock.current = true
    limparErroCatalogo(item.id)
    try {
      const payload = catalogoWrite(item)
      if (
        disponibilidadeCatalogoAlterada(item)
        && !window.confirm('Confirmar a alteracao de disponibilidade deste beneficio e de suas opcoes?')
      ) {
        return
      }
      setBusy(true)
      setCatalogoSavingId(item.id)
      const atualizado = item.id === STORY_DRAFT_ID
        ? await AdminCreditosApi.criarCatalogo({ codigo: 'STORIES', ...payload })
        : await AdminCreditosApi.atualizarCatalogo(item.id, payload)
      setCatalogo((current) => current.map((entry) =>
        entry.id === item.id ? catalogoForm(atualizado) : entry))
      toast.success(item.id === STORY_DRAFT_ID ? 'Beneficio criado.' : 'Beneficio atualizado.')
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Falha ao salvar beneficio.'
      setCatalogoErros((current) => ({ ...current, [item.id]: message }))
      toast.error(message)
      if (typeof requestAnimationFrame !== 'undefined') {
        requestAnimationFrame(() => catalogoErrorRefs.current[item.id]?.focus())
      }
    } finally {
      catalogoSaveLock.current = false
      setCatalogoSavingId(null)
      setBusy(false)
    }
  }

  return (
    <section className="space-y-8 pb-12">
      <header>
        <h1 className="text-2xl font-bold text-gray-900">Creditos e Premium</h1>
        <p className="mt-1 text-sm text-gray-500">Ledger, ajustes administrativos, catalogo e ativacoes em um unico fluxo.</p>
      </header>

      <div className="grid gap-6 xl:grid-cols-[360px_1fr]">
        <aside className="rounded-lg border border-gray-200 bg-white p-5">
          <h2 className="text-base font-semibold text-gray-900">Usuario</h2>
          <div className="mt-3 flex gap-2">
            <Input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Nome ou e-mail" />
            <Button onClick={() => void buscar()}>Buscar</Button>
          </div>
          <div className="mt-4 space-y-2">
            {usuarios.map((item) => (
              <button key={item.id} type="button" onClick={() => void selecionarUsuario(item)} className="w-full rounded-md border border-gray-200 p-3 text-left transition hover:border-pink-300 hover:bg-pink-50">
                <p className="text-sm font-semibold text-gray-900">{item.nome}</p>
                <p className="text-xs text-gray-500">{item.email}</p>
                <p className="mt-1 text-xs font-semibold text-[#C51683]">{item.saldo} creditos</p>
              </button>
            ))}
          </div>
        </aside>

        <div className="space-y-6">
          <section className="rounded-lg border border-gray-200 bg-white p-5">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 className="text-base font-semibold text-gray-900">Saldo e ajustes</h2>
                <p className="text-sm text-gray-500">{usuario ? `${usuario.nome}: ${usuario.saldo} creditos` : 'Selecione um usuario.'}</p>
              </div>
            </div>
            {usuario ? (
              <div className="mt-5 grid gap-3 md:grid-cols-[150px_150px_1fr_auto]">
                <select value={direcao} onChange={(event) => setDirecao(event.target.value as 'CREDITO' | 'DEBITO')} className="h-10 rounded-md border border-gray-200 px-3 text-sm">
                  <option value="CREDITO">Adicionar</option>
                  <option value="DEBITO">Remover</option>
                </select>
                <Input type="number" min={1} value={quantidade} onChange={(event) => setQuantidade(event.target.value)} placeholder="Quantidade" />
                <Input value={motivo} onChange={(event) => setMotivo(event.target.value)} placeholder="Motivo obrigatorio" />
                <Button disabled={busy} onClick={() => void ajustar()}>{busy ? 'Salvando...' : 'Confirmar'}</Button>
              </div>
            ) : null}
          </section>

          <section className="rounded-lg border border-gray-200 bg-white p-5">
            <h2 className="text-base font-semibold text-gray-900">Historico imutavel</h2>
            <div className="mt-4 overflow-x-auto">
              <table className="min-w-full text-left text-sm">
                <thead className="text-xs uppercase text-gray-500"><tr><th className="p-2">Natureza</th><th className="p-2">Quantidade</th><th className="p-2">Saldo</th><th className="p-2">Motivo</th><th className="p-2">Acao</th></tr></thead>
                <tbody>{movimentos.map((item) => <tr key={item.id} className="border-t border-gray-100"><td className="p-2">{rotuloOperacional(item.natureza)}</td><td className="p-2">{item.quantidade}</td><td className="p-2">{item.saldoAntes} → {item.saldoDepois}</td><td className="p-2">{item.motivo || '-'}</td><td className="p-2"><Button size="sm" variant="outline" disabled={busy || item.natureza === 'ESTORNO'} onClick={() => void estornar(item)}>Estornar</Button></td></tr>)}</tbody>
              </table>
            </div>
          </section>

          <section className="rounded-lg border border-gray-200 bg-white p-5">
            <h2 className="text-base font-semibold text-gray-900">Ativacoes Premium</h2>
            <div className="mt-4 space-y-2">
              {ativacoes.map((item) => <div key={item.id} className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-gray-200 p-3"><div><p className="text-sm font-semibold text-gray-900">{item.beneficioNome}</p><p className="text-xs text-gray-500">{rotuloOperacional(item.status)} · {rotuloOperacional(item.origem)} · {item.custoCreditos} creditos</p></div>{item.status === 'ATIVA' ? <Button size="sm" variant="outline" disabled={busy} onClick={() => void cancelarAtivacao(item)}>Cancelar/estornar</Button> : null}</div>)}
              {usuario && ativacoes.length === 0 ? <p className="text-sm text-gray-500">Nenhuma ativacao registrada.</p> : null}
            </div>
          </section>
        </div>
      </div>

      <section
        id="beneficios-premium"
        className="scroll-mt-6 rounded-lg border border-gray-200 bg-white p-5"
      >
        <h2 className="text-base font-semibold text-gray-900">Catalogo de beneficios</h2>
        <p className="mt-1 text-sm text-gray-500">Custos, duracoes e disponibilidade sao configurados aqui e aplicados pelo backend, sem valores implicitos.</p>
        <div className="mt-5 grid gap-4 lg:grid-cols-2">
          {catalogo.map((item) => (
            <div key={item.id} className="rounded-md border border-gray-200 p-4">
              <div className="mb-3 flex items-center justify-between gap-3">
                <p className="text-xs font-semibold uppercase text-gray-500">{item.codigo}</p>
                {item.id === STORY_DRAFT_ID ? (
                  <span className="text-xs font-medium text-amber-700">Ainda nao cadastrado</span>
                ) : null}
              </div>
              <div className="grid gap-3 sm:grid-cols-[1fr_90px_90px]">
                <Input
                  aria-label={`Nome do beneficio ${item.codigo}`}
                  value={item.nome}
                  onChange={(event) => atualizarCatalogoForm(item.id, { nome: event.target.value })}
                />
                <label className="text-xs text-gray-500">
                  Ordem
                  <Input
                    type="number"
                    min={0}
                    value={item.ordemExibicao}
                    onChange={(event) => atualizarCatalogoForm(item.id, { ordemExibicao: event.target.value })}
                  />
                </label>
                <label className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={item.ativo}
                    onChange={(event) => atualizarCatalogoForm(item.id, { ativo: event.target.checked })}
                  />
                  Ativo
                </label>
              </div>
              <textarea
                aria-label={`Descricao do beneficio ${item.codigo}`}
                value={item.descricao}
                onChange={(event) => atualizarCatalogoForm(item.id, { descricao: event.target.value })}
                className="mt-3 min-h-20 w-full rounded-md border border-gray-200 p-3 text-sm"
              />
              <div className="mt-4">
                {item.opcoes.map((opcao, index) => (
                  <div key={opcao.id} className="grid gap-2 border-t border-gray-100 py-3 sm:grid-cols-[1fr_1fr_90px_auto] sm:items-end">
                    <label className="text-xs text-gray-500">
                      Duracao (dias)
                      <Input
                        type="number"
                        min={1}
                        disabled={item.codigo !== 'STORIES'}
                        value={opcao.duracaoDias}
                        onChange={(event) => atualizarOpcao(item.id, opcao.id, { duracaoDias: event.target.value })}
                      />
                    </label>
                    <label className="text-xs text-gray-500">
                      Custo (creditos)
                      <Input
                        type="number"
                        min={0}
                        value={opcao.custoCreditos}
                        onChange={(event) => atualizarOpcao(item.id, opcao.id, { custoCreditos: event.target.value })}
                      />
                    </label>
                    <label className="text-xs text-gray-500">
                      Ordem
                      <Input
                        type="number"
                        min={0}
                        value={opcao.ordemExibicao}
                        onChange={(event) => atualizarOpcao(item.id, opcao.id, { ordemExibicao: event.target.value })}
                      />
                    </label>
                    <div className="flex min-h-10 items-center justify-between gap-2 sm:justify-end">
                      <label className="flex items-center gap-2 text-xs text-gray-600">
                        <input
                          type="checkbox"
                          checked={opcao.ativo}
                          onChange={(event) => atualizarOpcao(item.id, opcao.id, { ativo: event.target.checked })}
                        />
                        Disponivel
                      </label>
                      {!opcao.persistida ? (
                        <Button
                          type="button"
                          size="icon"
                          variant="ghost"
                          title={`Remover opcao ${index + 1}`}
                          aria-label={`Remover opcao ${index + 1}`}
                          onClick={() => removerOpcaoNova(item.id, opcao.id)}
                        >
                          <Trash2 className="size-4 text-red-600" aria-hidden="true" />
                        </Button>
                      ) : null}
                    </div>
                  </div>
                ))}
                {item.opcoes.length === 0 ? (
                  <p className="border-t border-gray-100 py-4 text-sm text-gray-500">Nenhuma opcao comercial cadastrada.</p>
                ) : null}
              </div>
              {catalogoErros[item.id] ? (
                <p
                  id={`catalogo-error-${item.id}`}
                  ref={(element) => { catalogoErrorRefs.current[item.id] = element }}
                  role="alert"
                  tabIndex={-1}
                  className="mt-3 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
                >
                  {catalogoErros[item.id]}
                </p>
              ) : null}
              <div className="mt-3 flex flex-wrap items-center justify-between gap-3">
                {item.codigo === 'STORIES' ? (
                  <Button type="button" variant="outline" onClick={() => adicionarOpcao(item.id)}>
                    <Plus className="mr-2 size-4" aria-hidden="true" />
                    Adicionar opcao
                  </Button>
                ) : <span />}
                <Button
                  aria-busy={catalogoSavingId === item.id}
                  aria-describedby={catalogoErros[item.id] ? `catalogo-error-${item.id}` : undefined}
                  disabled={busy}
                  onClick={() => void salvarCatalogo(item)}
                >
                  {catalogoSavingId === item.id
                    ? 'Salvando...'
                    : item.id === STORY_DRAFT_ID ? 'Criar beneficio' : 'Salvar beneficio'}
                </Button>
              </div>
            </div>
          ))}
        </div>
      </section>

      <PlanoCreditoManager />

      <section className="rounded-lg border border-gray-200 bg-white p-5">
        <h2 className="text-base font-semibold text-gray-900">Auditoria operacional</h2>
        <div className="mt-4 space-y-2">{auditoria.map((item) => <div key={item.id} className="rounded-md border border-gray-100 px-3 py-2 text-sm"><span className="font-semibold">{rotuloOperacional(item.acao)}</span><span className="ml-2 text-gray-500">{rotuloOperacional(item.recursoTipo)} · requestId {item.requestId || '-'}</span></div>)}</div>
      </section>
    </section>
  )
}
