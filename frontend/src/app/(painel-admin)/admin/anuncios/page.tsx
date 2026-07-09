'use client'

import { useState } from "react"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"
import { MagnifyingGlassIcon } from "@heroicons/react/24/solid"
import GerenciarAnunciosTable from "../components/anuncios/anuncios-table"

const STATUS_OPCOES = [
  { label: "Todos", value: "TODOS" },
  { label: "Pendentes", value: "PENDENTE" },
  { label: "Ativos", value: "ATIVO" },
  { label: "Rejeitados", value: "REJEITADO" },
  { label: "Revisões pendentes", value: "REVISAO_PENDENTE" },
]

export default function AdminAnunciosPage() {
  const [busca, setBusca] = useState("")
  const [status, setStatus] = useState("TODOS")

  return (
    <section>
      {/* Cabeçalho */}
      <div className="flex flex-col gap-1 mb-6">
        <h1 className="text-2xl font-bold text-gray-800">Gerenciar Anúncios</h1>
        <p className="text-sm text-gray-500">
          Veja todos os anúncios cadastrados, aprove ou reprove conforme necessário.
        </p>
      </div>

      {/* 🔍 Filtros + busca */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-6">
        {/* Filtros de status */}
        <div className="flex flex-wrap gap-2">
          {STATUS_OPCOES.map((opt) => (
            <Button
              key={opt.value}
              variant={status === opt.value ? "default" : "outline"}
              onClick={() => setStatus(opt.value)}
              className={
                status === opt.value
                  ? "bg-[#f0198f] hover:bg-[#f0198f]/40 text-white"
                  : "border-gray-300 text-gray-700 hover:bg-gray-100"
              }
            >
              {opt.label}
            </Button>
          ))}
        </div>

        {/* Campo de busca */}
        <div className="relative max-w-md w-full">
          <MagnifyingGlassIcon className="w-5 h-5 text-gray-400 absolute left-3 top-1/2 -translate-y-1/2" />
          <Input
            type="text"
            placeholder="Buscar por título, usuário ou status..."
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
            className="pl-10 pr-4 py-2 border-gray-200 rounded-lg focus-visible:ring-[#C41E73] focus-visible:border-[#C41E73]"
          />
        </div>
      </div>

      {/* 📋 Tabela */}
      <GerenciarAnunciosTable busca={busca} status={status} />
    </section>
  )
}
