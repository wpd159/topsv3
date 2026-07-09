'use client'

import FinanceiroCards from '../components/financeiro/financeiro-cards'
import FinanceiroFiltro from '../components/financeiro/financeiro-filtro'
import FinanceiroTabela from '../components/financeiro/financeiro-table'
import FinanceiroGrafico from '../components/financeiro/financeiro-charts'
import RankingUsuarios from '../components/creditos/ranking-usuarios'

export default function AdminFinanceiroPage() {
  return (
    <section className="pb-10">
      <div className="mb-8 flex flex-col">
        <h1 className="text-2xl font-bold text-gray-800">Financeiro</h1>
        <p className="text-sm text-gray-500">
          Acompanhe receitas, despesas e fluxo de caixa da plataforma.
        </p>
      </div>

      <FinanceiroCards />

      <div className="mt-8">
        <FinanceiroGrafico />
      </div>

      <div className="mt-8">
        <FinanceiroFiltro />
      </div>

      <div className="mt-8">
        <FinanceiroTabela />
      </div>

      <div className="mt-8">
        <RankingUsuarios />
      </div>
    </section>
  )
}
