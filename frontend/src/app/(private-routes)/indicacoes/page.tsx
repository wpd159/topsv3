"use client"

import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { toast } from "sonner"
import {
  UserGroupIcon,
  CurrencyDollarIcon,
  ArrowTrendingUpIcon,
  ClipboardDocumentIcon,
} from "@heroicons/react/24/solid"
import { useAuth } from "@/context/AuthContext"

export default function IndicacoesPage() {
  const { usuario } = useAuth()

  if (!usuario) {
    return (
      <div className="p-6 text-center text-gray-600">
        Carregando informações...
      </div>
    )
  }

  const copiar = async () => {
    await navigator.clipboard.writeText(usuario.linkIndicacao)
    toast.success("Link copiado!")
  }

  return (
    <div className="max-w-5xl mx-auto py-6 space-y-10">
      <h1 className="text-2xl font-bold text-gray-900">
        Indique e Ganhe Créditos
      </h1>

      {/* LINK DE INDICAÇÃO */}
      <Card className="p-6 bg-gray-50 border border-gray-200">
        <p className="text-sm text-gray-600 mb-2">Seu link de indicação</p>

        <div className="flex items-center gap-3">
          <Input
            readOnly
            value={usuario.linkIndicacao}
            className="font-mono text-sm"
          />

          <Button
            onClick={copiar}
            className="flex items-center gap-2 bg-[#FC1EAD] hover:bg-[#d81893]"
          >
            <ClipboardDocumentIcon className="w-5 h-5 text-white" />
            Copiar
          </Button>
        </div>
      </Card>

      {/* MÉTRICAS */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">

        {/* TOTAL DE INDICADOS */}
        <Card className="p-6 flex flex-col items-start gap-3">
          <UserGroupIcon className="w-8 h-8 text-[#FC1EAD]" />
          <p className="text-gray-600 text-sm">Pessoas indicadas</p>
          <p className="text-3xl font-bold text-gray-900">
            {usuario.totalIndicados}
          </p>
        </Card>

        {/* CRÉDITOS GANHOS */}
        <Card className="p-6 flex flex-col items-start gap-3">
          <CurrencyDollarIcon className="w-8 h-8 text-green-600" />
          <p className="text-gray-600 text-sm">Créditos ganhos</p>
          <p className="text-3xl font-bold text-gray-900">
            {usuario.creditosIndicacaoGanhos}
          </p>
        </Card>

        {/* CRÉDITO POR INDICAÇÃO */}
        <Card className="p-6 flex flex-col items-start gap-3">
          <ArrowTrendingUpIcon className="w-8 h-8 text-blue-600" />
          <p className="text-gray-600 text-sm">Crédito por nova indicação</p>
          <p className="text-3xl font-bold text-gray-900">
            +{usuario.creditosPorIndicacao}
          </p>
        </Card>
      </div>
    </div>
  )
}
