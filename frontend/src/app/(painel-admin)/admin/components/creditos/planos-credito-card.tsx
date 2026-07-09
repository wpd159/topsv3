'use client'

import { useState } from 'react'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import {
  PencilSquareIcon,
  BanknotesIcon,
  CheckIcon,
} from '@heroicons/react/24/solid'
import { cn } from '@/lib/utils'

interface Plano {
  id: number
  nome: string
  valor: number
  creditos: number
  cor: string
}

interface Props {
  plano: Plano
  atualizarPlano: (id: number, valor: number, creditos: number) => void
}

export default function PlanosCreditoCard({ plano, atualizarPlano }: Props) {
  const [open, setOpen] = useState(false)
  const [valor, setValor] = useState(plano.valor)
  const [creditos, setCreditos] = useState(plano.creditos)

  const handleSalvar = async () => {
    await atualizarPlano(plano.id, valor, creditos)
    setOpen(false)
  }

  return (
    <>
      <Card
        key={plano.id}
        className={cn(
          'bg-white border border-gray-100 rounded-xl shadow-sm hover:shadow-md transition-all duration-200 overflow-hidden flex flex-col justify-between'
        )}
      >
        {/* Header com gradiente */}
        <div className={cn('px-5 py-3 border-b bg-gradient-to-r', plano.cor)}>
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-sm font-medium text-gray-600">
                Plano {plano.nome}
              </h2>
            </div>
            <div className="p-2 rounded-md bg-white/70 border border-gray-100">
              <BanknotesIcon className="w-5 h-5 text-[#C41E73]" />
            </div>
          </div>
        </div>

        {/* Corpo */}
        <div className="p-5 flex-1 flex flex-col justify-between">
          <div className="mb-4">
            <p className="text-gray-600 text-sm mb-1">
              Valor:{' '}
              <span className="font-semibold text-gray-800">
                R$ {plano.valor.toFixed(2)}
              </span>
            </p>
            <p className="text-gray-600 text-sm">
              Créditos:{' '}
              <span className="font-semibold text-gray-800">
                {plano.creditos}
              </span>
            </p>
          </div>

          <div className="border-t mt-4 pt-3 flex justify-end">
            <Button
              size="sm"
              variant="outline"
              className="text-[#C41E73] border-[#C41E73]/40 hover:bg-[#FC1EAD]/10 flex items-center gap-1"
              onClick={() => setOpen(true)}
            >
              <PencilSquareIcon className="w-4 h-4" />
              Editar
            </Button>
          </div>
        </div>
      </Card>

      {/* Modal */}
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle className="text-lg font-semibold text-gray-800">
              Editar Plano {plano.nome}
            </DialogTitle>
            <p className="text-sm text-gray-500">
              Altere o valor e a quantidade de créditos oferecidos.
            </p>
          </DialogHeader>

          <div className="mt-4 space-y-4">
            <div>
              <label className="text-sm text-gray-600">Valor (R$)</label>
              <Input
                type="number"
                step="0.01"
                value={valor}
                onChange={e => setValor(parseFloat(e.target.value))}
                className="mt-1"
              />
            </div>
            <div>
              <label className="text-sm text-gray-600">Créditos Oferecidos</label>
              <Input
                type="number"
                value={creditos}
                onChange={e => setCreditos(parseInt(e.target.value))}
                className="mt-1"
              />
            </div>
          </div>

          <DialogFooter className="mt-6 flex justify-end gap-2">
            <Button
              variant="outline"
              onClick={() => setOpen(false)}
              className="border-gray-300 text-gray-600 hover:bg-gray-100"
            >
              Cancelar
            </Button>
            <Button
              onClick={handleSalvar}
              className="bg-green-600 hover:bg-green-700 text-white flex items-center gap-1"
            >
              <CheckIcon className="w-4 h-4" />
              Salvar
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
