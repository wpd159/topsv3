'use client'

import { useEffect, useMemo, useState } from 'react'
import { MagnifyingGlassIcon, UsersIcon, UserPlusIcon, FolderOpenIcon, UserMinusIcon } from '@heroicons/react/24/solid'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { toast } from 'sonner'
import GerenciarUsuariosTable from '../components/usuarios-table'
import AdicionarCreditosDialog from '../components/adicionar-creditos-dialog'
import type { AdminUsuario } from '../components/admin-usuarios-utils'
import {
  formatarDataHoraBR,
  formatarLocalUsuario,
  getUsuarioNomePrincipal,
} from '../components/admin-usuarios-utils'

type Ordenacao = 'CADASTRO_DESC' | 'CADASTRO_ASC' | 'NOME_ASC'
type TabFiltro = 'TODOS' | 'ATIVO' | 'INATIVO' | 'COM_ANUNCIOS' | 'SEM_ANUNCIOS'
type TamanhoPagina = '30' | '50' | '100'

type UsuarioAdminPageResponse = {
  items: AdminUsuario[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  totalUsuarios: number
  novosHoje: number
  comAnuncios: number
  semAnuncios: number
}

type UsuariosLegacyResponse = AdminUsuario[]

const TABS: Array<{ label: string; value: TabFiltro }> = [
  { label: 'Todos', value: 'TODOS' },
  { label: 'Ativos', value: 'ATIVO' },
  { label: 'Inativos', value: 'INATIVO' },
  { label: 'Com anúncios', value: 'COM_ANUNCIOS' },
  { label: 'Sem anúncios', value: 'SEM_ANUNCIOS' },
]

const ORDENACOES: Array<{ label: string; value: Ordenacao }> = [
  { label: 'Cadastro mais recente', value: 'CADASTRO_DESC' },
  { label: 'Cadastro mais antigo', value: 'CADASTRO_ASC' },
  { label: 'Nome A-Z', value: 'NOME_ASC' },
]

const TAMANHOS_PAGINA: Array<{ label: string; value: TamanhoPagina }> = [
  { label: '30 por página', value: '30' },
  { label: '50 por página', value: '50' },
  { label: '100 por página', value: '100' },
]

const UFS_BR = [
  'AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS',
  'MG', 'PA', 'PB', 'PR', 'PE', 'PI', 'RJ', 'RN', 'RS', 'RO', 'RR', 'SC',
  'SP', 'SE', 'TO',
]

async function readApiError(response: Response, fallback: string) {
  const raw = await response.text().catch(() => '')
  if (!raw.trim()) return fallback
  try {
    const parsed = JSON.parse(raw) as { error?: string; message?: string }
    return String(parsed.error || parsed.message || '').trim() || fallback
  } catch {
    return raw.trim() || fallback
  }
}

function buildUsuariosQuery(params: {
  busca: string
  tab: TabFiltro
  uf: string
  cidade: string
  ordenacao: Ordenacao
  pagina: number
  tamanhoPagina: number
}) {
  const query = new URLSearchParams()
  query.set('page', String(params.pagina))
  query.set('size', String(params.tamanhoPagina))
  query.set('ordenacao', params.ordenacao)

  const busca = params.busca.trim()
  if (busca) query.set('busca', busca)
  if (params.uf !== 'TODAS') query.set('estadoUf', params.uf)
  if (params.cidade.trim()) query.set('cidade', params.cidade.trim())

  if (params.tab === 'ATIVO') query.set('status', 'ATIVO')
  if (params.tab === 'INATIVO') query.set('status', 'INATIVO')
  if (params.tab === 'COM_ANUNCIOS') query.set('comAnuncios', 'true')
  if (params.tab === 'SEM_ANUNCIOS') query.set('comAnuncios', 'false')

  return query.toString()
}

function filtrarUsuariosLocais(
  usuarios: AdminUsuario[],
  params: {
    busca: string
    tab: TabFiltro
    uf: string
    cidade: string
  }
) {
  const buscaTexto = params.busca.trim().toLowerCase()
  const buscaDigitos = params.busca.replace(/\D/g, '')
  const cidadeFiltro = params.cidade.trim().toLowerCase()

  return usuarios.filter((usuario) => {
    if (params.tab === 'ATIVO' && usuario.status !== 'ATIVO') return false
    if (params.tab === 'INATIVO' && usuario.status !== 'INATIVO') return false
    if (params.tab === 'COM_ANUNCIOS' && Number(usuario.totalAnuncios || 0) <= 0) return false
    if (params.tab === 'SEM_ANUNCIOS' && Number(usuario.totalAnuncios || 0) > 0) return false

    if (params.uf !== 'TODAS' && (usuario.estadoUf || '').toUpperCase() !== params.uf) {
      return false
    }

    if (cidadeFiltro && !(usuario.cidadeNome || '').toLowerCase().includes(cidadeFiltro)) {
      return false
    }

    if (!buscaTexto) return true

    const camposTexto = [
      usuario.nomeCompleto,
      usuario.username,
      usuario.email,
    ]
      .filter(Boolean)
      .map((value) => String(value).toLowerCase())

    if (camposTexto.some((value) => value.includes(buscaTexto))) {
      return true
    }

    if (!buscaDigitos) return false

    const cpf = String(usuario.cpf || '').replace(/\D/g, '')
    const telefone = String(usuario.telefone || '').replace(/\D/g, '')
    return cpf.includes(buscaDigitos) || telefone.includes(buscaDigitos)
  })
}

function ordenarUsuariosLocais(usuarios: AdminUsuario[], ordenacao: Ordenacao) {
  const collator = new Intl.Collator('pt-BR', { sensitivity: 'base' })
  const lista = [...usuarios]

  lista.sort((a, b) => {
    if (ordenacao === 'NOME_ASC') {
      const nomeA = getUsuarioNomePrincipal(a)
      const nomeB = getUsuarioNomePrincipal(b)
      return collator.compare(nomeA, nomeB)
    }

    const dataA = a.criadoEm ?? a.dataCadastro ?? ''
    const dataB = b.criadoEm ?? b.dataCadastro ?? ''
    const tsA = dataA ? new Date(dataA).getTime() : 0
    const tsB = dataB ? new Date(dataB).getTime() : 0

    if (ordenacao === 'CADASTRO_ASC') {
      return tsA - tsB || Number(a.id || 0) - Number(b.id || 0)
    }

    return tsB - tsA || Number(b.id || 0) - Number(a.id || 0)
  })

  return lista
}

function calcularResumoLegacy(usuarios: AdminUsuario[]) {
  const hoje = new Date()
  const ano = hoje.getFullYear()
  const mes = hoje.getMonth()
  const dia = hoje.getDate()

  const novosHoje = usuarios.filter((usuario) => {
    const raw = usuario.criadoEm ?? usuario.dataCadastro
    if (!raw) return false
    const data = new Date(raw)
    return (
      data.getFullYear() === ano &&
      data.getMonth() === mes &&
      data.getDate() === dia
    )
  }).length

  const comAnuncios = usuarios.filter((usuario) => Number(usuario.totalAnuncios || 0) > 0).length

  return {
    totalUsuarios: usuarios.length,
    novosHoje,
    comAnuncios,
    semAnuncios: Math.max(0, usuarios.length - comAnuncios),
  }
}

function normalizarRespostaUsuarios(
  data: UsuarioAdminPageResponse | UsuariosLegacyResponse,
  params: {
    busca: string
    tab: TabFiltro
    uf: string
    cidade: string
    ordenacao: Ordenacao
    pagina: number
    tamanhoPagina: number
  }
) {
  if (Array.isArray(data)) {
    const resumo = calcularResumoLegacy(data)
    const filtrados = filtrarUsuariosLocais(data, params)
    const ordenados = ordenarUsuariosLocais(filtrados, params.ordenacao)
    const inicio = params.pagina * params.tamanhoPagina
    const fim = inicio + params.tamanhoPagina
    const items = ordenados.slice(inicio, fim)
    const totalPages = ordenados.length === 0 ? 0 : Math.ceil(ordenados.length / params.tamanhoPagina)

    return {
      items,
      page: params.pagina,
      size: params.tamanhoPagina,
      totalElements: ordenados.length,
      totalPages,
      totalUsuarios: resumo.totalUsuarios,
      novosHoje: resumo.novosHoje,
      comAnuncios: resumo.comAnuncios,
      semAnuncios: resumo.semAnuncios,
    }
  }

  return {
    items: Array.isArray(data.items) ? data.items : [],
    page: Number(data.page || 0),
    size: Number(data.size || params.tamanhoPagina),
    totalElements: Number(data.totalElements || 0),
    totalPages: Number(data.totalPages || 0),
    totalUsuarios: Number(data.totalUsuarios || 0),
    novosHoje: Number(data.novosHoje || 0),
    comAnuncios: Number(data.comAnuncios || 0),
    semAnuncios: Number(data.semAnuncios || 0),
  }
}

export default function AdminUsuariosPage() {
  const [usuarios, setUsuarios] = useState<AdminUsuario[]>([])
  const [loading, setLoading] = useState(true)
  const [busca, setBusca] = useState('')
  const [tab, setTab] = useState<TabFiltro>('TODOS')
  const [uf, setUf] = useState('TODAS')
  const [cidade, setCidade] = useState('')
  const [ordenacao, setOrdenacao] = useState<Ordenacao>('CADASTRO_DESC')
  const [pagina, setPagina] = useState(0)
  const [tamanhoPagina, setTamanhoPagina] = useState<TamanhoPagina>('30')
  const [creditoOpen, setCreditoOpen] = useState(false)
  const [usuarioCredito, setUsuarioCredito] = useState<AdminUsuario | null>(null)
  const [resumo, setResumo] = useState({
    totalUsuarios: 0,
    novosHoje: 0,
    comAnuncios: 0,
    semAnuncios: 0,
    totalElements: 0,
    totalPages: 0,
  })

  const carregarUsuarios = async () => {
    try {
      setLoading(true)

      const query = buildUsuariosQuery({
        busca,
        tab,
        uf,
        cidade,
        ordenacao,
        pagina,
        tamanhoPagina: Number(tamanhoPagina),
      })

      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/usuarios?${query}`, {
        credentials: 'include',
      })

      if (!res.ok) {
        throw new Error(await readApiError(res, 'Erro ao carregar usuários.'))
      }

      const payload = (await res.json()) as UsuarioAdminPageResponse | UsuariosLegacyResponse
      const data = normalizarRespostaUsuarios(payload, {
        busca,
        tab,
        uf,
        cidade,
        ordenacao,
        pagina,
        tamanhoPagina: Number(tamanhoPagina),
      })

      setUsuarios(data.items)
      setResumo({
        totalUsuarios: data.totalUsuarios,
        novosHoje: data.novosHoje,
        comAnuncios: data.comAnuncios,
        semAnuncios: data.semAnuncios,
        totalElements: data.totalElements,
        totalPages: data.totalPages,
      })

      if (data.totalPages > 0 && pagina > data.totalPages - 1) {
        setPagina(Math.max(0, data.totalPages - 1))
      }
    } catch (error) {
      const message = error instanceof Error && error.message.trim()
        ? error.message
        : 'Falha ao carregar usuários.'
      toast.error(message)
      setUsuarios([])
      setResumo({
        totalUsuarios: 0,
        novosHoje: 0,
        comAnuncios: 0,
        semAnuncios: 0,
        totalElements: 0,
        totalPages: 0,
      })
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void carregarUsuarios()
  }, [busca, tab, uf, cidade, ordenacao, pagina, tamanhoPagina])

  const cardsResumo = useMemo(() => {
    return [
      { label: 'Total de usuários', value: resumo.totalUsuarios, icon: UsersIcon },
      { label: 'Novos hoje', value: resumo.novosHoje, icon: UserPlusIcon },
      { label: 'Com anúncios', value: resumo.comAnuncios, icon: FolderOpenIcon },
      { label: 'Sem anúncios', value: resumo.semAnuncios, icon: UserMinusIcon },
    ]
  }, [resumo])

  const usuarioReferencia = usuarios[0] || null

  const abrirCredito = (usuario: AdminUsuario) => {
    setUsuarioCredito(usuario)
    setCreditoOpen(true)
  }

  const atualizarSaldoUsuario = (novoSaldo: number) => {
    if (!usuarioCredito) return

    setUsuarios((prev) =>
      prev.map((item) =>
        item.id === usuarioCredito.id
          ? { ...item, totalCreditos: novoSaldo }
          : item
      )
    )

    setUsuarioCredito((prev) => (prev ? { ...prev, totalCreditos: novoSaldo } : prev))
  }

  const alternarStatus = async (usuario: AdminUsuario) => {
    try {
      const endpoint = usuario.status === 'ATIVO' ? 'inativar' : 'ativar'
      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/usuarios/${usuario.id}/${endpoint}`, {
        method: 'PUT',
        credentials: 'include',
      })

      if (!res.ok) {
        throw new Error(await readApiError(res, 'Erro ao alterar status do usuário.'))
      }

      setUsuarios((prev) =>
        prev.map((item) =>
          item.id === usuario.id
            ? { ...item, status: usuario.status === 'ATIVO' ? 'INATIVO' : 'ATIVO' }
            : item
        )
      )

      toast.success(`Usuário ${usuario.status === 'ATIVO' ? 'inativado' : 'ativado'} com sucesso.`)
    } catch (error) {
      const message = error instanceof Error && error.message.trim()
        ? error.message
        : 'Erro ao alterar status do usuário.'
      toast.error(message)
    }
  }

  const paginaAtual = resumo.totalPages > 0 ? pagina + 1 : 0

  return (
    <section className="space-y-6 pb-8">
      <div className="space-y-1">
        <h1 className="text-2xl font-bold text-gray-800">Gerenciar Usuários</h1>
        <p className="text-sm text-gray-500">
          Gestão cadastral, comercial e operacional dos usuários da plataforma.
        </p>
      </div>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {cardsResumo.map((card) => (
          <div key={card.label} className="rounded-2xl border border-gray-100 bg-white p-5 shadow-sm">
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="text-sm text-gray-500">{card.label}</p>
                <p className="mt-2 text-2xl font-bold text-gray-900">{card.value.toLocaleString('pt-BR')}</p>
              </div>
              <div className="rounded-full bg-[#FC1EAD]/10 p-3 text-[#C41E73]">
                <card.icon className="h-5 w-5" />
              </div>
            </div>
          </div>
        ))}
      </div>

      <div className="rounded-2xl border border-gray-100 bg-white p-5 shadow-sm">
        <div className="flex flex-col gap-4">
          <div className="flex flex-wrap gap-2">
            {TABS.map((item) => (
              <Button
                key={item.value}
                variant={tab === item.value ? 'default' : 'outline'}
                onClick={() => {
                  setTab(item.value)
                  setPagina(0)
                }}
                className={
                  tab === item.value
                    ? 'bg-[#f0198f] text-white hover:bg-[#db1984]'
                    : 'border-gray-300 text-gray-700 hover:bg-gray-100'
                }
              >
                {item.label}
              </Button>
            ))}
          </div>

          <div className="grid gap-3 xl:grid-cols-[minmax(0,1.6fr)_180px_220px_220px_180px]">
            <div className="relative">
              <MagnifyingGlassIcon className="absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-gray-400" />
              <Input
                type="text"
                placeholder="Buscar por nome, email, CPF ou telefone..."
                value={busca}
                onChange={(event) => {
                  setBusca(event.target.value)
                  setPagina(0)
                }}
                className="pl-10"
              />
            </div>

            <Select
              value={uf}
              onValueChange={(value) => {
                setUf(value)
                setPagina(0)
              }}
            >
              <SelectTrigger>
                <SelectValue placeholder="UF" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="TODAS">Todas as UFs</SelectItem>
                {UFS_BR.map((value) => (
                  <SelectItem key={value} value={value}>
                    {value}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>

            <Input
              type="text"
              placeholder="Filtrar por cidade"
              value={cidade}
              onChange={(event) => {
                setCidade(event.target.value)
                setPagina(0)
              }}
            />

            <Select
              value={ordenacao}
              onValueChange={(value) => {
                setOrdenacao(value as Ordenacao)
                setPagina(0)
              }}
            >
              <SelectTrigger>
                <SelectValue placeholder="Ordenação" />
              </SelectTrigger>
              <SelectContent>
                {ORDENACOES.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>

            <Select
              value={tamanhoPagina}
              onValueChange={(value) => {
                setTamanhoPagina(value as TamanhoPagina)
                setPagina(0)
              }}
            >
              <SelectTrigger>
                <SelectValue placeholder="Quantidade" />
              </SelectTrigger>
              <SelectContent>
                {TAMANHOS_PAGINA.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-gray-50 px-4 py-3 text-sm text-gray-600">
            <p>
              Exibindo <span className="font-semibold text-gray-900">{usuarios.length}</span> usuário(s) nesta página de um total de{' '}
              <span className="font-semibold text-gray-900">{resumo.totalElements.toLocaleString('pt-BR')}</span>.
            </p>
            <p>
              {usuarioReferencia ? (
                <>
                  Local principal: <span className="font-medium text-gray-800">{formatarLocalUsuario(usuarioReferencia)}</span>
                  <span className="ml-2 text-gray-500">
                    · cadastro {formatarDataHoraBR(usuarioReferencia.criadoEm ?? usuarioReferencia.dataCadastro)}
                  </span>
                </>
              ) : (
                'Nenhum usuário carregado para o filtro atual.'
              )}
            </p>
          </div>
        </div>
      </div>

      <GerenciarUsuariosTable
        usuarios={usuarios}
        loading={loading}
        onToggleStatus={alternarStatus}
        onAddCredit={abrirCredito}
      />

      <div className="flex flex-col gap-3 rounded-2xl border border-gray-100 bg-white px-5 py-4 shadow-sm sm:flex-row sm:items-center sm:justify-between">
        <div className="text-sm text-gray-600">
          Página <span className="font-semibold text-gray-900">{paginaAtual}</span>
          {' '}de <span className="font-semibold text-gray-900">{resumo.totalPages}</span>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            onClick={() => setPagina((prev) => Math.max(0, prev - 1))}
            disabled={loading || pagina <= 0}
          >
            Anterior
          </Button>
          <Button
            variant="outline"
            onClick={() => setPagina((prev) => prev + 1)}
            disabled={loading || resumo.totalPages === 0 || pagina >= resumo.totalPages - 1}
          >
            Próxima
          </Button>
        </div>
      </div>

      <AdicionarCreditosDialog
        open={creditoOpen}
        onOpenChange={setCreditoOpen}
        usuarioId={usuarioCredito?.id ?? null}
        nomeUsuario={getUsuarioNomePrincipal(usuarioCredito || {})}
        saldoAtual={usuarioCredito?.totalCreditos ?? 0}
        onSuccess={atualizarSaldoUsuario}
      />
    </section>
  )
}
