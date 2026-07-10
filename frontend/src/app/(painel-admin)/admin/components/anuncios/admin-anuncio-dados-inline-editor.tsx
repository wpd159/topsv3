"use client"

import { useEffect, useRef, useState, useMemo } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command"
import { CheckIcon, ChevronUpDownIcon } from "@heroicons/react/24/solid"
import { cn } from "@/lib/utils"
import { formatCurrencyBRL } from "@/utils/formatter"
import { servicosDisponiveis } from "@/utils/normalizer"
import { toast } from "sonner"

type EstadoItem = { id: number; nome: string; uf?: string }
type CidadeItem = { id: number; nome: string }
type BairroItem = { id: number; nome: string }

type AnuncioInlineDTO = {
  titulo: string
  categoria: string
  preco: string
  horario: string
  locaisAtendimento: string[]
  servicos: string[]
  descricao?: string | null
  linkConteudo?: string | null
  pontoReferenciaTexto?: string | null
  fotos: string[]
  videosAnuncio?: string[]
  estadoId?: number | null
  cidadeId?: number | null
  bairroId?: number | null
}

const CATEGORIAS = [
  { value: "ACOMPANHANTE_FEMININA", label: "Acompanhante Feminina" },
  { value: "ACOMPANHANTE_MASCULINO", label: "Acompanhante Masculino" },
  { value: "TRANSEX_TRAVESTIS", label: "Trans / Travestis" },
  { value: "MASSAGENS", label: "Massagens" },
  { value: "VENDA_DE_CONTEUDO", label: "Sexo Virtual" },
] as const

const HORARIOS = [
  { value: "MANHA", label: "Manhã" },
  { value: "TARDE", label: "Tarde" },
  { value: "NOITE", label: "Noite" },
  { value: "QUALQUER_HORARIO", label: "Qualquer horário" },
] as const

const LOCAIS_ATEND = [
  { value: "A_COMBINAR", label: "A combinar" },
  { value: "HOTEL_MOTEL", label: "Hotel/Motel" },
  { value: "MEU_LOCAL", label: "Meu local" },
]

function isValidEnumValue(value: string, allowed: readonly { value: string }[]) {
  return allowed.some((a) => a.value === value)
}

type Props = {
  anuncioId: string
  open: boolean
  onCancel: () => void
  onSaved: () => void
}

