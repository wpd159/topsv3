'use client'

import { useEffect, useRef, useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { PlanoCreditoManager } from '../components/plano-credito-manager'
import { AdminStoryConfiguracaoCard } from './admin-story-configuracao-card'
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
  CREDITO: 'Crédito',
  DEBITO: 'Débito',
  ESTORNO: 'Estorno',
  MIGRACAO_SALDO_INICIAL: 'Saldo inicial migrado',
  ATIVA: 'Ativa',
  CANCELADA: 'Cancelada',
  EXPIRADA: 'Expirada',
  ADMIN: 'Administrativa',
  COMPRA_CREDITOS: 'Compra com créditos',
  CORTESIA: 'Cortesia',
  CREDITO_ADMIN_AJUSTAR: 'Ajuste administrativo de créditos',
  CREDITO_ADMIN_ESTORNAR: 'Estorno administrativo de créditos',
  PREMIUM_CATALOGO_ATUALIZAR: 'Catálogo Premium atualizado',
  PREMIUM_ATIVACAO_CANCELAR: 'Ativação Premium cancelada',
  STORY_CONFIGURACAO_ATUALIZAR: 'Configuração de Stories atualizada',
  MOVIMENTO_CREDITO: 'Movimento de créditos',
  BENEFICIO_PREMIUM: 'Benefício Premium',
  ATIVACAO_BENEFICIO: 'Ativação de benefício',
  STORY_CONFIGURACAO_COMERCIAL: 'Configuração comercial de Stories',
}

function rotuloOperacional(valor: string | null | undefined) {
  if (!valor) return '-'
  return ROTULOS[valor] ?? valor.toLowerCase().replaceAll('_', ' ').replace(/^./, (letra) => letra.toUpperCase())
}

type CatalogoOpcaoForm = {
  id: string
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
      duracaoDias: String(opcao.duracaoDias),
      custoCreditos: String(opcao.custoCreditos),
      ativoOriginal: opcao.ativo,
      ordemExibicao: String(opcao.ordemExibicao),
    })),
  }
}

function inteiroFormulario(value: string, minimo: number, label: string) {
  const parsed = Number(value)
  if (!value.trim() || !Number.isInteger(parsed) || parsed < minimo) {
    throw new Error(`${label} deve ser um número inteiro maior ou igual a ${minimo}.`)
  }
  return parsed
}

