'use client'

import { useState } from "react"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"
import { MagnifyingGlassIcon, PlusIcon } from "@heroicons/react/24/solid"
import GerenciarStaffTable from "../components/staff-table"
import NovoStaffModal from "../components/novo-staff-modal"

export default function AdminStaffPage() {
  const [busca, setBusca] = useState("")
  const [openNovoStaff, setOpenNovoStaff] = useState(false)

  return (
    <section>
      {/* Cabeçalho */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between mb-6 gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-800">Gerenciar Staff</h1>
          <p className="text-sm text-gray-500">
            Veja todos os membros da equipe cadastrados, visualize cargos e gerencie permissões.
          </p>
        </div>

        <Button
          onClick={() => setOpenNovoStaff(true)}
          className="flex items-center gap-2 bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-semibold"
        >
          <PlusIcon className="w-4 h-4" />
          Novo Staff
        </Button>
      </div>

      {/* 🔍 Campo de busca */}
      <div className="mb-6 flex items-center max-w-md relative">
        <MagnifyingGlassIcon className="w-5 h-5 text-gray-400 absolute left-3" />
        <Input
          type="text"
          placeholder="Buscar por nome, e-mail ou CPF..."
          value={busca}
          onChange={(e) => setBusca(e.target.value)}
          className="pl-10 pr-4 py-2 w-full border-gray-200 rounded-lg focus-visible:ring-[#C41E73] focus-visible:border-[#C41E73]"
        />
      </div>

      {/* Tabela */}
      <GerenciarStaffTable busca={busca} />

      {/* Modal de criação */}
      <NovoStaffModal open={openNovoStaff} onOpenChange={setOpenNovoStaff} />
    </section>
  )
}
