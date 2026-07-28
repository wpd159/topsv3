'use client'

import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { useCallback, useEffect, useMemo, useState } from 'react'

import FinanceiroCards from '../components/financeiro/financeiro-cards'
import FinanceiroFiltro from '../components/financeiro/financeiro-filtro'
import FinanceiroTabela from '../components/financeiro/financeiro-table'
import FinanceiroGrafico from '../components/financeiro/financeiro-charts'
import {
  listarRelatorioReceitaTransacoes,
  obterRelatorioReceitaResumo,
  type AdminRelatorioReceitaFiltros,
  type AdminRelatorioReceitaPagina,
  type AdminRelatorioReceitaResumo,
} from '@/lib/admin-pagamentos-api'

export default function AdminFinanceiroPage() {
  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const [resumo, setResumo] = useState<AdminRelatorioReceitaResumo | null>(null)
  const [pagina, setPagina] = useState<AdminRelatorioReceitaPagina | null>(null)
  const [resumoLoading, setResumoLoading] = useState(true)
  const [tabelaLoading, setTabelaLoading] = useState(true)
  const [resumoErro, setResumoErro] = useState<unknown>(null)
  const [tabelaErro, setTabelaErro] = useState<unknown>(null)
  const [resumoReload, setResumoReload] = useState(0)
  const [tabelaReload, setTabelaReload] = useState(0)

  const filtros = useMemo<AdminRelatorioReceitaFiltros>(() => {
    const periodo = searchParams.get('periodo')
    const status = searchParams.get('status')
    const metodo = searchParams.get('metodo')
    const ordenacao = searchParams.get('ordenacao')
    const tamanho = Number(searchParams.get('size') ?? '20')
    return {
      periodo: ['HOJE', '7_DIAS', '30_DIAS', 'PERSONALIZADO'].includes(periodo ?? '')
        ? periodo as AdminRelatorioReceitaFiltros['periodo']
        : '30_DIAS',
      inicio: searchParams.get('inicio') || undefined,
      fim: searchParams.get('fim') || undefined,
      status: [
        'TODOS',
        'CONFIRMADO',
        'PENDENTE',
        'FALHO',
        'CANCELADO',
        'EXPIRADO',
        'ESTORNADO',
        'LEGADO',
      ].includes(status ?? '')
        ? status as AdminRelatorioReceitaFiltros['status']
        : 'TODOS',
      metodo: ['TODOS', 'PIX', 'LEGADO', 'DESCONHECIDO'].includes(metodo ?? '')
        ? metodo as AdminRelatorioReceitaFiltros['metodo']
        : 'TODOS',
      usuario: searchParams.get('usuario') || undefined,
      produto: searchParams.get('produto') || undefined,
      ordenacao: ['MAIS_RECENTES', 'MAIS_ANTIGOS', 'MAIOR_VALOR', 'MENOR_VALOR'].includes(
        ordenacao ?? '',
      )
        ? ordenacao as AdminRelatorioReceitaFiltros['ordenacao']
        : 'MAIS_RECENTES',
      page: Math.max(Number(searchParams.get('page') ?? '0') || 0, 0),
      size: [20, 30, 50, 100].includes(tamanho) ? tamanho : 20,
    }
  }, [searchParams])

  const atualizarFiltros = useCallback((updates: Record<string, string | number | null>) => {
    const next = new URLSearchParams(searchParams.toString())
    Object.entries(updates).forEach(([key, value]) => {
      if (value === null || value === '' || value === 'TODOS' || value === '30_DIAS') {
        next.delete(key)
      } else {
        next.set(key, String(value))
      }
    })
    router.push(next.size ? `${pathname}?${next.toString()}` : pathname)
  }, [pathname, router, searchParams])

  useEffect(() => {
    let active = true
    setResumoLoading(true)
    setResumoErro(null)
    setResumo(null)
    obterRelatorioReceitaResumo(filtros)
      .then((response) => {
        if (active) setResumo(response)
      })
      .catch((error) => {
        if (active) setResumoErro(error)
      })
      .finally(() => {
        if (active) setResumoLoading(false)
      })
    return () => { active = false }
  }, [filtros, resumoReload])

  useEffect(() => {
    let active = true
    setTabelaLoading(true)
    setTabelaErro(null)
    setPagina(null)
    listarRelatorioReceitaTransacoes(filtros)
      .then((response) => {
        if (active) setPagina(response)
      })
      .catch((error) => {
        if (active) setTabelaErro(error)
      })
      .finally(() => {
        if (active) setTabelaLoading(false)
      })
    return () => { active = false }
  }, [filtros, tabelaReload])

  return (
    <section className="space-y-6 pb-10">
      <header>
        <h1 className="text-2xl font-bold text-zinc-950">Relatórios de receita</h1>
        <p className="mt-1 text-sm text-zinc-600">
          Pagamentos confirmados e vendas de créditos, sem misturar saldo ou ajustes administrativos.
        </p>
      </header>

      <FinanceiroFiltro filtros={filtros} onChange={atualizarFiltros} />
      <FinanceiroCards
        resumo={resumo}
        loading={resumoLoading}
        error={resumoErro}
        onRetry={() => setResumoReload((value) => value + 1)}
      />
      <FinanceiroGrafico
        resumo={resumo}
        loading={resumoLoading}
        error={resumoErro}
        onRetry={() => setResumoReload((value) => value + 1)}
      />
      <FinanceiroTabela
        pagina={pagina}
        loading={tabelaLoading}
        error={tabelaErro}
        onRetry={() => setTabelaReload((value) => value + 1)}
        onPageChange={(page) => atualizarFiltros({ page: page || null })}
      />
    </section>
  )
}