function catalogoWrite(item: CatalogoForm): AdminPremiumCatalogoWrite {
  const nome = item.nome.trim()
  const descricao = item.descricao.trim()
  if (nome.length < 2) throw new Error('Informe um nome com ao menos dois caracteres.')
  if (descricao.length < 5) throw new Error('Informe uma descrição com ao menos cinco caracteres.')
  if (item.opcoes.length === 0) throw new Error('O benefício precisa manter ao menos uma duração.')
  return {
    nome,
    descricao,
    ativo: item.ativo,
    ordemExibicao: inteiroFormulario(item.ordemExibicao, 0, 'Ordem do benefício'),
    opcoes: item.opcoes.map((opcao) => ({
      duracaoDias: inteiroFormulario(opcao.duracaoDias, 1, 'Duração'),
      custoCreditos: inteiroFormulario(opcao.custoCreditos, 0, 'Custo'),
      ativo: opcao.ativo,
      ordemExibicao: inteiroFormulario(opcao.ordemExibicao, 0, 'Ordem da duração'),
    })),
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

  const carregarAdministracao = async () => {
    try {
      const [catalogoData, auditoriaData] = await Promise.all([
        AdminCreditosApi.catalogo(),
        AdminCreditosApi.auditoria(),
      ])
      setCatalogo(catalogoData.map(catalogoForm))
      setAuditoria(auditoriaData)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Falha ao carregar a administração financeira.')
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
      toast.error(error instanceof Error ? error.message : 'Falha ao carregar o ledger do usuário.')
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
      toast.error(error instanceof Error ? error.message : 'Falha ao buscar usuários.')
    }
  }

  const ajustar = async () => {
    if (!usuario) return
    const valor = Number(quantidade)
    if (!Number.isInteger(valor) || valor <= 0 || motivo.trim().length < 5) {
      toast.warning('Informe quantidade positiva e motivo com ao menos cinco caracteres.')
      return
    }
    if (direcao === 'DEBITO' && !window.confirm(`Remover ${valor} créditos de ${usuario.nome}?`)) return
    try {
      setBusy(true)
      const resultado = await AdminCreditosApi.ajustar(usuario.id, direcao, valor, motivo.trim())
      setQuantidade('')
      setMotivo('')
      await selecionarUsuario({ ...usuario, saldo: resultado.saldoPosterior })
      await carregarAdministracao()
      toast.success(`Saldo atualizado para ${resultado.saldoPosterior} créditos.`)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Falha ao ajustar créditos.')
    } finally {
      setBusy(false)
    }
  }

  const estornar = async (movimento: AdminCreditoMovimento) => {
    const motivoEstorno = window.prompt('Motivo do estorno (mínimo de cinco caracteres):')?.trim() || ''
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
    const motivoCancelamento = window.prompt('Motivo do cancelamento (mínimo de cinco caracteres):')?.trim() || ''
    if (motivoCancelamento.length < 5 || !usuario) return
    try {
      setBusy(true)
      await AdminCreditosApi.cancelarAtivacao(ativacao.id, motivoCancelamento)
      await selecionarUsuario(usuario)
      await carregarAdministracao()
      toast.success('Ativação cancelada e estorno aplicado quando permitido.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Falha ao cancelar ativação.')
    } finally {
      setBusy(false)
    }
  }

  const atualizarCatalogoForm = (id: string, patch: Partial<CatalogoForm>) => {
    setCatalogoErros((current) => ({ ...current, [id]: '' }))
    setCatalogo((current) => current.map((entry) => entry.id === id ? { ...entry, ...patch } : entry))
  }

  const atualizarOpcao = (itemId: string, opcaoId: string, patch: Partial<CatalogoOpcaoForm>) => {
    setCatalogoErros((current) => ({ ...current, [itemId]: '' }))
    setCatalogo((current) => current.map((entry) => entry.id === itemId
      ? { ...entry, opcoes: entry.opcoes.map((opcao) => opcao.id === opcaoId ? { ...opcao, ...patch } : opcao) }
      : entry))
  }

  const salvarCatalogo = async (item: CatalogoForm) => {
    if (catalogoSaveLock.current) return
    catalogoSaveLock.current = true
    setCatalogoErros((current) => ({ ...current, [item.id]: '' }))
    try {
      const payload = catalogoWrite(item)
      if (disponibilidadeCatalogoAlterada(item)
          && !window.confirm('Confirmar a alteração de disponibilidade deste benefício?')) return
      setBusy(true)
      setCatalogoSavingId(item.id)
      const atualizado = await AdminCreditosApi.atualizarCatalogo(item.id, payload)
      setCatalogo((current) => current.map((entry) => entry.id === item.id ? catalogoForm(atualizado) : entry))
      toast.success('Benefício atualizado.')
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Falha ao salvar benefício.'
      setCatalogoErros((current) => ({ ...current, [item.id]: message }))
      toast.error(message)
    } finally {
      catalogoSaveLock.current = false
      setCatalogoSavingId(null)
      setBusy(false)
    }
  }

  return (
    <section className="space-y-6 pb-12">
      <header>
        <h1 className="text-2xl font-bold text-gray-900">Créditos, Premium e Stories</h1>
        <p className="mt-1 text-sm text-gray-500">Configurações comerciais e operações financeiras em áreas independentes.</p>
      </header>

      <Tabs defaultValue="stories" className="space-y-5">
        <div className="overflow-x-auto pb-1">
          <TabsList className="w-max min-w-full justify-start rounded-md">
            <TabsTrigger value="stories">Stories</TabsTrigger>
            <TabsTrigger value="catalogo">Pacotes e benefícios</TabsTrigger>
            <TabsTrigger value="saldos">Saldos e ajustes</TabsTrigger>
            <TabsTrigger value="historico">Ativações e histórico</TabsTrigger>
          </TabsList>
        </div>

        <TabsContent value="stories">
          <AdminStoryConfiguracaoCard />
        </TabsContent>

        <TabsContent value="catalogo" className="space-y-6">
          <section id="beneficios-premium" className="scroll-mt-6 rounded-lg border border-gray-200 bg-white p-5">
            <h2 className="text-base font-semibold text-gray-900">Catálogo de benefícios Premium</h2>
            <p className="mt-1 text-sm text-gray-500">Stories não faz parte deste catálogo.</p>
            <div className="mt-5 grid gap-4 lg:grid-cols-2">
              {catalogo.map((item) => (
                <div key={item.id} className="rounded-md border border-gray-200 p-4">
                  <p className="mb-3 text-xs font-semibold uppercase text-gray-500">{item.codigo}</p>
                  <div className="grid gap-3 sm:grid-cols-[1fr_90px_90px]">
                    <Input aria-label={`Nome do benefício ${item.codigo}`} value={item.nome} onChange={(event) => atualizarCatalogoForm(item.id, { nome: event.target.value })} />
                    <label className="text-xs text-gray-500">Ordem<Input type="number" min={0} value={item.ordemExibicao} onChange={(event) => atualizarCatalogoForm(item.id, { ordemExibicao: event.target.value })} /></label>
                    <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={item.ativo} onChange={(event) => atualizarCatalogoForm(item.id, { ativo: event.target.checked })} />Ativo</label>
                  </div>
                  <textarea aria-label={`Descrição do benefício ${item.codigo}`} value={item.descricao} onChange={(event) => atualizarCatalogoForm(item.id, { descricao: event.target.value })} className="mt-3 min-h-20 w-full rounded-md border border-gray-200 p-3 text-sm" />
                  <div className="mt-4">
                    {item.opcoes.map((opcao) => (
                      <div key={opcao.id} className="grid gap-2 border-t border-gray-100 py-3 sm:grid-cols-[1fr_1fr_90px_auto] sm:items-end">
                        <label className="text-xs text-gray-500">Duração (dias)<Input type="number" value={opcao.duracaoDias} disabled /></label>
                        <label className="text-xs text-gray-500">Custo (créditos)<Input type="number" min={0} value={opcao.custoCreditos} onChange={(event) => atualizarOpcao(item.id, opcao.id, { custoCreditos: event.target.value })} /></label>
                        <label className="text-xs text-gray-500">Ordem<Input type="number" min={0} value={opcao.ordemExibicao} onChange={(event) => atualizarOpcao(item.id, opcao.id, { ordemExibicao: event.target.value })} /></label>
                        <label className="flex min-h-10 items-center gap-2 text-xs text-gray-600"><input type="checkbox" checked={opcao.ativo} onChange={(event) => atualizarOpcao(item.id, opcao.id, { ativo: event.target.checked })} />Disponível</label>
                      </div>
                    ))}
                  </div>
                  {catalogoErros[item.id] ? <p className="mt-3 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{catalogoErros[item.id]}</p> : null}
                  <div className="mt-3 flex justify-end"><Button aria-busy={catalogoSavingId === item.id} disabled={busy} onClick={() => void salvarCatalogo(item)}>{catalogoSavingId === item.id ? 'Salvando...' : 'Salvar benefício'}</Button></div>
                </div>
              ))}
              {catalogo.length === 0 ? <p className="text-sm text-gray-500">Nenhum benefício Premium cadastrado.</p> : null}
            </div>
          </section>
          <PlanoCreditoManager />
        </TabsContent>

        <TabsContent value="saldos">
          <div className="grid gap-6 xl:grid-cols-[360px_1fr]">
            <aside className="rounded-lg border border-gray-200 bg-white p-5">
              <h2 className="text-base font-semibold text-gray-900">Usuário</h2>
              <div className="mt-3 flex gap-2"><Input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Nome ou e-mail" /><Button onClick={() => void buscar()}>Buscar</Button></div>
              <div className="mt-4 space-y-2">{usuarios.map((item) => <button key={item.id} type="button" onClick={() => void selecionarUsuario(item)} className="w-full rounded-md border border-gray-200 p-3 text-left transition hover:border-pink-300 hover:bg-pink-50"><p className="text-sm font-semibold text-gray-900">{item.nome}</p><p className="text-xs text-gray-500">{item.email}</p><p className="mt-1 text-xs font-semibold text-[#C51683]">{item.saldo} créditos</p></button>)}</div>
            </aside>
            <div className="space-y-6">
              <section className="rounded-lg border border-gray-200 bg-white p-5">
                <h2 className="text-base font-semibold text-gray-900">Saldo e ajustes</h2>
                <p className="mt-1 text-sm text-gray-500">{usuario ? `${usuario.nome}: ${usuario.saldo} créditos` : 'Selecione um usuário.'}</p>
                {usuario ? <div className="mt-5 grid gap-3 md:grid-cols-[150px_150px_1fr_auto]"><select value={direcao} onChange={(event) => setDirecao(event.target.value as 'CREDITO' | 'DEBITO')} className="h-10 rounded-md border border-gray-200 px-3 text-sm"><option value="CREDITO">Adicionar</option><option value="DEBITO">Remover</option></select><Input type="number" min={1} value={quantidade} onChange={(event) => setQuantidade(event.target.value)} placeholder="Quantidade" /><Input value={motivo} onChange={(event) => setMotivo(event.target.value)} placeholder="Motivo obrigatório" /><Button disabled={busy} onClick={() => void ajustar()}>{busy ? 'Salvando...' : 'Confirmar'}</Button></div> : null}
              </section>
              <section className="rounded-lg border border-gray-200 bg-white p-5"><h2 className="text-base font-semibold text-gray-900">Histórico imutável</h2><div className="mt-4 overflow-x-auto"><table className="min-w-full text-left text-sm"><thead className="text-xs uppercase text-gray-500"><tr><th className="p-2">Natureza</th><th className="p-2">Quantidade</th><th className="p-2">Saldo</th><th className="p-2">Motivo</th><th className="p-2">Ação</th></tr></thead><tbody>{movimentos.map((item) => <tr key={item.id} className="border-t border-gray-100"><td className="p-2">{rotuloOperacional(item.natureza)}</td><td className="p-2">{item.quantidade}</td><td className="p-2">{item.saldoAntes} → {item.saldoDepois}</td><td className="p-2">{item.motivo || '-'}</td><td className="p-2"><Button size="sm" variant="outline" disabled={busy || item.natureza === 'ESTORNO'} onClick={() => void estornar(item)}>Estornar</Button></td></tr>)}</tbody></table></div></section>
            </div>
          </div>
        </TabsContent>

        <TabsContent value="historico" className="space-y-6">
          <section className="rounded-lg border border-gray-200 bg-white p-5"><h2 className="text-base font-semibold text-gray-900">Ativações do usuário selecionado</h2><p className="mt-1 text-sm text-gray-500">{usuario ? usuario.nome : 'Selecione um usuário na aba Saldos e ajustes.'}</p><div className="mt-4 space-y-2">{ativacoes.map((item) => <div key={item.id} className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-gray-200 p-3"><div><p className="text-sm font-semibold text-gray-900">{item.beneficioNome}</p><p className="text-xs text-gray-500">{rotuloOperacional(item.status)} · {rotuloOperacional(item.origem)} · {item.custoCreditos} créditos</p></div>{item.status === 'ATIVA' ? <Button size="sm" variant="outline" disabled={busy} onClick={() => void cancelarAtivacao(item)}>Cancelar/estornar</Button> : null}</div>)}{usuario && ativacoes.length === 0 ? <p className="text-sm text-gray-500">Nenhuma ativação registrada.</p> : null}</div></section>
          <section className="rounded-lg border border-gray-200 bg-white p-5"><h2 className="text-base font-semibold text-gray-900">Auditoria operacional</h2><div className="mt-4 space-y-2">{auditoria.map((item) => <div key={item.id} className="rounded-md border border-gray-100 px-3 py-2 text-sm"><span className="font-semibold">{rotuloOperacional(item.acao)}</span><span className="ml-2 text-gray-500">{rotuloOperacional(item.recursoTipo)} · requestId {item.requestId || '-'}</span></div>)}</div></section>
        </TabsContent>
      </Tabs>
    </section>
  )
}
