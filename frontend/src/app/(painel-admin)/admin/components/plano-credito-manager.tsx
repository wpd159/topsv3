'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import { Pencil, Plus, Power, PowerOff, RefreshCw } from 'lucide-react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import {
  AdminCreditosApi,
  type AdminPlanoCredito,
  type AdminPlanoCreditoAtualizar,
  type AdminPlanoCreditoCriar,
} from '@/lib/admin-creditos-operacionais-api'

type StatusFiltro = 'TODOS' | 'ATIVOS' | 'INATIVOS'
type PlanoForm = {
  codigo: string
  nome: string
  descricao: string
  quantidadeCreditos: string
  valor: string
  ordemExibicao: string
  ativo: boolean
  criadoEm: string
  atualizadoEm: string
}

const FORM_VAZIO: PlanoForm = {
  codigo: '',
  nome: '',
  descricao: '',
  quantidadeCreditos: '',
  valor: '',
  ordemExibicao: '0',
  ativo: false,
  criadoEm: '',
  atualizadoEm: '',
}

const reais = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
})

function toForm(plano: AdminPlanoCredito): PlanoForm {
  return {
    codigo: plano.codigo,
    nome: plano.nome,
    descricao: plano.descricao,
    quantidadeCreditos: String(plano.quantidadeCreditos),
    valor: plano.valor.toFixed(2).replace('.', ','),
    ordemExibicao: String(plano.ordemExibicao),
    ativo: plano.ativo,
    criadoEm: plano.criadoEm,
    atualizadoEm: plano.atualizadoEm,
  }
}

function parseValor(value: string) {
  return Number(value.trim().replace(/\s/g, '').replace(',', '.'))
}