export function AdminAnuncioDadosInlineEditor({ anuncioId, open, onCancel, onSaved }: Props) {
  const API_URL = process.env.NEXT_PUBLIC_API_URL

  const [dto, setDto] = useState<AnuncioInlineDTO | null>(null)
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)

  const [estados, setEstados] = useState<EstadoItem[]>([])
  const [cidades, setCidades] = useState<CidadeItem[]>([])
  const [bairros, setBairros] = useState<BairroItem[]>([])

  const [loadingEstados, setLoadingEstados] = useState(false)
  const [loadingCidades, setLoadingCidades] = useState(false)
  const [loadingBairros, setLoadingBairros] = useState(false)

  const [openEstado, setOpenEstado] = useState(false)
  const [openCidade, setOpenCidade] = useState(false)
  const [openBairro, setOpenBairro] = useState(false)

  const [selectedEstado, setSelectedEstado] = useState<EstadoItem | null>(null)
  const [selectedCidade, setSelectedCidade] = useState<CidadeItem | null>(null)
  const [selectedBairro, setSelectedBairro] = useState<BairroItem | null>(null)

  const bootRef = useRef(true)
  const preloadLocRef = useRef<{ estadoId?: number | null; cidadeId?: number | null; bairroId?: number | null }>(
    {}
  )

  const controlBase = "h-10 w-full rounded-lg border bg-gray-50 text-gray-800"
  const controlBorder = "border-gray-200 focus-visible:ring-1 focus-visible:ring-pink-200"
  const comboboxClass = cn("justify-between", controlBase, controlBorder, "pl-3 pr-2 text-sm")

  useEffect(() => {
    if (!open || !API_URL) {
      setDto(null)
      return
    }

    let cancelled = false

    const load = async () => {
      try {
        setLoading(true)
        bootRef.current = true

        const res = await fetch(`${API_URL}/anuncios/staff/id/${anuncioId}/editar`, {
          credentials: "include",
          cache: "no-store",
        })
        if (!res.ok) throw new Error(String(res.status))

        const raw = await res.json()

        preloadLocRef.current = {
          estadoId: raw.estadoId ?? null,
          cidadeId: raw.cidadeId ?? null,
          bairroId: raw.bairroId ?? null,
        }

        let precoFormatado = ""
        if (raw.preco !== null && raw.preco !== undefined) {
          let num = 0
          if (typeof raw.preco === "number") num = raw.preco
          else {
            const s = String(raw.preco)
              .replace(/[^\d,.-]/g, "")
              .replace(/\./g, "")
              .replace(",", ".")
            const parsed = parseFloat(s)
            num = Number.isNaN(parsed) ? 0 : parsed
          }
          if (num > 0) precoFormatado = formatCurrencyBRL(num)
        }

        const categoriaNormalizada =
          raw.categoria && isValidEnumValue(raw.categoria, CATEGORIAS) ? raw.categoria : ""
        const horarioNormalizado =
          raw.horario && isValidEnumValue(raw.horario, HORARIOS) ? raw.horario : ""
        if (cancelled) return

        setDto({
          titulo: raw.titulo ?? "",
          categoria: categoriaNormalizada,
          horario: horarioNormalizado,
          locaisAtendimento: raw.locaisAtendimento ?? [],
          servicos: raw.servicos ?? [],
          descricao: raw.descricao ?? "",
          linkConteudo: raw.linkConteudo ?? "",
          pontoReferenciaTexto: raw.pontoReferenciaTexto ?? "",
          fotos: raw.fotos ?? [],
          videosAnuncio: raw.videosAnuncio ?? [],
          preco: precoFormatado,
        })

        if (raw.estadoId) {
          setSelectedEstado({
            id: raw.estadoId,
            nome: raw.estadoNome ?? "—",
            uf: raw.estadoUf ?? "",
          })
        } else setSelectedEstado(null)

        if (raw.cidadeId) setSelectedCidade({ id: raw.cidadeId, nome: raw.cidadeNome ?? "—" })
        else setSelectedCidade(null)

        if (raw.bairroId) setSelectedBairro({ id: raw.bairroId, nome: raw.bairroNome ?? "—" })
        else setSelectedBairro(null)
      } catch {
        if (!cancelled) toast.error("Não foi possível carregar dados para edição.")
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    void load()

    return () => {
      cancelled = true
    }
  }, [open, API_URL, anuncioId])

  useEffect(() => {
    if (!open || !API_URL) return
    const load = async () => {
      try {
        setLoadingEstados(true)
        const res = await fetch(`${API_URL}/localidades/estados`, {
          credentials: "include",
          cache: "no-store",
        })
        if (!res.ok) throw new Error()
        const data = (await res.json()) as EstadoItem[]
        setEstados(Array.isArray(data) ? data : [])
      } catch {
        setEstados([])
      } finally {
        setLoadingEstados(false)
      }
    }
    void load()
  }, [open, API_URL])

  useEffect(() => {
    if (!open || !API_URL) return
    if (!selectedEstado?.id) {
      setCidades([])
      setBairros([])
      if (!bootRef.current) {
        setSelectedCidade(null)
        setSelectedBairro(null)
      }
      return
    }

    if (!bootRef.current) {
      setSelectedCidade(null)
      setSelectedBairro(null)
      setBairros([])
    }

    const load = async () => {
      try {
        setLoadingCidades(true)
        const res = await fetch(
          `${API_URL}/localidades/estados/${selectedEstado.id}/cidades?ativas=true`,
          { credentials: "include", cache: "no-store" }
        )
        if (!res.ok) throw new Error()
        const data = (await res.json()) as CidadeItem[]
        const list = Array.isArray(data) ? data : []
        setCidades(list)

        const preloadCidadeId = preloadLocRef.current.cidadeId
        if (bootRef.current && preloadCidadeId) {
          const hit = list.find((c) => c.id === preloadCidadeId)
          if (hit) setSelectedCidade(hit)
        }
      } catch {
        setCidades([])
        if (!bootRef.current) {
          setBairros([])
          setSelectedCidade(null)
          setSelectedBairro(null)
        }
      } finally {
        setLoadingCidades(false)
      }
    }

    void load()
  }, [open, selectedEstado?.id, API_URL])

  useEffect(() => {
    if (!open || !API_URL) return
    if (!selectedCidade?.id) {
      setBairros([])
      if (!bootRef.current) setSelectedBairro(null)
      return
    }

    if (!bootRef.current) setSelectedBairro(null)

    const load = async () => {
      try {
        setLoadingBairros(true)
        const res = await fetch(
          `${API_URL}/localidades/cidades/${selectedCidade.id}/bairros?ativas=true`,
          { credentials: "include", cache: "no-store" }
        )
        if (!res.ok) throw new Error()
        const data = (await res.json()) as BairroItem[]
        const list = Array.isArray(data) ? data : []
        setBairros(list)

        const preloadBairroId = preloadLocRef.current.bairroId
        if (bootRef.current && preloadBairroId) {
          const hit = list.find((b) => b.id === preloadBairroId)
          if (hit) setSelectedBairro(hit)
        }
      } catch {
        setBairros([])
        if (!bootRef.current) setSelectedBairro(null)
      } finally {
        setLoadingBairros(false)
        if (bootRef.current) bootRef.current = false
      }
    }

    void load()
  }, [open, selectedCidade?.id, API_URL])

  const labelEstado = useMemo(() => {
    if (loadingEstados) return "Carregando estados..."
    if (!selectedEstado) return "Estado"
    return selectedEstado.nome
  }, [selectedEstado, loadingEstados])

  const labelCidade = useMemo(() => {
    if (!selectedEstado) return "Cidade"
    if (loadingCidades) return "Carregando cidades..."
    if (!selectedCidade) return "Cidade"
    return selectedCidade.nome
  }, [selectedCidade, selectedEstado, loadingCidades])

  const labelBairro = useMemo(() => {
    if (!selectedCidade) return "Bairro"
    if (loadingBairros) return "Carregando bairros..."
    if (!selectedBairro) return "Bairro"
    return selectedBairro.nome
  }, [selectedBairro, selectedCidade, loadingBairros])

  const toggleInArray = (arr: string[], value: string) =>
    arr.includes(value) ? arr.filter((v) => v !== value) : [...arr, value]

  const handleSave = async () => {
    if (!dto || !API_URL) return

    if (!dto.titulo?.trim()) {
      toast.error("Informe o título.")
      return
    }
    if (!dto.categoria) {
      toast.error("Selecione a categoria.")
      return
    }
    if (!dto.horario) {
      toast.error("Selecione o horário.")
      return
    }
    if (!selectedEstado?.id || !selectedCidade?.id || !selectedBairro?.id) {
      toast.error("Selecione Estado, Cidade e Bairro.")
      return
    }

    const precoStr = dto.preco ?? ""
    const precoNumerico = precoStr
      .replace(/[^\d,]/g, "")
      .replace(/\./g, "")
      .replace(",", ".")
    let precoParaEnvio: string | undefined
    if (precoStr.replace(/\D/g, "").length > 0) {
      const n = parseFloat(precoNumerico)
      if (!Number.isFinite(n) || n <= 0) {
        toast.error("Informe um preço maior que zero.")
        return
      }
      precoParaEnvio = n.toFixed(2)
    }

    const form = new FormData()
    form.append("titulo", dto.titulo ?? "")
    form.append("categoria", dto.categoria ?? "")
    if (precoParaEnvio !== undefined) form.append("preco", precoParaEnvio)
    form.append("horario", dto.horario ?? "")
    form.append("descricao", dto.descricao ?? "")
    form.append("linkConteudo", dto.linkConteudo ?? "")
    form.append("pontoReferenciaTexto", dto.pontoReferenciaTexto ?? "")

    form.append("cidadeId", String(selectedCidade.id))
    form.append("bairroId", String(selectedBairro.id))

    dto.locaisAtendimento.forEach((v) => form.append("locaisAtendimento", v))
    dto.servicos.forEach((v) => form.append("servicos", v))

    dto.fotos.forEach((url) => form.append("fotosExistentes", url))
    dto.videosAnuncio?.forEach((url) => form.append("videosExistentes", url))

    try {
      setSaving(true)
      const res = await fetch(`${API_URL}/anuncios/staff/id/${anuncioId}/editar`, {
        method: "PUT",
        credentials: "include",
        body: form,
      })
      if (!res.ok) throw new Error(String(res.status))
      toast.success("Dados salvos. A tela será atualizada.")
      if (typeof window !== "undefined") {
        window.dispatchEvent(new Event("admin-revisions-updated"))
      }
      onSaved()
    } catch {
      toast.error("Falha ao salvar. Verifique os campos.")
    } finally {
      setSaving(false)
    }
  }

  if (!open) return null

  if (loading || !dto) {
    return (
      <div className="rounded-xl border border-gray-100 bg-white p-4 text-sm text-gray-500">
        Carregando dados para edição...
      </div>
    )
  }

  return (
    <div className="rounded-xl border border-pink-100 bg-pink-50/30 p-4 shadow-sm">
      <p className="mb-3 text-sm font-semibold text-gray-900">Editar anúncio (revisão pendente)</p>
      <p className="mb-3 text-xs text-gray-600">
        Alterações em anúncios ativos geram uma revisão. A mídia exibida aqui é o snapshot que será aplicado
        na aprovação, alinhado ao que está publicado quando você não altera fotos/vídeos.
      </p>

      <div className="grid gap-3 sm:grid-cols-2">
        <div className="flex flex-col gap-1">
          <Label htmlFor="inline-titulo">Título</Label>
          <Input
            id="inline-titulo"
            value={dto.titulo}
            onChange={(e) => setDto((d) => (d ? { ...d, titulo: e.target.value } : d))}
            className="h-10"
          />
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="inline-preco">Preço</Label>
          <Input
            id="inline-preco"
            value={dto.preco}
            onChange={(e) => setDto((d) => (d ? { ...d, preco: e.target.value } : d))}
            className="h-10"
            placeholder="R$ 0,00"
          />
        </div>

        <div className="flex flex-col gap-1">
          <Label>Categoria</Label>
          <Select
            value={dto.categoria || ""}
            onValueChange={(v) => setDto((d) => (d ? { ...d, categoria: v } : d))}
          >
            <SelectTrigger className="h-10">
              <SelectValue placeholder="Categoria" />
            </SelectTrigger>
            <SelectContent>
              {CATEGORIAS.map((c) => (
                <SelectItem key={c.value} value={c.value}>
                  {c.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="flex flex-col gap-1">
          <Label>Horário</Label>
          <Select
            value={dto.horario || ""}
            onValueChange={(v) => setDto((d) => (d ? { ...d, horario: v } : d))}
          >
            <SelectTrigger className="h-10">
              <SelectValue placeholder="Horário" />
            </SelectTrigger>
            <SelectContent>
              {HORARIOS.map((h) => (
                <SelectItem key={h.value} value={h.value}>
                  {h.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

      </div>

      <div className="mt-4 grid gap-2 sm:grid-cols-3">
        <div className="flex flex-col gap-1">
          <Label>Estado</Label>
          <Popover open={openEstado} onOpenChange={setOpenEstado}>
            <PopoverTrigger asChild>
              <Button variant="outline" role="combobox" className={comboboxClass}>
                <span className="truncate">{labelEstado}</span>
                <ChevronUpDownIcon className="ml-2 h-4 w-4 shrink-0 opacity-60" />
              </Button>
            </PopoverTrigger>
            <PopoverContent className="w-[280px] p-0" align="start">
              <Command>
                <CommandInput placeholder="Buscar estado..." />
                <CommandList>
                  <CommandEmpty>Nenhum resultado.</CommandEmpty>
                  <CommandGroup>
                    {estados.map((e) => (
                      <CommandItem
                        key={e.id}
                        value={`${e.nome} ${e.uf ?? ""}`}
                        onSelect={() => {
                          setSelectedEstado(e)
                          setOpenEstado(false)
                        }}
                      >
                        <CheckIcon
                          className={cn(
                            "mr-2 h-4 w-4",
                            selectedEstado?.id === e.id ? "opacity-100" : "opacity-0"
                          )}
                        />
                        {e.nome} {e.uf ? `(${e.uf})` : ""}
                      </CommandItem>
                    ))}
                  </CommandGroup>
                </CommandList>
              </Command>
            </PopoverContent>
          </Popover>
        </div>

        <div className="flex flex-col gap-1">
          <Label>Cidade</Label>
          <Popover open={openCidade} onOpenChange={setOpenCidade}>
            <PopoverTrigger asChild>
              <Button variant="outline" role="combobox" disabled={!selectedEstado} className={comboboxClass}>
                <span className="truncate">{labelCidade}</span>
                <ChevronUpDownIcon className="ml-2 h-4 w-4 shrink-0 opacity-60" />
              </Button>
            </PopoverTrigger>
            <PopoverContent className="w-[280px] p-0" align="start">
              <Command>
                <CommandInput placeholder="Buscar cidade..." />
                <CommandList>
                  <CommandEmpty>Nenhum resultado.</CommandEmpty>
                  <CommandGroup>
                    {cidades.map((c) => (
                      <CommandItem
                        key={c.id}
                        value={c.nome}
                        onSelect={() => {
                          setSelectedCidade(c)
                          setOpenCidade(false)
                        }}
                      >
                        <CheckIcon
                          className={cn(
                            "mr-2 h-4 w-4",
                            selectedCidade?.id === c.id ? "opacity-100" : "opacity-0"
                          )}
                        />
                        {c.nome}
                      </CommandItem>
                    ))}
                  </CommandGroup>
                </CommandList>
              </Command>
            </PopoverContent>
          </Popover>
        </div>

        <div className="flex flex-col gap-1">
          <Label>Bairro</Label>
          <Popover open={openBairro} onOpenChange={setOpenBairro}>
            <PopoverTrigger asChild>
              <Button variant="outline" role="combobox" disabled={!selectedCidade} className={comboboxClass}>
                <span className="truncate">{labelBairro}</span>
                <ChevronUpDownIcon className="ml-2 h-4 w-4 shrink-0 opacity-60" />
              </Button>
            </PopoverTrigger>
            <PopoverContent className="w-[280px] p-0" align="start">
              <Command>
                <CommandInput placeholder="Buscar bairro..." />
                <CommandList>
                  <CommandEmpty>Nenhum resultado.</CommandEmpty>
                  <CommandGroup>
                    {bairros.map((b) => (
                      <CommandItem
                        key={b.id}
                        value={b.nome}
                        onSelect={() => {
                          setSelectedBairro(b)
                          setOpenBairro(false)
                        }}
                      >
                        <CheckIcon
                          className={cn(
                            "mr-2 h-4 w-4",
                            selectedBairro?.id === b.id ? "opacity-100" : "opacity-0"
                          )}
                        />
                        {b.nome}
                      </CommandItem>
                    ))}
                  </CommandGroup>
                </CommandList>
              </Command>
            </PopoverContent>
          </Popover>
        </div>
      </div>

      <div className="mt-4 flex flex-col gap-1">
        <Label htmlFor="inline-ponto-referencia">Micro-região</Label>
        <Input
          id="inline-ponto-referencia"
          value={dto.pontoReferenciaTexto ?? ""}
          maxLength={120}
          onChange={(e) => setDto((d) => (d ? { ...d, pontoReferenciaTexto: e.target.value } : d))}
          placeholder="Ex: Próx ao Bretas, Região da 44"
          className="h-10"
        />
      </div>

      <div className="mt-4">
        <p className="mb-2 text-xs font-medium uppercase tracking-wide text-gray-500">Locais de atendimento</p>
        <div className="flex flex-wrap gap-3">
          {LOCAIS_ATEND.map((opt) => (
            <label key={opt.value} className="flex items-center gap-2 text-sm text-gray-700">
              <input
                type="checkbox"
                className="accent-pink-500"
                checked={dto.locaisAtendimento.includes(opt.value)}
                onChange={() =>
                  setDto((d) =>
                    d ? { ...d, locaisAtendimento: toggleInArray(d.locaisAtendimento, opt.value) } : d
                  )
                }
              />
              {opt.label}
            </label>
          ))}
        </div>
      </div>

      <div className="mt-4">
        <p className="mb-2 text-xs font-medium uppercase tracking-wide text-gray-500">Serviços</p>
        <div className="max-h-40 overflow-y-auto rounded-lg border border-gray-100 bg-white p-2">
          <div className="grid grid-cols-2 gap-2 text-sm sm:grid-cols-3">
            {servicosDisponiveis.map((s) => (
              <label key={s.value} className="flex items-center gap-2 text-gray-700">
                <input
                  type="checkbox"
                  className="accent-pink-500"
                  checked={dto.servicos.includes(s.value)}
                  onChange={() =>
                    setDto((d) => (d ? { ...d, servicos: toggleInArray(d.servicos, s.value) } : d))
                  }
                />
                <span className="truncate">{s.label}</span>
              </label>
            ))}
          </div>
        </div>
      </div>

      <div className="mt-4 flex flex-wrap gap-2">
        <Button
          type="button"
          size="sm"
          className="bg-[#FC1EAD] text-white hover:bg-[#e01a9a]"
          disabled={saving}
          onClick={() => void handleSave()}
        >
          {saving ? "Salvando..." : "Salvar dados"}
        </Button>
        <Button type="button" size="sm" variant="outline" disabled={saving} onClick={onCancel}>
          Cancelar
        </Button>
      </div>
    </div>
  )
}
