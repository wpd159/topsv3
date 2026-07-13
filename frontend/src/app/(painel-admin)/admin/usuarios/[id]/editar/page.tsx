"use client"

import { useEffect, useState } from "react"
import { useParams, useRouter } from "next/navigation"
import { toast } from "sonner"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  ArrowLeftIcon,
  CalendarIcon,
  CheckCircleIcon,
  CreditCardIcon,
  EnvelopeIcon,
  PhoneIcon,
  UserIcon,
  XCircleIcon,
} from "@heroicons/react/24/solid"
import { maskCPF, maskPhoneBR, normalizeCPF, normalizePhoneBR } from "@/utils/mask"

type UsuarioEdit = {
  id: number | string
  nomeCompleto?: string
  username?: string
  email?: string
  telefone?: string
  cpf?: string
  dataNascimento?: string
  criadoEm?: string
  status?: "ATIVO" | "INATIVO"
}

export default function EditarUsuarioPage() {
  const { id } = useParams()
  const router = useRouter()

  const [carregando, setCarregando] = useState(true)
  const [salvando, setSalvando] = useState(false)
  const [usuario, setUsuario] = useState<UsuarioEdit>({
    id: String(id),
    nomeCompleto: "",
    username: "",
    email: "",
    telefone: "",
    cpf: "",
    dataNascimento: "",
    criadoEm: "",
    status: "ATIVO",
  })

  useEffect(() => {
    if (!id) return
    ;(async () => {
      try {
        setCarregando(true)
        const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/usuarios/${id}`, {
          credentials: "include",
        })

        if (!res.ok) throw new Error(`Erro ${res.status}`)

        const data = await res.json()
        setUsuario({
          id: data.id,
          nomeCompleto: data.nomeCompleto ?? "",
          username: data.username ?? "",
          email: data.email ?? "",
          telefone: maskPhoneBR(data.telefone ?? ""),
          cpf: maskCPF(data.cpf ?? ""),
          dataNascimento: data.dataNascimento ?? "",
          criadoEm: data.criadoEm ?? data.dataCadastro ?? "",
          status: (data.status as "ATIVO" | "INATIVO") ?? "ATIVO",
        })
      } catch {
        toast.error("Falha ao carregar usuário.")
      } finally {
        setCarregando(false)
      }
    })()
  }, [id])

  const handleChange = (field: keyof UsuarioEdit, value: string) => {
    if (field === "cpf") value = maskCPF(value)
    if (field === "telefone") value = maskPhoneBR(value)
    setUsuario((prev) => ({ ...prev, [field]: value }))
  }

  const handleCancel = () => router.push(`/admin/usuarios/${id}`)

  const handleSave = async () => {
    try {
      setSalvando(true)

      const payload = {
        nomeCompleto: usuario.nomeCompleto?.trim() || null,
        email: usuario.email?.trim() || null,
        username: usuario.username?.trim() || null,
        telefone: normalizePhoneBR(usuario.telefone || "") || null,
        cpf: normalizeCPF(usuario.cpf || "") || null,
        dataNascimento: usuario.dataNascimento || null,
        status: usuario.status,
      }

      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/usuarios/${id}/editar-admin`, {
        method: "PUT",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      })

      if (!res.ok) {
        const message = await res.text()
        throw new Error(message || "Erro ao salvar alterações")
      }

      toast.success("Usuário atualizado com sucesso.")
    } catch (error: any) {
      toast.error(error?.message || "Falha ao salvar usuário.")
    } finally {
      setSalvando(false)
    }
  }

  if (carregando) {
    return <div className="py-10 text-center text-gray-500">Carregando...</div>
  }

  return (
    <section className="pb-8">
      <div className="mb-6 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Button variant="ghost" size="icon" onClick={handleCancel} className="text-gray-600 hover:bg-gray-100">
            <ArrowLeftIcon className="h-5 w-5" />
          </Button>
          <div>
            <h1 className="text-2xl font-bold text-gray-800">Editar usuário</h1>
            <p className="text-sm text-gray-500">Ajuste os dados da conta com uma edição rápida e organizada.</p>
          </div>
        </div>

        <div className="flex gap-2">
          <Button variant="outline" onClick={handleCancel} className="border-gray-300 text-gray-600 hover:bg-gray-100">
            Cancelar
          </Button>
          <Button onClick={handleSave} disabled={salvando} className="bg-[#C41E73] text-white hover:bg-[#a51861]">
            {salvando ? "Salvando..." : "Salvar alterações"}
          </Button>
        </div>
      </div>

      <div className="space-y-4">
        <div className="rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
          <h2 className="mb-4 flex items-center gap-2 text-base font-semibold text-gray-800">
            <UserIcon className="h-5 w-5 text-[#C41E73]" />
            Dados do usuário
          </h2>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <div>
              <Label className="text-sm text-gray-700">Nome completo</Label>
              <Input
                value={usuario.nomeCompleto || ""}
                onChange={(e) => handleChange("nomeCompleto", e.target.value)}
                className="mt-1 h-10"
              />
            </div>

            <div>
              <Label className="text-sm text-gray-700">Nome de usuário</Label>
              <Input
                value={usuario.username || ""}
                onChange={(e) => handleChange("username", e.target.value)}
                className="mt-1 h-10"
              />
            </div>

            <div>
              <Label className="flex items-center gap-1 text-sm text-gray-700">
                <EnvelopeIcon className="h-4 w-4 text-[#C41E73]" /> Email
              </Label>
              <Input
                type="email"
                value={usuario.email || ""}
                onChange={(e) => handleChange("email", e.target.value)}
                className="mt-1 h-10"
              />
            </div>

            <div>
              <Label className="flex items-center gap-1 text-sm text-gray-700">
                <PhoneIcon className="h-4 w-4 text-[#C41E73]" /> Telefone
              </Label>
              <Input
                value={usuario.telefone || ""}
                onChange={(e) => handleChange("telefone", e.target.value)}
                className="mt-1 h-10"
                inputMode="numeric"
                placeholder="(11) 99999-9999"
              />
              <p className="mt-1 text-xs text-gray-500">Máscara somente visual.</p>
            </div>

            <div>
              <Label className="flex items-center gap-1 text-sm text-gray-700">
                <CreditCardIcon className="h-4 w-4 text-[#C41E73]" /> CPF
              </Label>
              <Input
                value={usuario.cpf || ""}
                onChange={(e) => handleChange("cpf", e.target.value)}
                className="mt-1 h-10"
                inputMode="numeric"
                placeholder="000.000.000-00"
              />
            </div>

            <div>
              <Label className="flex items-center gap-1 text-sm text-gray-700">
                <CalendarIcon className="h-4 w-4 text-[#C41E73]" /> Data de nascimento
              </Label>
              <Input
                type="date"
                value={usuario.dataNascimento || ""}
                onChange={(e) => handleChange("dataNascimento", e.target.value)}
                className="mt-1 h-10"
              />
            </div>
          </div>
        </div>

        <div className="rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
          <h2 className="mb-4 flex items-center gap-2 text-base font-semibold text-gray-800">
            <CalendarIcon className="h-5 w-5 text-[#C41E73]" />
            Conta
          </h2>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <div>
              <p className="text-sm text-gray-700">Status</p>
              <div className="mt-2">
                <Badge
                  className={`flex w-fit items-center gap-1 rounded-md border px-2 py-1 text-[12px] font-medium ${
                    usuario.status === "ATIVO"
                      ? "border-green-300 bg-green-100 text-green-700"
                      : "border-red-300 bg-red-100 text-red-700"
                  }`}
                >
                  {usuario.status === "ATIVO" ? <CheckCircleIcon className="h-3.5 w-3.5" /> : <XCircleIcon className="h-3.5 w-3.5" />}
                  {usuario.status === "ATIVO" ? "Ativo" : "Inativo"}
                </Badge>
              </div>
            </div>

            <div>
              <p className="text-sm text-gray-700">Data de cadastro</p>
              <div className="mt-1 flex h-10 items-center rounded-md border border-gray-200 bg-gray-50 px-3 text-sm text-gray-700">
                {usuario.criadoEm ? new Date(usuario.criadoEm).toLocaleString("pt-BR") : "-"}
              </div>
            </div>
          </div>
        </div>

      </div>
    </section>
  )
}
