'use client'

import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  AdminCreditosApi,
  type AdminAuditoriaFinanceira,
  type AdminCreditoMovimento,
  type AdminCreditoUsuario,
  type AdminPlanoCredito,
  type AdminPremiumAtivacao,
  type AdminPremiumCatalogo,
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
  PREMIUM_ATIVACAO_CANCELAR: 'Ativacao Premium cancelada',
  MOVIMENTO_CREDITO: 'Movimento de creditos',
  BENEFICIO_PREMIUM: 'Beneficio Premium',
  ATIVACAO_BENEFICIO: 'Ativacao de beneficio',
}

function rotuloOperacional(valor: string | null | undefined) {
  if (!valor) return '-'
  return ROTULOS[valor] ?? valor.toLowerCase().replaceAll('_', ' ').replace(/^./, (letra) => letra.toUpperCase())
}

export default function AdminCreditosPage() {
  const [query, setQuery] = useState('')
  const [usuarios, setUsuarios] = useState<AdminCreditoUsuario[]>([])
  const [usuario, setUsuario] = useState<AdminCreditoUsuario | null>(null)
  const [movimentos, setMovimentos] = useState<AdminCreditoMovimento[]>([])
  const [ativacoes, setAtivacoes] = useState<AdminPremiumAtivacao[]>([])
  const [catalogo, setCatalogo] = useState<AdminPremiumCatalogo[]>([])
  const [pacotes, setPacotes] = useState<AdminPlanoCredito[]>([])
  const [auditoria, setAuditoria] = useState<AdminAuditoriaFinanceira[]>([])
  const [direcao, setDirecao] = useState<'CREDITO' | 'DEBITO'>('CREDITO')
  const [quantidade, setQuantidade] = useState('')
  const [motivo, setMotivo] = useState('')
  const [busy, setBusy] = useState(false)

  const carregarAdministracao = async () => {
    try {
      const [catalogoData, pacotesData, auditoriaData] = await Promise.all([
        AdminCreditosApi.catalogo(),
        AdminCreditosApi.pacotes(),
        AdminCreditosApi.auditoria(),
      ])
      setCatalogo(catalogoData)
      setPacotes(pacotesData)
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

  const salvarCatalogo = async (item: AdminPremiumCatalogo) => {
    try {
      setBusy(true)
      const atualizado = await AdminCreditosApi.atualizarCatalogo(item)
      setCatalogo((current) => current.map((entry) => entry.id === item.id ? atualizado : entry))
      toast.success('Beneficio atualizado.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Falha ao atualizar beneficio.')
    } finally {
      setBusy(false)
    }
  }

  const salvarPacote = async (item: AdminPlanoCredito) => {
    try {
      setBusy(true)
      const atualizado = await AdminCreditosApi.atualizarPacote(item)
      setPacotes((current) => current.map((entry) => entry.id === item.id ? atualizado : entry))
      toast.success('Pacote atualizado sem iniciar cobranca externa.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Falha ao atualizar pacote.')
    } finally {
      setBusy(false)
    }
  }

  const duracoesDisponiveis = Array.from(new Set(
    catalogo.flatMap((item) => item.opcoes.map((opcao) => opcao.duracaoDias))
  )).sort((a, b) => a - b)

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
        <p className="mt-1 text-sm text-gray-500">
          Custos e duracoes {duracoesDisponiveis.length ? duracoesDisponiveis.join(', ') : 'disponiveis'} dias sao definidos pelo backend.
        </p>
        <div className="mt-5 grid gap-4 lg:grid-cols-2">
          {catalogo.map((item) => (
            <div key={item.id} className="rounded-md border border-gray-200 p-4">
              <div className="grid gap-3 sm:grid-cols-[1fr_90px_90px]">
                <Input value={item.nome} onChange={(event) => setCatalogo((current) => current.map((entry) => entry.id === item.id ? { ...entry, nome: event.target.value } : entry))} />
                <label className="text-xs text-gray-500">Ordem<Input type="number" min={0} value={item.ordemExibicao} onChange={(event) => setCatalogo((current) => current.map((entry) => entry.id === item.id ? { ...entry, ordemExibicao: Number(event.target.value) } : entry))} /></label>
                <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={item.ativo} onChange={(event) => setCatalogo((current) => current.map((entry) => entry.id === item.id ? { ...entry, ativo: event.target.checked } : entry))} />Ativo</label>
              </div>
              <textarea value={item.descricao} onChange={(event) => setCatalogo((current) => current.map((entry) => entry.id === item.id ? { ...entry, descricao: event.target.value } : entry))} className="mt-3 min-h-20 w-full rounded-md border border-gray-200 p-3 text-sm" />
              <div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-4">
                {item.opcoes.map((opcao) => (
                  <div key={opcao.id} className="space-y-2 rounded-md border border-gray-100 p-2">
                    <label className="text-xs text-gray-500">{opcao.duracaoDias} dias<Input type="number" min={0} value={opcao.custoCreditos} onChange={(event) => setCatalogo((current) => current.map((entry) => entry.id === item.id ? { ...entry, opcoes: entry.opcoes.map((value) => value.id === opcao.id ? { ...value, custoCreditos: Number(event.target.value) } : value) } : entry))} /></label>
                    <label className="flex items-center gap-2 text-xs text-gray-600"><input type="checkbox" checked={opcao.ativo} onChange={(event) => setCatalogo((current) => current.map((entry) => entry.id === item.id ? { ...entry, opcoes: entry.opcoes.map((value) => value.id === opcao.id ? { ...value, ativo: event.target.checked } : value) } : entry))} />Disponivel</label>
                  </div>
                ))}
              </div>
              <Button className="mt-4" disabled={busy} onClick={() => void salvarCatalogo(item)}>Salvar beneficio</Button>
            </div>
          ))}
        </div>
      </section>

      <section className="rounded-lg border border-gray-200 bg-white p-5">
        <h2 className="text-base font-semibold text-gray-900">Pacotes de credito</h2>
        <p className="mt-1 text-sm text-gray-500">Configuracao comercial sem checkout ou cobranca externa nesta fase.</p>
        <div className="mt-5 grid gap-4 lg:grid-cols-3">
          {pacotes.map((item) => <div key={item.id} className="space-y-3 rounded-md border border-gray-200 p-4"><Input value={item.nome} onChange={(event) => setPacotes((current) => current.map((entry) => entry.id === item.id ? { ...entry, nome: event.target.value } : entry))} /><Input value={item.descricao} onChange={(event) => setPacotes((current) => current.map((entry) => entry.id === item.id ? { ...entry, descricao: event.target.value } : entry))} /><div className="grid grid-cols-3 gap-2"><label className="text-xs text-gray-500">Creditos<Input type="number" min={1} value={item.quantidadeCreditos} onChange={(event) => setPacotes((current) => current.map((entry) => entry.id === item.id ? { ...entry, quantidadeCreditos: Number(event.target.value) } : entry))} /></label><label className="text-xs text-gray-500">Valor ref.<Input type="number" min={0} step="0.01" value={item.valor} onChange={(event) => setPacotes((current) => current.map((entry) => entry.id === item.id ? { ...entry, valor: Number(event.target.value) } : entry))} /></label><label className="text-xs text-gray-500">Ordem<Input type="number" min={0} value={item.ordemExibicao} onChange={(event) => setPacotes((current) => current.map((entry) => entry.id === item.id ? { ...entry, ordemExibicao: Number(event.target.value) } : entry))} /></label></div><label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={item.ativo} onChange={(event) => setPacotes((current) => current.map((entry) => entry.id === item.id ? { ...entry, ativo: event.target.checked } : entry))} />Ativo</label><Button disabled={busy} onClick={() => void salvarPacote(item)}>Salvar pacote</Button></div>)}
        </div>
      </section>

      <section className="rounded-lg border border-gray-200 bg-white p-5">
        <h2 className="text-base font-semibold text-gray-900">Auditoria operacional</h2>
        <div className="mt-4 space-y-2">{auditoria.map((item) => <div key={item.id} className="rounded-md border border-gray-100 px-3 py-2 text-sm"><span className="font-semibold">{rotuloOperacional(item.acao)}</span><span className="ml-2 text-gray-500">{rotuloOperacional(item.recursoTipo)} · requestId {item.requestId || '-'}</span></div>)}</div>
      </section>
    </section>
  )
}
