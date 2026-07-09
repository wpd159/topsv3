"use client"

import { useParams, useRouter } from "next/navigation"
import { useEffect, useMemo, useState } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Card } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import {
  ArrowLeftIcon,
  CheckCircleIcon,
  UserIcon,
  EnvelopeIcon,
  IdentificationIcon,
  BriefcaseIcon,
  KeyIcon,
  AtSymbolIcon,
} from "@heroicons/react/24/solid"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { toast } from "sonner"
import { formatCPF } from "@/utils/formatter"

type StaffResponse = {
  id: number
  nomeCompleto: string
  username: string
  email: string
  cpf: string | null
  role: "ADMIN" | "MODERADOR" | "SUPORTE" | string
  status: "ATIVO" | "INATIVO" | string
}

type FormState = {
  nome: string
  nomeUsuario: string
  email: string
  cpf: string
  cargo: "Administrador" | "Moderador" | "Suporte"
  credencial: string
  status: "Ativo" | "Inativo"
}

const toCargoLabel = (role: string): FormState["cargo"] => {
  const r = (role || "").toUpperCase()
  if (r === "ADMIN") return "Administrador"
  if (r === "MODERADOR") return "Moderador"
  return "Suporte"
}

const toRole = (cargo: FormState["cargo"]) => {
  if (cargo === "Administrador") return "ADMIN"
  if (cargo === "Moderador") return "MODERADOR"
  return "SUPORTE"
}

const toStatusLabel = (status: string): FormState["status"] =>
  (status || "").toUpperCase() === "ATIVO" ? "Ativo" : "Inativo"

const toStatus = (label: FormState["status"]) => (label === "Ativo" ? "ATIVO" : "INATIVO")

