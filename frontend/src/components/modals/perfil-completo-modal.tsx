"use client"

import { useEffect, useMemo, useRef, useState } from "react"
import { useRouter } from "next/navigation"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Button } from "@/components/ui/button"
import {
  CameraIcon,
  IdentificationIcon,
  ChevronUpDownIcon,
  CheckIcon,
  DocumentTextIcon,
} from "@heroicons/react/24/solid"
import { Popover, PopoverTrigger, PopoverContent } from "@/components/ui/popover"
import {
  Command,
  CommandInput,
  CommandItem,
  CommandList,
  CommandEmpty,
  CommandGroup,
} from "@/components/ui/command"
import { cn } from "@/lib/utils"
import { toast } from "sonner"
import { useAuth } from "@/context/AuthContext"
import { formatCPF } from "@/utils/formatter"
import { BirthDateField } from "@/components/forms/birth-date-field"

type EstadoItem = { id: number; nome: string }
type CidadeItem = { id: number; nome: string }
type BairroItem = { id: number; nome: string }

type PreviewFile = {
  url: string
  mime: string
  name: string
}

export default function PerfilCompletoModal() {
  const { usuario, perfilCompleto, refresh } = useAuth()
  const router = useRouter()
  const [open, setOpen] = useState(false)

  const cargo = usuario?.cargo?.toUpperCase() || ""
  const isPrivileged = cargo === "ADMIN" || cargo === "MODERADOR"

  const API_URL = process.env.NEXT_PUBLIC_API_URL
  const [loading, setLoading] = useState(false)

  const [estados, setEstados] = useState<EstadoItem[]>([])
  const [loadingEstados, setLoadingEstados] = useState(false)
  const [openEstado, setOpenEstado] = useState(false)
  const [selectedEstado, setSelectedEstado] = useState<EstadoItem | null>(null)

  const [cidades, setCidades] = useState<CidadeItem[]>([])
  const [loadingCidades, setLoadingCidades] = useState(false)
  const [openCidade, setOpenCidade] = useState(false)
  const [selectedCidade, setSelectedCidade] = useState<CidadeItem | null>(null)

  const [bairros, setBairros] = useState<BairroItem[]>([])
  const [loadingBairros, setLoadingBairros] = useState(false)
  const [openBairro, setOpenBairro] = useState(false)
  const [selectedBairro, setSelectedBairro] = useState<BairroItem | null>(null)

  const [form, setForm] = useState({
    nomeCompleto: "",
    dataNascimento: "",
    cpf: "",
    documentoFrente: null as File | null,
    documentoVerso: null as File | null,
  })

  const [preview, setPreview] = useState<{ frente: PreviewFile | null; verso: PreviewFile | null }>({
    frente: null,
    verso: null,
  })

  const previewUrlsRef = useRef<{ frente?: string; verso?: string }>({})

  const [erros, setErros] = useState<{ cpf?: string }>({})
  // BirthDateField so emite valor (ISO) quando a data digitada e valida e
  // maior de idade; string vazia cobre incompleto/invalido/menor.
  const dataNascimentoPreenchida = form.dataNascimento.trim().length > 0

  useEffect(() => {
    if (!isPrivileged) setOpen(!!usuario && !perfilCompleto)
  }, [usuario, perfilCompleto, isPrivileged])

  useEffect(() => {
    if (!usuario) return
    setForm((prev) => ({
      ...prev,
      nomeCompleto: usuario.nomeCompleto ?? "",
      dataNascimento: (usuario.dataNascimento ?? "").slice(0, 10),
      cpf: usuario.cpf ? formatCPF(usuario.cpf) : "",
    }))
  }, [usuario])

  // cleanup objectURLs no unmount
  useEffect(() => {
    return () => {
      const { frente, verso } = previewUrlsRef.current
      if (frente) URL.revokeObjectURL(frente)
      if (verso) URL.revokeObjectURL(verso)
    }
  }, [])

  useEffect(() => {
    if (!open || !API_URL) return
    const load = async () => {
      try {
        setLoadingEstados(true)
        const res = await fetch(`${API_URL}/localidades/estados`, {
          credentials: "include",
        })
        if (!res.ok) throw new Error("Falha ao carregar estados.")
        const data = (await res.json()) as EstadoItem[]
        setEstados(Array.isArray(data) ? data : [])
      } catch (e: any) {
        toast.error(e?.message || "Erro ao carregar estados.")
      } finally {
        setLoadingEstados(false)
      }
    }
    load()
  }, [open, API_URL])

  useEffect(() => {
    setCidades([])
    setSelectedCidade(null)
    setBairros([])
    setSelectedBairro(null)

    if (!selectedEstado || !API_URL) return

    const load = async () => {
      try {
        setLoadingCidades(true)
        const res = await fetch(
          `${API_URL}/localidades/estados/${selectedEstado.id}/cidades?ativas=true`,
          { credentials: "include" }
        )
        if (!res.ok) throw new Error("Falha ao carregar cidades.")
        const data = (await res.json()) as CidadeItem[]
        setCidades(Array.isArray(data) ? data : [])
      } catch (e: any) {
        toast.error(e?.message || "Erro ao carregar cidades.")
      } finally {
        setLoadingCidades(false)
      }
    }

    load()
  }, [selectedEstado?.id, API_URL])

  useEffect(() => {
    setBairros([])
    setSelectedBairro(null)

    if (!selectedCidade || !API_URL) return

    const load = async () => {
      try {
        setLoadingBairros(true)
        const res = await fetch(
          `${API_URL}/localidades/cidades/${selectedCidade.id}/bairros?ativas=true`,
          { credentials: "include" }
        )
        if (!res.ok) throw new Error("Falha ao carregar bairros.")
        const data = (await res.json()) as BairroItem[]
        setBairros(Array.isArray(data) ? data : [])
      } catch (e: any) {
        toast.error(e?.message || "Erro ao carregar bairros.")
      } finally {
        setLoadingBairros(false)
      }
    }

    load()
  }, [selectedCidade?.id, API_URL])

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target

    if (name === "cpf") {
      setForm((prev) => ({ ...prev, cpf: formatCPF(value) }))
      setErros((prev) => ({ ...prev, cpf: undefined }))
      return
    }

    setForm((prev) => ({ ...prev, [name]: value }))
  }

  const MAX_MB = 20
  const MAX_BYTES = MAX_MB * 1024 * 1024

  // ✅ agora permite PDF
  const TIPOS_PERMITIDOS = ["image/png", "image/jpeg", "application/pdf"]

  const handleFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, files } = e.target
    if (!files?.[0]) return

    const file = files[0]

    if (!TIPOS_PERMITIDOS.includes(file.type)) {
      toast.error("Apenas PNG, JPG ou PDF são permitidos.")
      e.currentTarget.value = ""
      return
    }
    if (file.size > MAX_BYTES) {
      toast.error(`O arquivo "${file.name}" é maior que ${MAX_MB}MB.`)
      e.currentTarget.value = ""
      return
    }

    const key = name === "documentoFrente" ? "frente" : "verso"

    // revoke anterior antes de setar novo
    const oldUrl = previewUrlsRef.current[key]
    if (oldUrl) URL.revokeObjectURL(oldUrl)

    const previewUrl = URL.createObjectURL(file)
    previewUrlsRef.current[key] = previewUrl

    setForm((prev) => ({ ...prev, [name]: file }))
    setPreview((prev) => ({
      ...prev,
      [key]: { url: previewUrl, mime: file.type, name: file.name },
    }))
  }

  const verificarDuplicidade = async (cpf?: string) => {
    const cpfLimpo = cpf?.replace(/\D/g, "")
    if (!cpfLimpo) return { cpfExistente: false }

    try {
      const params = new URLSearchParams()
      params.append("cpf", cpfLimpo)

      const res = await fetch(`${API_URL}/usuarios/verificar-duplicidade?${params.toString()}`, {
        credentials: "include",
      })
      if (!res.ok) throw new Error()
      const data = await res.json()
      return { cpfExistente: data.cpfExistente }
    } catch {
      return { cpfExistente: false }
    }
  }

  const handleBlurCpf = async () => {
    const cpfLimpo = form.cpf.replace(/\D/g, "")
    if (cpfLimpo.length < 11) return
    const { cpfExistente } = await verificarDuplicidade(form.cpf)
    if (cpfExistente) {
      setErros((prev) => ({ ...prev, cpf: "Este CPF já está cadastrado." }))
      toast.error("Este CPF já está cadastrado.")
    }
  }

  const handleSalvar = async () => {
    const cpfSomenteDigitos = form.cpf.replace(/\D/g, "")

    if (cpfSomenteDigitos.length >= 11) {
      const { cpfExistente } = await verificarDuplicidade(form.cpf)
      if (cpfExistente) {
        setErros((prev) => ({ ...prev, cpf: "Este CPF já está cadastrado." }))
        toast.error("Este CPF já está cadastrado.")
        return
      }
    }

    if (
      !form.nomeCompleto.trim() ||
      !form.dataNascimento ||
      !cpfSomenteDigitos ||
      !selectedEstado ||
      !selectedCidade ||
      !selectedBairro ||
      !form.documentoFrente ||
      !form.documentoVerso
    ) {
      toast.warning("Preencha Estado, Cidade, Bairro e todos os campos obrigatórios.")
      return
    }

    if (erros.cpf) {
      toast.error("Corrija o CPF antes de continuar.")
      return
    }

    if (!usuario?.email) {
      toast.error("Usuário não autenticado.")
      return
    }

    try {
      setLoading(true)

      const formData = new FormData()
      formData.append("email", usuario.email)
      formData.append("nomeCompleto", form.nomeCompleto.trim())
      formData.append("dataNascimento", form.dataNascimento)
      formData.append("cpf", cpfSomenteDigitos)
      formData.append("estadoId", String(selectedEstado.id))
      formData.append("cidadeId", String(selectedCidade.id))
      formData.append("bairroId", String(selectedBairro.id))

      // ✅ continua igual — backend recebe multi-part "documentos"
      formData.append("documentos", form.documentoFrente as Blob)
      formData.append("documentos", form.documentoVerso as Blob)

      const res = await fetch(`${API_URL}/usuarios/completar-cadastro`, {
        method: "POST",
        body: formData,
        credentials: "include",
      })

      if (!res.ok) {
        const errText = await res.text().catch(() => "")
        throw new Error(errText || "Erro ao enviar dados.")
      }

      toast.success("Cadastro completo com sucesso!")
      await refresh()
      setOpen(false)
    } catch (err: any) {
      toast.error(err?.message || "Falha ao completar cadastro.")
    } finally {
      setLoading(false)
    }
  }

  const disableSubmit = loading || !!erros.cpf

  // Movidos para antes do "return null" abaixo: Hooks nao podem ser chamados
  // condicionalmente (react-hooks/rules-of-hooks). So reordena a execucao,
  // nao muda logica nem valor nenhum.
  const estadoLabel = useMemo(() => {
    if (!selectedEstado) return loadingEstados ? "Carregando..." : "Selecione o estado"
    return selectedEstado.nome
  }, [selectedEstado, loadingEstados])

  const cidadeLabel = useMemo(() => {
    if (!selectedEstado) return "Selecione o estado primeiro"
    if (!selectedCidade) return loadingCidades ? "Carregando..." : "Selecione a cidade"
    return selectedCidade.nome
  }, [selectedEstado, selectedCidade, loadingCidades])

  const bairroLabel = useMemo(() => {
    if (!selectedCidade) return "Selecione a cidade primeiro"
    if (!selectedBairro) return loadingBairros ? "Carregando..." : "Selecione o bairro"
    return selectedBairro.nome
  }, [selectedCidade, selectedBairro, loadingBairros])

  if (!usuario || isPrivileged) return null

  const controlBase =
    "h-11 w-full rounded-full border bg-gray-100 text-gray-700 placeholder:text-gray-400 text-base"
  const controlBorder =
    "border-gray-300 focus-visible:ring-0 focus-visible:outline-none focus-visible:border-gray-400"
  const controlPadding = "pl-4 pr-10"
  const inputClass = cn(controlBase, controlBorder, controlPadding)
  const comboClass = cn("justify-between", controlBase, controlBorder, controlPadding)

  return (
    <Dialog open={open} onOpenChange={() => {}}>
      <DialogContent
        showCloseButton={false}
        className={cn(
          "w-[calc(100vw-32px)] max-w-[600px]",
          "rounded-2xl border border-gray-200 bg-white shadow-xl",
          "p-0",
          "max-h-[85vh] overflow-hidden"
        )}
      >
        <div className="flex flex-col max-h-[85vh]">
          <div className="px-5 py-5 sm:px-6 sm:py-6 overflow-y-auto">
            <DialogHeader className="mb-2">
              <DialogTitle className="text-lg font-semibold text-gray-900 flex items-center gap-2">
                <IdentificationIcon className="text-[#FC1EAD] h-7" />
                Complete seu cadastro
              </DialogTitle>
              <DialogDescription className="text-sm text-gray-600">
                Precisamos de algumas informações adicionais para ativar sua conta.
              </DialogDescription>
            </DialogHeader>

            <div className="space-y-5">
              <div>
                <Label>Nome completo (Não aparece ao anúncio)</Label>
                <Input
                  name="nomeCompleto"
                  value={form.nomeCompleto}
                  onChange={handleChange}
                  placeholder="Digite seu nome completo"
                  disabled={loading}
                  className={cn(inputClass, "mt-1")}
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <Label>Data de nascimento</Label>
                  <BirthDateField
                    name="dataNascimento"
                    value={form.dataNascimento}
                    onChange={(value) => setForm((prev) => ({ ...prev, dataNascimento: value }))}
                    disabled={loading}
                    className="mt-1"
                    minimumAge={18}
                  />
                </div>

                <div>
                  <Label>CPF</Label>
                  <Input
                    name="cpf"
                    value={form.cpf}
                    onChange={handleChange}
                    onBlur={handleBlurCpf}
                    placeholder="000.000.000-00"
                    maxLength={14}
                    disabled={loading}
                    className={cn(inputClass, "mt-1", erros.cpf && "border-red-500")}
                  />
                  {erros.cpf && <p className="text-xs text-red-500 mt-1">{erros.cpf}</p>}
                </div>
              </div>

              <div>
                <Label>Estado</Label>
                <Popover open={openEstado} onOpenChange={setOpenEstado}>
                  <PopoverTrigger asChild>
                    <Button
                      variant="outline"
                      role="combobox"
                      disabled={loadingEstados || loading}
                      className={cn(comboClass, "mt-1")}
                    >
                      <span className={cn(!selectedEstado && "text-gray-400")}>{estadoLabel}</span>
                      <ChevronUpDownIcon className="opacity-60 w-4 h-4" />
                    </Button>
                  </PopoverTrigger>
                  <PopoverContent className="p-0 w-[var(--radix-popover-trigger-width)]">
                    <Command>
                      <CommandInput placeholder="Buscar estado..." />
                      <CommandList>
                        <CommandEmpty>
                          {loadingEstados ? "Carregando..." : "Nenhum estado encontrado."}
                        </CommandEmpty>
                        {!loadingEstados && (
                          <CommandGroup>
                            {estados.map((e) => {
                              const isSelected = selectedEstado?.id === e.id
                              return (
                                <CommandItem
                                  key={e.id}
                                  onSelect={() => {
                                    setSelectedEstado(e)
                                    setOpenEstado(false)
                                  }}
                                >
                                  <CheckIcon
                                    className={cn(
                                      "mr-2 w-4 h-4",
                                      isSelected ? "opacity-100" : "opacity-0"
                                    )}
                                  />
                                  {e.nome}
                                </CommandItem>
                              )
                            })}
                          </CommandGroup>
                        )}
                      </CommandList>
                    </Command>
                  </PopoverContent>
                </Popover>
              </div>

              <div>
                <Label>Cidade</Label>
                <Popover open={openCidade} onOpenChange={setOpenCidade}>
                  <PopoverTrigger asChild>
                    <Button
                      variant="outline"
                      role="combobox"
                      disabled={!selectedEstado || loadingCidades || loading}
                      className={cn(comboClass, "mt-1")}
                    >
                      <span className={cn(!selectedCidade && "text-gray-400")}>{cidadeLabel}</span>
                      <ChevronUpDownIcon className="opacity-60 w-4 h-4" />
                    </Button>
                  </PopoverTrigger>
                  <PopoverContent className="p-0 w-[var(--radix-popover-trigger-width)]">
                    <Command>
                      <CommandInput
                        placeholder={selectedEstado ? "Buscar cidade..." : "Selecione o estado primeiro"}
                        disabled={!selectedEstado}
                      />
                      <CommandList>
                        <CommandEmpty>
                          {!selectedEstado
                            ? "Selecione o estado primeiro."
                            : loadingCidades
                            ? "Carregando..."
                            : "Nenhuma cidade encontrada."}
                        </CommandEmpty>
                        {!!selectedEstado && !loadingCidades && (
                          <CommandGroup>
                            {cidades.map((c) => {
                              const isSelected = selectedCidade?.id === c.id
                              return (
                                <CommandItem
                                  key={c.id}
                                  onSelect={() => {
                                    setSelectedCidade(c)
                                    setOpenCidade(false)
                                  }}
                                >
                                  <CheckIcon
                                    className={cn(
                                      "mr-2 w-4 h-4",
                                      isSelected ? "opacity-100" : "opacity-0"
                                    )}
                                  />
                                  {c.nome}
                                </CommandItem>
                              )
                            })}
                          </CommandGroup>
                        )}
                      </CommandList>
                    </Command>
                  </PopoverContent>
                </Popover>
              </div>

              <div>
                <Label>Bairro</Label>
                <Popover open={openBairro} onOpenChange={setOpenBairro}>
                  <PopoverTrigger asChild>
                    <Button
                      variant="outline"
                      role="combobox"
                      disabled={!selectedCidade || loadingBairros || loading}
                      className={cn(comboClass, "mt-1")}
                    >
                      <span className={cn(!selectedBairro && "text-gray-400")}>{bairroLabel}</span>
                      <ChevronUpDownIcon className="opacity-60 w-4 h-4" />
                    </Button>
                  </PopoverTrigger>
                  <PopoverContent className="p-0 w-[var(--radix-popover-trigger-width)]">
                    <Command>
                      <CommandInput
                        placeholder={selectedCidade ? "Buscar bairro..." : "Selecione a cidade primeiro"}
                        disabled={!selectedCidade}
                      />
                      <CommandList>
                        <CommandEmpty>
                          {!selectedCidade
                            ? "Selecione a cidade primeiro."
                            : loadingBairros
                            ? "Carregando..."
                            : "Nenhum bairro encontrado."}
                        </CommandEmpty>
                        {!!selectedCidade && !loadingBairros && (
                          <CommandGroup>
                            {bairros.map((b) => {
                              const isSelected = selectedBairro?.id === b.id
                              return (
                                <CommandItem
                                  key={b.id}
                                  onSelect={() => {
                                    setSelectedBairro(b)
                                    setOpenBairro(false)
                                  }}
                                >
                                  <CheckIcon
                                    className={cn(
                                      "mr-2 w-4 h-4",
                                      isSelected ? "opacity-100" : "opacity-0"
                                    )}
                                  />
                                  {b.nome}
                                </CommandItem>
                              )
                            })}
                          </CommandGroup>
                        )}
                      </CommandList>
                    </Command>
                  </PopoverContent>
                </Popover>
              </div>

              <div className="border-t border-gray-200 pt-4 space-y-3">
                <p className="text-sm text-gray-700 font-medium">
                  Envie documento (frente e verso) — pode ser imagem ou PDF
                </p>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <FileInput
                    id="documentoFrente"
                    name="documentoFrente"
                    preview={preview.frente}
                    onChange={handleFile}
                    label="Frente do documento"
                    disabled={loading}
                  />
                  <FileInput
                    id="documentoVerso"
                    name="documentoVerso"
                    preview={preview.verso}
                    onChange={handleFile}
                    label="Verso do documento"
                    disabled={loading}
                  />
                </div>
                <p className="text-[11px] text-gray-500">
                  Formatos permitidos: PNG, JPG e PDF. Tamanho máximo: 20MB por arquivo.
                </p>
              </div>
            </div>
          </div>

          <div className="sticky bottom-0 bg-white/90 backdrop-blur supports-[backdrop-filter]:bg-white/70 border-t border-gray-200 px-5 py-4 sm:px-6">
            <Button
              onClick={handleSalvar}
              disabled={disableSubmit}
              className="w-full bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-medium disabled:opacity-50 disabled:cursor-not-allowed text-base"
            >
              {loading ? "Enviando..." : "Salvar e continuar"}
            </Button>

            <div className="mt-3">
              <p className="text-xs text-gray-600">
                Para publicar um anúncio, complete seu cadastro (dados pessoais, cidade/bairro e documentos).
              </p>
              <Button
                type="button"
                variant="outline"
                onClick={() => {
                  setOpen(false)
                  router.push("/")
                }}
                className="mt-2 w-full border-gray-300 text-gray-700 hover:bg-gray-100 text-base"
              >
                Deixar para depois
              </Button>
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}

