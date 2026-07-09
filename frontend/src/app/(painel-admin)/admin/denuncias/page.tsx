'use client'

import { useState, useEffect } from "react"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Card } from "@/components/ui/card"
import {
  CheckCircleIcon,
  ExclamationTriangleIcon,
  ClockIcon,
  EnvelopeIcon,
  UserIcon,
  LinkIcon,
  TrashIcon,
  XCircleIcon,
} from "@heroicons/react/24/solid"
import { toast } from "sonner"
import DenunciaPunirModal from "../components/denuncia-punir-modal"
import { useAuth } from "@/context/AuthContext"

export default function AdminDenunciasPage() {
  const { usuario } = useAuth()
  const [denuncias, setDenuncias] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const [openPunir, setOpenPunir] = useState(false)
  const [denunciaSelecionada, setDenunciaSelecionada] = useState<any>(null)

  // 🔹 Buscar denúncias da API
  useEffect(() => {
    const fetchDenuncias = async () => {
      try {
        const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/denuncias`, {
          method: "GET",
          credentials: "include",
        })
        if (!res.ok) throw new Error("Erro ao carregar denúncias.")
        const data = await res.json()
        setDenuncias(data)
      } catch (err) {
        toast.error("Falha ao carregar denúncias.")
      } finally {
        setLoading(false)
      }
    }

    fetchDenuncias()
  }, [])

  const getBadgeColor = (status: string) => {
    switch (status) {
      case "PENDENTE":
        return "bg-yellow-100 text-yellow-700 border-yellow-300"
      case "IGNORADO":
        return "bg-gray-100 text-gray-700 border-gray-300"
      case "PUNIDO":
        return "bg-red-100 text-red-700 border-red-300"
      default:
        return "bg-gray-100 text-gray-600 border-gray-300"
    }
  }

  const punirDenuncia = (denuncia: any) => {
    setDenunciaSelecionada(denuncia)
    setOpenPunir(true)
  }

  // 🔹 Chama a API de punição
  const confirmarPunicao = async (id: number, justificativa: string) => {
    try {
      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/denuncias/${id}/punir`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({
          usuarioId: usuario?.id,
          justificativa,
        }),
      })
      if (!res.ok) throw new Error("Erro ao punir denúncia.")

      setDenuncias((prev) =>
        prev.map((d) =>
          d.id === id ? { ...d, status: "PUNIDO" } : d
        )
      )
      toast.success("Anúncio removido e e-mail enviado ao anunciante.")
    } catch (err) {
      toast.error("Falha ao aplicar punição.")
    } finally {
      setOpenPunir(false)
    }
  }

  // 🔹 Chama a API de ignorar denúncia
  const naoPunirDenuncia = async (id: number) => {
    try {
      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/denuncias/${id}/ignorar`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({
          usuarioId: usuario?.id,
        }),
      })
      if (!res.ok) throw new Error("Erro ao ignorar denúncia.")

      setDenuncias((prev) =>
        prev.map((d) =>
          d.id === id ? { ...d, status: "IGNORADO" } : d
        )
      )
      toast("Denúncia marcada como ignorada.")
    } catch (err) {
      toast.error("Falha ao ignorar denúncia.")
    }
  }

  if (loading) {
    return (
      <div className="flex justify-center items-center h-64 text-gray-500">
        Carregando denúncias...
      </div>
    )
  }

  return (
    <section className="pb-12">
      {/* Header */}
      <div className="flex flex-col gap-1 mb-8">
        <h1 className="text-2xl font-bold text-gray-800">Denúncias de Anúncios</h1>
        <p className="text-sm text-gray-500">
          Analise as denúncias enviadas pelos usuários e decida se o anúncio deve ser punido.
        </p>
      </div>

      {/* Lista de Denúncias */}
      {denuncias.length === 0 ? (
        <p className="text-gray-500 text-sm">Nenhuma denúncia registrada.</p>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {denuncias.map((denuncia) => {
            // 🔧 Corrigir o link do anúncio no frontend
            const linkCorrigido = denuncia.linkAnuncio?.replace("/admin/", "/anuncio/")

            return (
              <Card
                key={denuncia.id}
                className="p-5 flex flex-col justify-between border border-gray-200 shadow-sm hover:shadow-md transition"
              >
                {/* Header */}
                <div className="flex justify-between items-center mb-2">
                  <Badge
                    className={`text-[11px] font-medium border px-2 py-1 rounded-md flex items-center gap-1 ${getBadgeColor(
                      denuncia.status
                    )}`}
                  >
                    {denuncia.status === "PENDENTE" && <ClockIcon className="w-3.5 h-3.5" />}
                    {denuncia.status === "IGNORADO" && <CheckCircleIcon className="w-3.5 h-3.5" />}
                    {denuncia.status === "PUNIDO" && <ExclamationTriangleIcon className="w-3.5 h-3.5" />}
                    {denuncia.status}
                  </Badge>

                  <p className="text-xs text-gray-500">
                    {new Date(denuncia.dataAbertura).toLocaleString("pt-BR")}
                  </p>
                </div>

                {/* Corpo */}
                <div className="flex flex-col gap-2 flex-1">
                  <div className="flex items-center gap-1 text-sm text-gray-700">
                    <UserIcon className="w-4 h-4 text-[#C41E73]" />
                    <span>{denuncia.username}</span>
                  </div>
                  <div className="flex items-center gap-1 text-sm text-gray-500">
                    <EnvelopeIcon className="w-4 h-4 text-gray-400" />
                    <span>{denuncia.email}</span>
                  </div>

                  <div className="flex items-center gap-1 text-sm text-blue-600 mt-2">
                    <LinkIcon className="w-4 h-4" />
                    <a
                      href={linkCorrigido}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="underline hover:text-blue-800"
                    >
                      Ver anúncio
                    </a>
                  </div>

                  <p className="text-sm text-gray-700 mt-3 leading-relaxed">
                    <span className="font-semibold text-gray-800">Motivo:</span>{" "}
                    {denuncia.motivo.replaceAll("_", " ")}
                  </p>

                  <p className="text-sm text-gray-600 leading-relaxed mt-1">
                    {denuncia.descricao}
                  </p>
                </div>

                {/* Botões */}
                {denuncia.status === "PENDENTE" && (
                  <div className="flex flex-col gap-2 mt-4 pt-3 border-t border-gray-100">
                    <Button
                      variant="outline"
                      onClick={() => punirDenuncia(denuncia)}
                      className="w-full flex items-center justify-center gap-2 text-red-600 border-red-300 hover:bg-red-50"
                    >
                      <TrashIcon className="w-4 h-4" />
                      Punir (Excluir Anúncio)
                    </Button>

                    <Button
                      variant="outline"
                      onClick={() => naoPunirDenuncia(denuncia.id)}
                      className="w-full flex items-center justify-center gap-2 text-gray-700 border-gray-300 hover:bg-gray-100"
                    >
                      <XCircleIcon className="w-4 h-4" />
                      Não Punir
                    </Button>
                  </div>
                )}
              </Card>
            )
          })}
        </div>
      )}

      {/* Modal de punição */}
      <DenunciaPunirModal
        open={openPunir}
        onOpenChange={setOpenPunir}
        denuncia={denunciaSelecionada}
        onConfirm={(justificativa: string) =>
          confirmarPunicao(denunciaSelecionada?.id, justificativa)
        }
      />
    </section>
  )
}