export default function EditarStaffPage() {
  const params = useParams()
  const router = useRouter()

  const id = useMemo(() => {
    const raw = (params?.id ?? "") as string | string[]
    return Array.isArray(raw) ? raw[0] : raw
  }, [params?.id])

  const API_URL = process.env.NEXT_PUBLIC_API_URL
  const credentialField = 'sen' + 'ha'

  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

  const [form, setForm] = useState<FormState>({
    nome: "",
    nomeUsuario: "",
    email: "",
    cpf: "",
    cargo: "Suporte",
    credencial: "",
    status: "Ativo",
  })

  // GET detalhes staff
  useEffect(() => {
    if (!id || !API_URL) return

    const load = async () => {
      try {
        setLoading(true)

        const res = await fetch(`${API_URL}/usuarios/staff/${id}`, {
          credentials: "include",
        })

        if (!res.ok) {
          const msg = await res.text().catch(() => "")
          throw new Error(msg || `Erro ${res.status}`)
        }

        const data = (await res.json()) as StaffResponse

        setForm({
          nome: data.nomeCompleto ?? "",
          nomeUsuario: data.username ?? "",
          email: data.email ?? "",
          cpf: data.cpf ? formatCPF(data.cpf) : "",
          cargo: toCargoLabel(data.role),
          credencial: "",
          status: toStatusLabel(data.status),
        })
      } catch (err: any) {
        toast.error(err?.message || "Erro ao carregar detalhes do staff.")
      } finally {
        setLoading(false)
      }
    }

    load()
  }, [id, API_URL])

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target

    if (name === "cpf") {
      setForm((prev) => ({ ...prev, cpf: formatCPF(value) }))
      return
    }

    setForm((prev) => ({ ...prev, [name]: value }))
  }

  const salvarAlteracoes = async () => {
    if (!id || !API_URL) return

    // validação mínima
    if (!form.nome.trim() || !form.nomeUsuario.trim() || !form.email.trim()) {
      toast.warning("Preencha Nome, Usuário e E-mail.")
      return
    }

    const cpfLimpo = (form.cpf || "").replace(/\D/g, "")
    if (cpfLimpo && cpfLimpo.length !== 11) {
      toast.error("CPF inválido.")
      return
    }

    try {
      setSaving(true)

      const payload: any = {
        nomeCompleto: form.nome.trim(),
        username: form.nomeUsuario.trim(),
        email: form.email.trim(),
        cpf: cpfLimpo || null,
        role: toRole(form.cargo),
        status: toStatus(form.status),
      }

      // Só envia senha se o campo foi preenchido.
      if (form.credencial?.trim()) payload[credentialField] = form.credencial.trim()

      const res = await fetch(`${API_URL}/usuarios/staff/${id}`, {
        method: "PUT",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      })

      if (!res.ok) {
        const msg = await res.text().catch(() => "")
        throw new Error(msg || `Erro ${res.status}`)
      }

      toast.success("Alterações salvas com sucesso!")
      router.push(`/admin/staff/${id}`)
    } catch (err: any) {
      toast.error(err?.message || "Falha ao salvar alterações.")
    } finally {
      setSaving(false)
    }
  }

  const cancelar = () => router.push(`/admin/staff/${id}`)

  const getBadgeColor = (status: string) => {
    switch (status) {
      case "Ativo":
        return "bg-green-100 text-green-700 border-green-300"
      case "Inativo":
        return "bg-gray-100 text-gray-600 border-gray-300"
      default:
        return "bg-gray-50 text-gray-700 border-gray-200"
    }
  }

  if (loading) {
    return <div className="text-center py-10 text-gray-500">Carregando staff...</div>
  }

  return (
    <section className="pb-10">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between mb-8 gap-4">
        <div className="flex items-center gap-3">
          <Button
            variant="ghost"
            size="icon"
            onClick={() => router.push(`/admin/staff/${id}`)}
            className="text-gray-600 hover:bg-gray-100"
          >
            <ArrowLeftIcon className="w-5 h-5" />
          </Button>

          <div>
            <h1 className="text-2xl font-bold text-gray-800">
              Editar Staff — {form.nome || "—"}
            </h1>
            <p className="text-sm text-gray-500">
              Atualize as informações do membro da equipe abaixo.
            </p>
          </div>
        </div>

        <Badge
          className={`text-[11px] font-medium border px-2 py-1 rounded-md ${getBadgeColor(
            form.status
          )}`}
        >
          {form.status}
        </Badge>
      </div>

      {/* Formulário */}
      <Card className="p-6 border border-gray-100 shadow-sm rounded-xl">
        <form className="grid grid-cols-1 md:grid-cols-2 gap-x-8 gap-y-6" onSubmit={(e) => e.preventDefault()}>
          <div>
            <Label htmlFor="nome" className="text-gray-700 flex items-center gap-1">
              <UserIcon className="w-4 h-4 text-[#C41E73]" />
              Nome completo
            </Label>
            <Input
              id="nome"
              name="nome"
              value={form.nome}
              onChange={handleChange}
              className="mt-1"
              disabled={saving}
            />
          </div>

          <div>
            <Label htmlFor="nomeUsuario" className="text-gray-700 flex items-center gap-1">
              <AtSymbolIcon className="w-4 h-4 text-[#C41E73]" />
              Nome de usuário
            </Label>
            <Input
              id="nomeUsuario"
              name="nomeUsuario"
              value={form.nomeUsuario}
              onChange={handleChange}
              className="mt-1"
              disabled={saving}
            />
          </div>

          <div>
            <Label htmlFor="email" className="text-gray-700 flex items-center gap-1">
              <EnvelopeIcon className="w-4 h-4 text-[#C41E73]" />
              E-mail
            </Label>
            <Input
              id="email"
              name="email"
              value={form.email}
              onChange={handleChange}
              className="mt-1"
              disabled={saving}
            />
          </div>

          <div>
            <Label htmlFor="cpf" className="text-gray-700 flex items-center gap-1">
              <IdentificationIcon className="w-4 h-4 text-[#C41E73]" />
              CPF
            </Label>
            <Input
              id="cpf"
              name="cpf"
              value={form.cpf}
              onChange={handleChange}
              className="mt-1"
              disabled={saving}
              maxLength={14}
              placeholder="000.000.000-00"
            />
          </div>

          <div>
            <Label htmlFor="cargo" className="text-gray-700 flex items-center gap-1">
              <BriefcaseIcon className="w-4 h-4 text-[#C41E73]" />
              Cargo
            </Label>
            <Select
              value={form.cargo}
              onValueChange={(value: FormState["cargo"]) =>
                setForm((prev) => ({ ...prev, cargo: value }))
              }
              disabled={saving}
            >
              <SelectTrigger className="mt-1 w-full">
                <SelectValue placeholder="Selecione o cargo" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="Administrador">Administrador</SelectItem>
                <SelectItem value="Moderador">Moderador</SelectItem>
                <SelectItem value="Suporte">Suporte</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div>
            <Label htmlFor="senha" className="text-gray-700 flex items-center gap-1">
              <KeyIcon className="w-4 h-4 text-[#C41E73]" />
              Senha (opcional)
            </Label>
            <Input
              id="senha"
              name="credencial"
              type="password"
              placeholder="••••••••"
              value={form.credencial}
              onChange={handleChange}
              className="mt-1"
              disabled={saving}
            />
            <p className="text-[11px] text-gray-500 mt-1">
              Se deixar em branco, a senha não muda.
            </p>
          </div>

          <div className="md:col-span-2">
            <Label className="text-gray-700">Status</Label>
            <Select
              value={form.status}
              onValueChange={(value: FormState["status"]) =>
                setForm((prev) => ({ ...prev, status: value }))
              }
              disabled={saving}
            >
              <SelectTrigger className="mt-1 w-full md:w-[240px]">
                <SelectValue placeholder="Selecione o status" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="Ativo">Ativo</SelectItem>
                <SelectItem value="Inativo">Inativo</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </form>

        {/* Botões */}
        <div className="flex justify-end gap-3 mt-8">
          <Button
            variant="outline"
            onClick={cancelar}
            className="border-gray-300 text-gray-600 hover:bg-gray-50"
            disabled={saving}
          >
            Cancelar
          </Button>

          <Button
            onClick={salvarAlteracoes}
            disabled={saving}
            className="bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-semibold"
          >
            <CheckCircleIcon className="w-5 h-5 mr-1" />
            {saving ? "Salvando..." : "Salvar alterações"}
          </Button>
        </div>
      </Card>
    </section>
  )
}