export function PlanoCreditoManager() {
  const [planos, setPlanos] = useState<AdminPlanoCredito[]>([])
  const [busca, setBusca] = useState('')
  const [status, setStatus] = useState<StatusFiltro>('TODOS')
  const [loading, setLoading] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editandoId, setEditandoId] = useState<string | null>(null)
  const [form, setForm] = useState<PlanoForm>(FORM_VAZIO)
  const [salvando, setSalvando] = useState(false)
  const [alterandoStatusId, setAlterandoStatusId] = useState<string | null>(null)

  const carregar = useCallback(async () => {
    setLoading(true)
    setErro(null)
    try {
      setPlanos(await AdminCreditosApi.pacotes(busca.trim(), status))
    } catch (error) {
      setErro(error instanceof Error ? error.message : 'Nao foi possivel carregar os planos.')
    } finally {
      setLoading(false)
    }
  }, [busca, status])

  useEffect(() => {
    const timer = window.setTimeout(() => void carregar(), 250)
    return () => window.clearTimeout(timer)
  }, [carregar])

  const indicadores = useMemo(() => ({
    ativos: planos.filter((plano) => plano.ativo).length,
    inativos: planos.filter((plano) => !plano.ativo).length,
  }), [planos])

  const abrirCriacao = () => {
    setEditandoId(null)
    setForm(FORM_VAZIO)
    setDialogOpen(true)
  }

  const abrirEdicao = async (id: string) => {
    setAlterandoStatusId(id)
    try {
      const plano = await AdminCreditosApi.detalharPacote(id)
      setEditandoId(id)
      setForm(toForm(plano))
      setDialogOpen(true)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Nao foi possivel abrir o plano.')
    } finally {
      setAlterandoStatusId(null)
    }
  }

  const validarForm = () => {
    const quantidadeCreditos = Number(form.quantidadeCreditos)
    const valor = parseValor(form.valor)
    const ordemExibicao = Number(form.ordemExibicao)
    if (!editandoId && !/^[A-Z0-9_]{3,50}$/.test(form.codigo.trim().toUpperCase())) {
      toast.warning('Informe um codigo estavel com letras, numeros ou sublinhado.')
      return null
    }
    if (form.nome.trim().length < 2 || form.nome.trim().length > 120) {
      toast.warning('O nome deve ter entre 2 e 120 caracteres.')
      return null
    }
    if (form.descricao.trim().length > 500) {
      toast.warning('A descricao deve ter no maximo 500 caracteres.')
      return null
    }
    if (!Number.isInteger(quantidadeCreditos) || quantidadeCreditos <= 0) {
      toast.warning('Informe uma quantidade positiva de creditos.')
      return null
    }
    if (!Number.isFinite(valor) || valor <= 0 || !/^\d+([,.]\d{1,2})?$/.test(form.valor.trim())) {
      toast.warning('Informe um preco positivo com no maximo duas casas decimais.')
      return null
    }
    if (!Number.isInteger(ordemExibicao) || ordemExibicao < 0) {
      toast.warning('Informe uma ordem valida.')
      return null
    }
    return { quantidadeCreditos, valor, ordemExibicao }
  }

  const salvar = async () => {
    if (salvando) return
    const validado = validarForm()
    if (!validado) return
    setSalvando(true)
    try {
      if (editandoId) {
        const body: AdminPlanoCreditoAtualizar = {
          nome: form.nome.trim(),
          descricao: form.descricao.trim(),
          quantidadeCreditos: validado.quantidadeCreditos,
          valor: validado.valor,
          ordemExibicao: validado.ordemExibicao,
          atualizadoEm: form.atualizadoEm,
        }
        await AdminCreditosApi.atualizarPacote(editandoId, body)
        toast.success('Plano atualizado sem alterar pagamentos anteriores.')
      } else {
        const body: AdminPlanoCreditoCriar = {
          codigo: form.codigo.trim().toUpperCase(),
          nome: form.nome.trim(),
          descricao: form.descricao.trim(),
          quantidadeCreditos: validado.quantidadeCreditos,
          valor: validado.valor,
          ativo: form.ativo,
          ordemExibicao: validado.ordemExibicao,
        }
        await AdminCreditosApi.criarPacote(body)
        toast.success('Plano criado.')
      }
      setDialogOpen(false)
      await carregar()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Nao foi possivel salvar o plano.')
    } finally {
      setSalvando(false)
    }
  }

  const alterarStatus = async (plano: AdminPlanoCredito) => {
    if (alterandoStatusId) return
    if (plano.ativo && !window.confirm(
      'Desativar este plano? Ele deixara de aparecer para novas compras, mas o historico sera preservado.',
    )) return
    setAlterandoStatusId(plano.id)
    try {
      const atualizado = plano.ativo
        ? await AdminCreditosApi.desativarPacote(plano.id, plano.atualizadoEm)
        : await AdminCreditosApi.ativarPacote(plano.id, plano.atualizadoEm)
      setPlanos((atuais) => atuais.map((item) => item.id === plano.id ? atualizado : item))
      toast.success(plano.ativo ? 'Plano desativado.' : 'Plano ativado no catalogo.')
      await carregar()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Nao foi possivel alterar o status.')
      await carregar()
    } finally {
      setAlterandoStatusId(null)
    }
  }

  return (
    <section className="rounded-lg border border-gray-200 bg-white p-4 sm:p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-base font-semibold text-gray-900">Planos e creditos</h2>
          <p className="mt-1 text-sm text-gray-500">
            Catalogo para novas compras. Pagamentos anteriores mantem preco e creditos originais.
          </p>
        </div>
        <Button onClick={abrirCriacao}>
          <Plus className="mr-2 size-4" aria-hidden="true" />
          Novo plano
        </Button>
      </div>

      <div className="mt-5 grid grid-cols-2 gap-3 sm:max-w-md">
        <div className="border-l-2 border-emerald-500 pl-3">
          <p className="text-xs text-gray-500">Ativos no resultado</p>
          <p className="text-xl font-semibold text-gray-900">{indicadores.ativos}</p>
        </div>
        <div className="border-l-2 border-gray-300 pl-3">
          <p className="text-xs text-gray-500">Inativos no resultado</p>
          <p className="text-xl font-semibold text-gray-900">{indicadores.inativos}</p>
        </div>
      </div>

      <div className="mt-5 grid gap-3 md:grid-cols-[minmax(0,1fr)_180px_auto]">
        <Input
          value={busca}
          onChange={(event) => setBusca(event.target.value)}
          placeholder="Buscar por nome ou codigo"
          aria-label="Buscar planos"
        />
        <select
          value={status}
          onChange={(event) => setStatus(event.target.value as StatusFiltro)}
          className="h-10 rounded-md border border-gray-200 bg-white px-3 text-sm"
          aria-label="Filtrar planos por status"
        >
          <option value="TODOS">Todos os status</option>
          <option value="ATIVOS">Ativos</option>
          <option value="INATIVOS">Inativos</option>
        </select>
        <Button
          type="button"
          variant="outline"
          size="icon"
          title="Atualizar planos"
          aria-label="Atualizar planos"
          disabled={loading}
          onClick={() => void carregar()}
        >
          <RefreshCw className={`size-4 ${loading ? 'animate-spin' : ''}`} aria-hidden="true" />
        </Button>
      </div>

      {erro ? (
        <div className="mt-5 border-l-2 border-red-500 bg-red-50 p-4 text-sm text-red-800">
          <p>{erro}</p>
          <Button className="mt-3" size="sm" variant="outline" onClick={() => void carregar()}>
            Tentar novamente
          </Button>
        </div>
      ) : null}

      {!erro && loading ? (
        <p className="py-10 text-center text-sm text-gray-500">Carregando planos...</p>
      ) : null}

      {!erro && !loading && planos.length === 0 ? (
        <p className="py-10 text-center text-sm text-gray-500">Nenhum plano encontrado.</p>
      ) : null}

      {!erro && !loading && planos.length > 0 ? (
        <>
          <div className="mt-5 hidden overflow-x-auto md:block">
            <table className="min-w-full table-fixed text-left text-sm">
              <thead className="border-b border-gray-200 text-xs uppercase text-gray-500">
                <tr>
                  <th className="w-[34%] px-3 py-2">Plano</th>
                  <th className="w-[13%] px-3 py-2">Creditos</th>
                  <th className="w-[15%] px-3 py-2">Preco</th>
                  <th className="w-[12%] px-3 py-2">Compras</th>
                  <th className="w-[12%] px-3 py-2">Status</th>
                  <th className="w-[14%] px-3 py-2 text-right">Acoes</th>
                </tr>
              </thead>
              <tbody>
                {planos.map((plano) => (
                  <tr key={plano.id} className="border-b border-gray-100 align-middle">
                    <td className="px-3 py-3">
                      <p className="font-medium text-gray-900">{plano.nome}</p>
                      <p className="truncate text-xs text-gray-500">{plano.codigo} · ordem {plano.ordemExibicao}</p>
                    </td>
                    <td className="px-3 py-3">{plano.quantidadeCreditos}</td>
                    <td className="px-3 py-3">{reais.format(plano.valor)}</td>
                    <td className="px-3 py-3">{plano.comprasConfirmadas}</td>
                    <td className="px-3 py-3">
                      <Badge variant={plano.ativo ? 'default' : 'secondary'}>
                        {plano.ativo ? 'Ativo' : 'Inativo'}
                      </Badge>
                    </td>
                    <td className="px-3 py-3">
                      <div className="flex justify-end gap-1">
                        <Button
                          variant="ghost"
                          size="icon"
                          title="Editar plano"
                          aria-label={`Editar ${plano.nome}`}
                          disabled={alterandoStatusId === plano.id}
                          onClick={() => void abrirEdicao(plano.id)}
                        >
                          <Pencil className="size-4" aria-hidden="true" />
                        </Button>
                        <Button
                          variant={plano.ativo ? 'destructive' : 'outline'}
                          size="icon"
                          title={plano.ativo ? 'Desativar plano' : 'Ativar plano'}
                          aria-label={`${plano.ativo ? 'Desativar' : 'Ativar'} ${plano.nome}`}
                          disabled={alterandoStatusId !== null}
                          onClick={() => void alterarStatus(plano)}
                        >
                          {plano.ativo
                            ? <PowerOff className="size-4" aria-hidden="true" />
                            : <Power className="size-4" aria-hidden="true" />}
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="mt-5 divide-y divide-gray-100 md:hidden">
            {planos.map((plano) => (
              <div key={plano.id} className="py-4">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="truncate font-medium text-gray-900">{plano.nome}</p>
                    <p className="truncate text-xs text-gray-500">{plano.codigo}</p>
                  </div>
                  <Badge variant={plano.ativo ? 'default' : 'secondary'}>
                    {plano.ativo ? 'Ativo' : 'Inativo'}
                  </Badge>
                </div>
                <dl className="mt-3 grid grid-cols-3 gap-2 text-sm">
                  <div><dt className="text-xs text-gray-500">Creditos</dt><dd>{plano.quantidadeCreditos}</dd></div>
                  <div><dt className="text-xs text-gray-500">Preco</dt><dd>{reais.format(plano.valor)}</dd></div>
                  <div><dt className="text-xs text-gray-500">Compras</dt><dd>{plano.comprasConfirmadas}</dd></div>
                </dl>
                <div className="mt-3 flex gap-2">
                  <Button
                    className="flex-1"
                    variant="outline"
                    disabled={alterandoStatusId === plano.id}
                    onClick={() => void abrirEdicao(plano.id)}
                  >
                    <Pencil className="mr-2 size-4" aria-hidden="true" />
                    Editar
                  </Button>
                  <Button
                    className="flex-1"
                    variant={plano.ativo ? 'destructive' : 'outline'}
                    disabled={alterandoStatusId !== null}
                    onClick={() => void alterarStatus(plano)}
                  >
                    {plano.ativo
                      ? <PowerOff className="mr-2 size-4" aria-hidden="true" />
                      : <Power className="mr-2 size-4" aria-hidden="true" />}
                    {plano.ativo ? 'Desativar' : 'Ativar'}
                  </Button>
                </div>
              </div>
            ))}
          </div>
        </>
      ) : null}

      <Dialog open={dialogOpen} onOpenChange={(open) => !salvando && setDialogOpen(open)}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>{editandoId ? 'Editar plano' : 'Novo plano'}</DialogTitle>
            <DialogDescription>
              O catalogo e atualizado imediatamente, sem recalcular pagamentos existentes.
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-2">
            <div className="grid gap-2">
              <Label htmlFor="plano-codigo">Codigo estavel</Label>
              <Input
                id="plano-codigo"
                value={form.codigo}
                disabled={Boolean(editandoId)}
                maxLength={50}
                onChange={(event) => setForm((atual) => ({
                  ...atual,
                  codigo: event.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, ''),
                }))}
                placeholder="PACOTE_QA_100"
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="plano-nome">Nome</Label>
              <Input
                id="plano-nome"
                value={form.nome}
                maxLength={120}
                onChange={(event) => setForm((atual) => ({ ...atual, nome: event.target.value }))}
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="plano-descricao">Descricao</Label>
              <Textarea
                id="plano-descricao"
                value={form.descricao}
                maxLength={500}
                onChange={(event) => setForm((atual) => ({ ...atual, descricao: event.target.value }))}
              />
            </div>
            <div className="grid gap-4 sm:grid-cols-3">
              <div className="grid gap-2">
                <Label htmlFor="plano-creditos">Creditos</Label>
                <Input
                  id="plano-creditos"
                  type="number"
                  min={1}
                  value={form.quantidadeCreditos}
                  onChange={(event) => setForm((atual) => ({ ...atual, quantidadeCreditos: event.target.value }))}
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="plano-valor">Preco (R$)</Label>
                <Input
                  id="plano-valor"
                  inputMode="decimal"
                  value={form.valor}
                  onChange={(event) => setForm((atual) => ({ ...atual, valor: event.target.value }))}
                  placeholder="49,90"
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="plano-ordem">Ordem</Label>
                <Input
                  id="plano-ordem"
                  type="number"
                  min={0}
                  value={form.ordemExibicao}
                  onChange={(event) => setForm((atual) => ({ ...atual, ordemExibicao: event.target.value }))}
                />
              </div>
            </div>
            {!editandoId ? (
              <label className="flex items-center gap-3 text-sm text-gray-700">
                <input
                  type="checkbox"
                  checked={form.ativo}
                  onChange={(event) => setForm((atual) => ({ ...atual, ativo: event.target.checked }))}
                />
                Disponibilizar imediatamente para novas compras
              </label>
            ) : (
              <p className="text-xs text-gray-500">
                Criado em {new Date(form.criadoEm).toLocaleString('pt-BR')} · ultima alteracao{' '}
                {new Date(form.atualizadoEm).toLocaleString('pt-BR')}
              </p>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" disabled={salvando} onClick={() => setDialogOpen(false)}>
              Cancelar
            </Button>
            <Button disabled={salvando} onClick={() => void salvar()}>
              {salvando ? 'Salvando...' : 'Salvar plano'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </section>
  )
}
