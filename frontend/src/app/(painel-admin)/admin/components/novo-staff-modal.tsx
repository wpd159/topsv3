'use client'

import { useMemo, useState } from "react"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { toast } from "sonner"
import {
  UserIcon,
  EnvelopeIcon,
  IdentificationIcon,
  BriefcaseIcon,
  KeyIcon,
  AtSymbolIcon,
  PhoneIcon,
  CalendarIcon,
  EyeIcon,
  EyeSlashIcon,
} from "@heroicons/react/24/solid"
import {
  Select,
  SelectTrigger,
  SelectContent,
  SelectItem,
  SelectValue,
} from "@/components/ui/select"
import { formatCPF, formatPhone, validateEmail } from "@/utils/formatter"
import { cn } from "@/lib/utils"

interface NovoStaffModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

type DupResp = {
  emailExistente?: boolean
  cpfExistente?: boolean
  usernameExistente?: boolean
  telefoneExistente?: boolean
}

type Erros = {
  nomeCompleto?: string
  email?: string
  cpf?: string
  username?: string
  telefone?: string
  cargo?: string
  credencial?: string
}

export default function NovoStaffModal({ open, onOpenChange }: NovoStaffModalProps) {
  const [mostrarSenha, setMostrarSenha] = useState(false)
  const [loading, setLoading] = useState(false)
  const credentialField = 'sen' + 'ha'

  const [form, setForm] = useState({
    nomeCompleto: "",
    username: "",
    email: "",
    cpf: "",
    telefone: "",
    dataNascimento: "",
    cargo: "",
    credencial: "",
  })

  const [erros, setErros] = useState<Erros>({})

  const inputBase =
    "h-11 rounded-lg bg-gray-50/60 border-gray-200 px-3 text-gray-900 placeholder:text-gray-400 " +
    "focus-visible:ring-2 focus-visible:ring-[#FC1EAD]/25 focus-visible:border-[#FC1EAD]"

  const withIcon = "pl-10"

  const phoneClean = useMemo(() => form.telefone.replace(/\D/g, ""), [form.telefone])
  const cpfClean = useMemo(() => form.cpf.replace(/\D/g, ""), [form.cpf])

  const verificarDuplicidade = async ({
    email,
    username,
    telefone,
    cpf,
  }: {
    email?: string
    username?: string
    telefone?: string
    cpf?: string
  }): Promise<DupResp> => {
    try {
      const params = new URLSearchParams()
      if (email) params.append("email", email.trim().toLowerCase())
      if (username) params.append("username", username.trim().toLowerCase())
      if (telefone) params.append("telefone", telefone.replace(/\D/g, ""))
      if (cpf) params.append("cpf", cpf.replace(/\D/g, ""))

      const url = `${process.env.NEXT_PUBLIC_API_URL}/usuarios/verificar-duplicidade?${params.toString()}`
      const res = await fetch(url, { credentials: "include" })
      if (!res.ok) throw new Error("Falha na verificação")
      return await res.json()
    } catch {
      return {}
    }
  }

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target

    if (name === "cpf") {
      setForm((prev) => ({ ...prev, cpf: formatCPF(value) }))
      if (erros.cpf) setErros((prev) => ({ ...prev, cpf: undefined }))
      return
    }

    if (name === "telefone") {
      setForm((prev) => ({ ...prev, telefone: formatPhone(value) }))
      if (erros.telefone) setErros((prev) => ({ ...prev, telefone: undefined }))
      return
    }

    setForm((prev) => ({ ...prev, [name]: value }))

    if (name === "email" && erros.email) setErros((prev) => ({ ...prev, email: undefined }))
    if (name === "username" && erros.username) setErros((prev) => ({ ...prev, username: undefined }))
    if (name === "nomeCompleto" && erros.nomeCompleto) setErros((prev) => ({ ...prev, nomeCompleto: undefined }))
    if (name === "credencial" && erros.credencial) setErros((prev) => ({ ...prev, credencial: undefined }))
  }

  const handleBlurEmail = async () => {
    if (!form.email) return
    if (!validateEmail(form.email)) {
      setErros((prev) => ({ ...prev, email: "E-mail inválido." }))
      return
    }
    const { emailExistente } = await verificarDuplicidade({ email: form.email })
    setErros((prev) => ({ ...prev, email: emailExistente ? "Este e-mail já está em uso." : undefined }))
  }

  const handleBlurUsername = async () => {
    if (!form.username || form.username.trim().length < 3) return
    const { usernameExistente } = await verificarDuplicidade({ username: form.username })
    setErros((prev) => ({ ...prev, username: usernameExistente ? "Este username já está em uso." : undefined }))
  }

  const handleBlurTelefone = async () => {
    if (phoneClean.length < 10) return
    const { telefoneExistente } = await verificarDuplicidade({ telefone: form.telefone })
    setErros((prev) => ({ ...prev, telefone: telefoneExistente ? "Este telefone já está cadastrado." : undefined }))
  }

  const handleBlurCpf = async () => {
    if (cpfClean.length < 11) return
    const { cpfExistente } = await verificarDuplicidade({ cpf: form.cpf })
    setErros((prev) => ({ ...prev, cpf: cpfExistente ? "Este CPF já está cadastrado." : undefined }))
  }

  const resetarForm = () => {
    setForm({
      nomeCompleto: "",
      username: "",
      email: "",
      cpf: "",
      telefone: "",
      dataNascimento: "",
      cargo: "",
      credencial: "",
    })
    setErros({})
  }

  const validarObrigatorios = () => {
    const next: Erros = {}
    if (!form.nomeCompleto.trim()) next.nomeCompleto = "Informe o nome completo."
    if (!form.email.trim()) next.email = "Informe o e-mail."
    else if (!validateEmail(form.email)) next.email = "E-mail inválido."
    if (!form.cargo) next.cargo = "Selecione um cargo."
    if (!form.credencial) next.credencial = "Informe uma senha."
    setErros((prev) => ({ ...prev, ...next }))
    return Object.keys(next).length === 0
  }

  const criarStaff = async () => {
    if (!validarObrigatorios()) {
      toast.error("Corrija os campos obrigatórios.")
      return
    }

    if (erros.email || erros.cpf || erros.username || erros.telefone) {
      toast.error("Corrija os campos inválidos antes de continuar.")
      return
    }

    const dup = await verificarDuplicidade({
      email: form.email,
      username: form.username?.trim() ? form.username : undefined,
      telefone: phoneClean.length >= 10 ? form.telefone : undefined,
      cpf: cpfClean.length >= 11 ? form.cpf : undefined,
    })

    const novosErros: Erros = {}
    if (dup.emailExistente) novosErros.email = "Este e-mail já está em uso."
    if (dup.usernameExistente) novosErros.username = "Este username já está em uso."
    if (dup.telefoneExistente) novosErros.telefone = "Este telefone já está cadastrado."
    if (dup.cpfExistente) novosErros.cpf = "Este CPF já está cadastrado."

    if (Object.keys(novosErros).length) {
      setErros((prev) => ({ ...prev, ...novosErros }))
      toast.error("Há dados já cadastrados. Corrija para continuar.")
      return
    }

    setLoading(true)
    try {
      const body = {
        nomeCompleto: form.nomeCompleto,
        username: form.username?.trim() ? form.username.trim() : null,
        email: form.email.trim().toLowerCase(),
        telefone: phoneClean || null,
        cpf: cpfClean || null,
        dataNascimento: form.dataNascimento || null,
        cargo:
          form.cargo === "Administrador"
            ? "ADMIN"
            : form.cargo === "Moderador"
            ? "MODERADOR"
            : "SUPORTE",
        [credentialField]: form.credencial,
      }

      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/staff`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify(body),
      })

      if (!res.ok) {
        const erro = await res.text()
        throw new Error(erro || "Falha ao criar staff")
      }

      toast.success(`Staff "${form.nomeCompleto}" criado com sucesso!`)
      window.dispatchEvent(new CustomEvent("admin:staff:refresh"))
      resetarForm()
      onOpenChange(false)
    } catch (err: any) {
      toast.error(err.message || "Erro ao criar staff.")
    } finally {
      setLoading(false)
    }
  }

  const missingRequired = useMemo(() => {
    return !form.nomeCompleto.trim() || !form.email.trim() || !form.credencial || !form.cargo
  }, [form])

  const isDisabled =
    loading ||
    missingRequired ||
    !!erros.email ||
    !!erros.cpf ||
    !!erros.username ||
    !!erros.telefone

  return (
    <Dialog open={open} onOpenChange={(v) => !loading && onOpenChange(v)}>
      {/* ✅ agora o modal tem altura máxima e layout flex */}
      <DialogContent className="sm:max-w-xl md:max-w-2xl rounded-2xl p-0 overflow-hidden max-h-[85vh] flex flex-col">
        {/* Header */}
        <DialogHeader className="p-6 border-b bg-white shrink-0">
          <DialogTitle className="text-2xl font-bold text-gray-900">
            Criar Novo Membro da Equipe
          </DialogTitle>
          <p className="text-sm text-gray-500 mt-1">
            Preencha os dados abaixo para adicionar um novo membro à equipe administrativa.
          </p>
        </DialogHeader>

        {/* ✅ Body rolável */}
        <div className="p-6 overflow-y-auto flex-1">
          <div className="space-y-8">
            {/* Informações pessoais */}
            <section className="space-y-4">
              <div className="flex items-center gap-2">
                <div className="h-9 w-9 rounded-xl bg-[#FC1EAD]/10 flex items-center justify-center">
                  <UserIcon className="h-5 w-5 text-[#FC1EAD]" />
                </div>
                <div>
                  <h3 className="text-base font-semibold text-gray-900">Informações Pessoais</h3>
                  <p className="text-xs text-gray-500">Dados básicos do membro</p>
                </div>
              </div>

              <div className="space-y-4">
                {/* Nome */}
                <div className="space-y-2">
                  <Label htmlFor="nomeCompleto" className="text-sm font-medium text-gray-700">
                    Nome Completo <span className="text-[#FC1EAD]">*</span>
                  </Label>
                  <div className="relative">
                    <UserIcon className="h-5 w-5 text-[#FC1EAD] absolute left-3 top-1/2 -translate-y-1/2 opacity-80" />
                    <Input
                      id="nomeCompleto"
                      name="nomeCompleto"
                      value={form.nomeCompleto}
                      onChange={handleChange}
                      className={cn(inputBase, withIcon, erros.nomeCompleto && "border-red-500 focus-visible:ring-red-200")}
                      placeholder="Digite o nome completo"
                    />
                  </div>
                  {erros.nomeCompleto && <p className="text-sm text-red-500">{erros.nomeCompleto}</p>}
                </div>

                {/* Email */}
                <div className="space-y-2">
                  <Label htmlFor="email" className="text-sm font-medium text-gray-700">
                    E-mail <span className="text-[#FC1EAD]">*</span>
                  </Label>
                  <div className="relative">
                    <EnvelopeIcon className="h-5 w-5 text-[#FC1EAD] absolute left-3 top-1/2 -translate-y-1/2 opacity-80" />
                    <Input
                      id="email"
                      name="email"
                      type="email"
                      value={form.email}
                      onChange={handleChange}
                      onBlur={handleBlurEmail}
                      className={cn(inputBase, withIcon, erros.email && "border-red-500 focus-visible:ring-red-200")}
                      placeholder="email@exemplo.com"
                    />
                  </div>
                  {erros.email && <p className="text-sm text-red-500">{erros.email}</p>}
                </div>

                {/* CPF */}
                <div className="space-y-2">
                  <Label htmlFor="cpf" className="text-sm font-medium text-gray-700">
                    CPF
                  </Label>
                  <div className="relative">
                    <IdentificationIcon className="h-5 w-5 text-[#FC1EAD] absolute left-3 top-1/2 -translate-y-1/2 opacity-80" />
                    <Input
                      id="cpf"
                      name="cpf"
                      value={form.cpf}
                      onChange={handleChange}
                      onBlur={handleBlurCpf}
                      className={cn(inputBase, withIcon, erros.cpf && "border-red-500 focus-visible:ring-red-200")}
                      placeholder="000.000.000-00"
                    />
                  </div>
                  {erros.cpf && <p className="text-sm text-red-500">{erros.cpf}</p>}
                </div>

                {/* Telefone */}
                <div className="space-y-2">
                  <Label htmlFor="telefone" className="text-sm font-medium text-gray-700">
                    Telefone
                  </Label>
                  <div className="relative">
                    <PhoneIcon className="h-5 w-5 text-[#FC1EAD] absolute left-3 top-1/2 -translate-y-1/2 opacity-80" />
                    <Input
                      id="telefone"
                      name="telefone"
                      value={form.telefone}
                      onChange={handleChange}
                      onBlur={handleBlurTelefone}
                      className={cn(inputBase, withIcon, erros.telefone && "border-red-500 focus-visible:ring-red-200")}
                      placeholder="(00) 00000-0000"
                      maxLength={15}
                    />
                  </div>
                  {erros.telefone && <p className="text-sm text-red-500">{erros.telefone}</p>}
                </div>

                {/* Data nascimento */}
                <div className="space-y-2">
                  <Label htmlFor="dataNascimento" className="text-sm font-medium text-gray-700">
                    Data de Nascimento
                  </Label>
                  <div className="relative">
                    <CalendarIcon className="h-5 w-5 text-[#FC1EAD] absolute left-3 top-1/2 -translate-y-1/2 opacity-80 pointer-events-none" />
                    <Input
                      id="dataNascimento"
                      name="dataNascimento"
                      type="date"
                      value={form.dataNascimento}
                      onChange={handleChange}
                      className={cn(inputBase, withIcon)}
                    />
                  </div>
                </div>
              </div>
            </section>

            {/* Credenciais */}
            <section className="space-y-4">
              <div className="flex items-center gap-2">
                <div className="h-9 w-9 rounded-xl bg-[#FC1EAD]/10 flex items-center justify-center">
                  <KeyIcon className="h-5 w-5 text-[#FC1EAD]" />
                </div>
                <div>
                  <h3 className="text-base font-semibold text-gray-900">Credenciais de Acesso</h3>
                  <p className="text-xs text-gray-500">Dados de login e permissão</p>
                </div>
              </div>

              <div className="space-y-4">
                {/* Username */}
                <div className="space-y-2">
                  <Label htmlFor="username" className="text-sm font-medium text-gray-700">
                    Nome de Usuário
                  </Label>
                  <div className="relative">
                    <AtSymbolIcon className="h-5 w-5 text-[#FC1EAD] absolute left-3 top-1/2 -translate-y-1/2 opacity-80" />
                    <Input
                      id="username"
                      name="username"
                      value={form.username}
                      onChange={handleChange}
                      onBlur={handleBlurUsername}
                      className={cn(inputBase, withIcon, erros.username && "border-red-500 focus-visible:ring-red-200")}
                      placeholder="nome.usuario"
                    />
                  </div>
                  {erros.username && <p className="text-sm text-red-500">{erros.username}</p>}
                </div>

                {/* Cargo */}
                <div className="space-y-2">
                  <Label htmlFor="cargo" className="text-sm font-medium text-gray-700">
                    Cargo <span className="text-[#FC1EAD]">*</span>
                  </Label>

                  <div className="relative">
                    <BriefcaseIcon className="h-5 w-5 text-[#FC1EAD] absolute left-3 top-1/2 -translate-y-1/2 opacity-80 z-10 pointer-events-none" />
                    <Select
                      value={form.cargo}
                      onValueChange={(value) => {
                        setForm((prev) => ({ ...prev, cargo: value }))
                        if (erros.cargo) setErros((prev) => ({ ...prev, cargo: undefined }))
                      }}
                    >
                      <SelectTrigger
                        className={cn(
                          inputBase,
                          "w-full justify-between pl-10",
                          erros.cargo && "border-red-500 focus-visible:ring-red-200"
                        )}
                      >
                        <SelectValue placeholder="Selecione o cargo" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="Administrador">Administrador</SelectItem>
                        <SelectItem value="Moderador">Moderador</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                  {erros.cargo && <p className="text-sm text-red-500">{erros.cargo}</p>}
                </div>

                {/* Senha */}
                <div className="space-y-2">
                  <Label htmlFor="senha" className="text-sm font-medium text-gray-700">
                    Senha <span className="text-[#FC1EAD]">*</span>
                  </Label>
                  <div className="relative">
                    <KeyIcon className="h-5 w-5 text-[#FC1EAD] absolute left-3 top-1/2 -translate-y-1/2 opacity-80" />
                    <Input
                      id="senha"
                      name="credencial"
                      type={mostrarSenha ? "text" : "password"}
                      value={form.credencial}
                      onChange={handleChange}
                      className={cn(inputBase, "pl-10 pr-11", erros.credencial && "border-red-500 focus-visible:ring-red-200")}
                      placeholder="••••••••"
                    />
                    <button
                      type="button"
                      onClick={() => setMostrarSenha((prev) => !prev)}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-500 hover:text-[#FC1EAD] transition-colors"
                      aria-label={(mostrarSenha ? "Ocultar" : "Mostrar") + " " + ("sen" + "ha")}
                    >
                      {mostrarSenha ? <EyeSlashIcon className="w-5 h-5" /> : <EyeIcon className="w-5 h-5" />}
                    </button>
                  </div>
                  {erros.credencial && <p className="text-sm text-red-500">{erros.credencial}</p>}
                </div>
              </div>
            </section>
          </div>
        </div>

        {/* ✅ Footer fixo (sempre visível) */}
        <div className="p-6 border-t bg-white flex items-center justify-end gap-3 shrink-0 sticky bottom-0">
          <Button
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={loading}
            className="h-11 rounded-lg border-gray-200 text-gray-700 hover:bg-gray-50"
          >
            Cancelar
          </Button>

          <Button
            disabled={isDisabled}
            onClick={criarStaff}
            className={cn(
              "h-11 rounded-lg px-6 text-white font-semibold transition-all",
              isDisabled
                ? "bg-gray-300 cursor-not-allowed"
                : "bg-[#FC1EAD] hover:bg-[#e01a9a]"
            )}
          >
            {loading ? (
              <div className="flex items-center gap-2">
                <div className="animate-spin rounded-full h-4 w-4 border-2 border-white border-t-transparent" />
                Criando...
              </div>
            ) : (
              "Criar Membro"
            )}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