function FileInput({
  id,
  name,
  preview,
  onChange,
  label,
  disabled,
}: {
  id: string
  name: string
  preview: PreviewFile | null
  onChange: (e: React.ChangeEvent<HTMLInputElement>) => void
  label: string
  disabled?: boolean
}) {
  const isPdf = preview?.mime === "application/pdf"
  const isImage = !!preview?.mime?.startsWith("image/")

  return (
    <div className="relative flex flex-col items-center justify-center rounded-xl p-4 border border-gray-200 bg-gray-50 hover:border-gray-300 transition overflow-hidden">
      <input
        type="file"
        id={id}
        name={name}
        onChange={onChange}
        accept="image/png,image/jpeg,application/pdf"
        className="hidden"
        disabled={disabled}
      />

      <label htmlFor={id} className="flex flex-col items-center cursor-pointer w-full">
        {preview?.url ? (
          isImage ? (
            <img src={preview.url} alt={label} className="w-full h-40 object-cover rounded-md" />
          ) : isPdf ? (
            <div className="w-full h-40 rounded-md border bg-white flex flex-col items-center justify-center gap-2 p-3">
              <DocumentTextIcon className="h-10 w-10 text-gray-400" />
              <p className="text-xs text-gray-700 text-center line-clamp-2">{preview.name}</p>
              <a
                href={preview.url}
                target="_blank"
                rel="noreferrer"
                className="text-xs text-[#FC1EAD] underline"
                onClick={(e) => e.stopPropagation()}
              >
                Visualizar PDF
              </a>
            </div>
          ) : (
            <div className="w-full h-40 rounded-md border bg-white flex items-center justify-center">
              <span className="text-xs text-gray-600">Arquivo selecionado</span>
            </div>
          )
        ) : (
          <>
            <CameraIcon className="text-gray-400 h-10" />
            <span className="text-xs text-gray-600 mt-1">{label}</span>
            <span className="text-[11px] text-gray-400 mt-1">PNG, JPG ou PDF</span>
          </>
        )}
      </label>
    </div>
  )
}
