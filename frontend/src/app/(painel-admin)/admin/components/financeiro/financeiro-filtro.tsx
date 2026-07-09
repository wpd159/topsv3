'use client'

import { useState } from 'react'
import { Input } from '@/components/ui/input'
import { Select, SelectTrigger, SelectValue, SelectContent, SelectItem } from '@/components/ui/select'
import { Button } from '@/components/ui/button'
import { FunnelIcon } from '@heroicons/react/24/outline'

export default function FinanceiroFiltro() {
  const [filtros, setFiltros] = useState({ tipo: '', status: '', periodo: '' })

  return (
    <div className="bg-white border border-gray-100 rounded-xl shadow-sm p-5 flex flex-wrap gap-4 items-end justify-between">
      <div className="flex flex-wrap gap-4">

        <div>
          <p className="text-sm text-gray-600 mb-1">Status</p>
          <Select onValueChange={(v) => setFiltros((f) => ({ ...f, status: v }))}>
            <SelectTrigger className="w-[160px]">
              <SelectValue placeholder="Todos" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="pendente">Pendente</SelectItem>
              <SelectItem value="pago">Pago</SelectItem>
              <SelectItem value="cancelado">Cancelado</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div>
          <p className="text-sm text-gray-600 mb-1">Período</p>
          <Input type="month" className="w-[180px]" />
        </div>
      </div>

      <Button className="bg-[#FC1EAD] hover:bg-[#e01a9a] text-white flex items-center gap-1 font-semibold">
        <FunnelIcon className="w-4 h-4" />
        Filtrar
      </Button>
    </div>
  )
}
