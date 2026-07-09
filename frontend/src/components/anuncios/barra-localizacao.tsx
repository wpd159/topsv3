"use client"

import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { useRouter, useSearchParams } from "next/navigation"
import {
  ChevronDownIcon,
  ChevronUpDownIcon,
  CheckIcon,
  MapPinIcon,
  MagnifyingGlassIcon,
} from "@heroicons/react/24/solid"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command"
import { cn } from "@/lib/utils"

type EstadoItem = { id: number; nome: string; uf?: string }
type CidadeItem = { id: number; nome: string }
type BairroItem = { id: number; nome: string }

function normalizeKey(raw?: string | null) {
  if (!raw) return ""
  return raw
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, " ")
    .replace(/\s+/g, " ")
    .trim()
}

function isUfCandidate(raw?: string | null) {
  if (!raw) return false
  return /^[A-Za-z]{2}$/.test(raw.trim())
}

function parseLocalFromText(raw: string) {
  const original = raw.trim()
  if (!original) return { kind: "none" as const }

  let parts = original
    .split(",")
    .map((part) => part.trim())
    .filter(Boolean)

  if (parts.length === 1 && parts[0].includes("-")) {
    parts = parts[0].split("-").map((part) => part.trim()).filter(Boolean)
  }

  if (parts.length >= 2) {
    const last = parts[parts.length - 1]
    if (last.includes("-")) {
      const split = last.split("-").map((part) => part.trim()).filter(Boolean)
      parts = [...parts.slice(0, -1), ...split]
    }
  }

  if (parts.length === 1) {
    const p0 = parts[0]
    if (isUfCandidate(p0)) return { kind: "estado" as const, estadoRaw: p0 }
    return { kind: "estado" as const, estadoRaw: p0 }
  }

  if (parts.length === 2) {
    return {
      kind: "cidadeEstado" as const,
      cidadeRaw: parts[0],
      estadoRaw: parts[1],
    }
  }

  return {
    kind: "bairroCidadeEstado" as const,
    bairroRaw: parts[0],
    cidadeRaw: parts[1],
    estadoRaw: parts[2],
  }
}

export function BarraLocalizacao() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const apiUrl = process.env.NEXT_PUBLIC_API_URL

  const estadoIdParam = searchParams.get("estadoId")
  const cidadeIdParam = searchParams.get("cidadeId")
  const bairroIdParam = searchParams.get("bairroId")
  const buscaParam = searchParams.get("busca") || ""

  const [busca, setBusca] = useState(buscaParam)
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
  const [pendingCidadeKey, setPendingCidadeKey] = useState("")
  const [pendingBairroKey, setPendingBairroKey] = useState("")
  const [mobileRefinadoresOpen, setMobileRefinadoresOpen] = useState(false)

  const didAutoResolveRef = useRef(false)
  const inputRef = useRef<HTMLInputElement | null>(null)
  const didApplyDesktopFocusRef = useRef(false)
  const pendingNavigationRef = useRef<number | null>(null)

  useEffect(() => {
    return () => {
      if (pendingNavigationRef.current !== null) {
        window.clearTimeout(pendingNavigationRef.current)
      }
    }
  }, [])

  const pushBuscaRoute = useCallback(
    (href: string) => {
      if (typeof window === "undefined") {
        router.push(href)
        return
      }

      const isMobile = window.matchMedia("(max-width: 767px)").matches
      const activeElement = document.activeElement instanceof HTMLElement ? document.activeElement : null
      const shouldCloseKeyboard = isMobile && activeElement === inputRef.current

      if (!shouldCloseKeyboard) {
        router.push(href)
        return
      }

      if (!activeElement) {
        router.push(href)
        return
      }

      activeElement.blur()

      if (pendingNavigationRef.current !== null) {
        window.clearTimeout(pendingNavigationRef.current)
      }

      pendingNavigationRef.current = window.setTimeout(() => {
        pendingNavigationRef.current = null
        router.push(href)
      }, 120)
    },
    [router]
  )

  const setParamsBatch = useCallback(
    (updates: Record<string, string | undefined>) => {
      const params = new URLSearchParams(searchParams)

      Object.entries(updates).forEach(([key, value]) => {
        if (value === undefined) params.delete(key)
        else params.set(key, value)
      })

      params.delete("page")
      const nextQuery = params.toString()
      pushBuscaRoute(nextQuery ? `/anuncios?${nextQuery}` : "/anuncios")
    },
    [pushBuscaRoute, searchParams]
  )

  useEffect(() => {
    setBusca(buscaParam)
  }, [buscaParam])

  useEffect(() => {
    if (didApplyDesktopFocusRef.current) return
    if (typeof window === "undefined") return
    if (window.matchMedia("(max-width: 767px)").matches) return

    inputRef.current?.focus()
    didApplyDesktopFocusRef.current = true
  }, [])

  useEffect(() => {
    if (!apiUrl) return

    const load = async () => {
      try {
        setLoadingEstados(true)
        const res = await fetch(`${apiUrl}/localidades/estados`, {
          credentials: "include",
          cache: "no-store",
        })
        if (!res.ok) throw new Error()

        const data = (await res.json()) as EstadoItem[]
        setEstados(Array.isArray(data) ? data : [])
      } finally {
        setLoadingEstados(false)
      }
    }

    void load()
  }, [apiUrl])

  const findEstadoByRaw = useCallback(
    (rawEstado: string) => {
      const normalized = normalizeKey(rawEstado)
      const ufUpper = rawEstado.trim().toUpperCase()

      return (
        estados.find((estado) => normalizeKey(estado.nome) === normalized) ||
        estados.find((estado) => (estado.uf ? estado.uf.toUpperCase() === ufUpper : false)) ||
        estados.find((estado) => (estado.uf ? normalizeKey(estado.uf) === normalized : false)) ||
        null
      )
    },
    [estados]
  )

  const applyBusca = useCallback(
    (rawOverride?: string) => {
      const raw = (rawOverride ?? busca).trim()
      const possuiFiltrosLocaisAtivos = Boolean(selectedEstado || selectedCidade || selectedBairro)

      if (!raw) {
        setParamsBatch({ busca: undefined })
        return
      }

      if (possuiFiltrosLocaisAtivos || estados.length === 0) {
        setParamsBatch({ busca: raw })
        return
      }

      const parsed = parseLocalFromText(raw)

      if (parsed.kind === "estado") {
        const foundEstado = findEstadoByRaw(parsed.estadoRaw)
        if (foundEstado) {
          setSelectedEstado(foundEstado)
          setSelectedCidade(null)
          setSelectedBairro(null)
          setPendingCidadeKey("")
          setPendingBairroKey("")
          setBusca("")

          setParamsBatch({
            estadoId: String(foundEstado.id),
            cidadeId: undefined,
            bairroId: undefined,
            busca: undefined,
          })
          return
        }
      }

      if (parsed.kind === "cidadeEstado") {
        const foundEstado = findEstadoByRaw(parsed.estadoRaw)
        if (foundEstado) {
          setSelectedEstado(foundEstado)
          setSelectedCidade(null)
          setSelectedBairro(null)
          setPendingCidadeKey(normalizeKey(parsed.cidadeRaw))
          setPendingBairroKey("")
          setBusca("")

          setParamsBatch({
            estadoId: String(foundEstado.id),
            cidadeId: undefined,
            bairroId: undefined,
            busca: undefined,
          })
          return
        }
      }

      if (parsed.kind === "bairroCidadeEstado") {
        const foundEstado = findEstadoByRaw(parsed.estadoRaw)
        if (foundEstado) {
          setSelectedEstado(foundEstado)
          setSelectedCidade(null)
          setSelectedBairro(null)
          setPendingCidadeKey(normalizeKey(parsed.cidadeRaw))
          setPendingBairroKey(normalizeKey(parsed.bairroRaw))
          setBusca("")

          setParamsBatch({
            estadoId: String(foundEstado.id),
            cidadeId: undefined,
            bairroId: undefined,
            busca: undefined,
          })
          return
        }
      }

      setParamsBatch({ busca: raw })
    },
    [busca, estados.length, findEstadoByRaw, selectedEstado, selectedCidade, selectedBairro, setParamsBatch]
  )

  useEffect(() => {
    if (didAutoResolveRef.current) return
    if (!buscaParam.trim()) return
    if (estadoIdParam || cidadeIdParam || bairroIdParam) return
    if (estados.length === 0) return

    didAutoResolveRef.current = true
    applyBusca(buscaParam)
  }, [applyBusca, bairroIdParam, buscaParam, cidadeIdParam, estadoIdParam, estados.length])

  useEffect(() => {
    if (!estadoIdParam || estados.length === 0) return
    const found = estados.find((estado) => estado.id === Number(estadoIdParam)) || null
    setSelectedEstado(found)
  }, [estadoIdParam, estados])

  useEffect(() => {
    setCidades([])
    setBairros([])
    setSelectedCidade(null)
    setSelectedBairro(null)

    if (!selectedEstado || !apiUrl) return

    const load = async () => {
      try {
        setLoadingCidades(true)
        const res = await fetch(
          `${apiUrl}/localidades/estados/${selectedEstado.id}/cidades?ativas=true`,
          { credentials: "include", cache: "no-store" }
        )
        if (!res.ok) throw new Error()

        const data = (await res.json()) as CidadeItem[]
        setCidades(Array.isArray(data) ? data : [])
      } finally {
        setLoadingCidades(false)
      }
    }

    void load()
  }, [apiUrl, selectedEstado?.id])

  useEffect(() => {
    if (!cidadeIdParam || cidades.length === 0) return
    const found = cidades.find((cidade) => cidade.id === Number(cidadeIdParam)) || null
    setSelectedCidade(found)
  }, [cidadeIdParam, cidades])

  useEffect(() => {
    setBairros([])
    setSelectedBairro(null)

    if (!selectedCidade || !apiUrl) return

    const load = async () => {
      try {
        setLoadingBairros(true)
        const res = await fetch(
          `${apiUrl}/localidades/cidades/${selectedCidade.id}/bairros?ativas=true`,
          { credentials: "include", cache: "no-store" }
        )
        if (!res.ok) throw new Error()

        const data = (await res.json()) as BairroItem[]
        setBairros(Array.isArray(data) ? data : [])
      } finally {
        setLoadingBairros(false)
      }
    }

    void load()
  }, [apiUrl, selectedCidade?.id])

  useEffect(() => {
    if (!bairroIdParam || bairros.length === 0) return
    const found = bairros.find((bairro) => bairro.id === Number(bairroIdParam)) || null
    setSelectedBairro(found)
  }, [bairroIdParam, bairros])

  useEffect(() => {
    if (!pendingCidadeKey || cidades.length === 0) return
    const found = cidades.find((cidade) => normalizeKey(cidade.nome) === pendingCidadeKey) || null
    if (!found) return

    setSelectedCidade(found)
    setPendingCidadeKey("")

    setParamsBatch({
      cidadeId: String(found.id),
      bairroId: undefined,
      busca: busca.trim() || undefined,
    })
  }, [bairros.length, busca, cidades, pendingCidadeKey, setParamsBatch])

  useEffect(() => {
    if (!pendingBairroKey || bairros.length === 0) return
    const found = bairros.find((bairro) => normalizeKey(bairro.nome) === pendingBairroKey) || null
    if (!found) return

    setSelectedBairro(found)
    setPendingBairroKey("")

    setParamsBatch({
      bairroId: String(found.id),
      busca: busca.trim() || undefined,
    })
  }, [bairros, busca, pendingBairroKey, setParamsBatch])

  const labelEstado = useMemo(() => {
    if (loadingEstados) return "Carregando estados..."
    if (!selectedEstado) return "Estado"
    return selectedEstado.uf ? `${selectedEstado.nome} (${selectedEstado.uf})` : selectedEstado.nome
  }, [loadingEstados, selectedEstado])

  const labelCidade = useMemo(() => {
    if (!selectedEstado) return "Cidade"
    if (loadingCidades) return "Carregando cidades..."
    if (!selectedCidade) return "Cidade"
    return selectedCidade.nome
  }, [loadingCidades, selectedCidade, selectedEstado])

  const labelBairro = useMemo(() => {
    if (!selectedCidade) return "Bairro"
    if (loadingBairros) return "Carregando bairros..."
    if (!selectedBairro) return "Bairro"
    return selectedBairro.nome
  }, [loadingBairros, selectedBairro, selectedCidade])

  const placeholderBusca = useMemo(() => {
    if (selectedCidade?.nome) {
      return `Buscar em ${selectedCidade.nome} por nome, bairro ou característica...`
    }

    if (selectedEstado?.nome) {
      return `Buscar em ${selectedEstado.nome} por nome, bairro ou cidade...`
    }

    return "Buscar acompanhantes, bairro ou nome..."
  }, [selectedCidade?.nome, selectedEstado?.nome])

  const limparTudo = () => {
    setOpenEstado(false)
    setOpenCidade(false)
    setOpenBairro(false)
    setSelectedEstado(null)
    setSelectedCidade(null)
    setSelectedBairro(null)
    setPendingCidadeKey("")
    setPendingBairroKey("")
    setCidades([])
    setBairros([])
    setBusca("")
    didAutoResolveRef.current = false

    setParamsBatch({
      estadoId: undefined,
      cidadeId: undefined,
      bairroId: undefined,
      busca: undefined,
    })
  }

  const refinadoresAtivos =
    Boolean(selectedEstado) || Boolean(selectedCidade) || Boolean(selectedBairro)

  return (
    <div className="w-full rounded-[30px] border border-gray-200 bg-white p-3 shadow-[0_10px_35px_rgba(15,23,42,0.06)] sm:p-5 lg:p-6">
      <div className="flex flex-col gap-3 md:gap-6">
        <div className="hidden space-y-2 md:block">
          <div className="inline-flex items-center gap-2 rounded-full bg-pink-50 px-3 py-1 text-xs font-medium text-pink-700">
            <MagnifyingGlassIcon className="h-4 w-4" />
            Busca
          </div>
          <div className="space-y-1">
            <h2 className="text-xl font-semibold text-gray-900 sm:text-2xl">
              Busque primeiro. Refine depois.
            </h2>
            <p className="max-w-3xl text-sm leading-6 text-gray-500">
              Encontre por nome, bairro, cidade ou característica e use os filtros de localização
              apenas para refinar o resultado.
            </p>
          </div>
        </div>

        <div className="grid gap-3 md:gap-4 lg:grid-cols-[minmax(0,1fr)_180px] lg:items-end">
          <div className="rounded-[26px] border border-pink-100 bg-gradient-to-r from-pink-50/70 via-white to-pink-50/40 p-3 shadow-sm">
            <div className="relative">
              <MagnifyingGlassIcon className="pointer-events-none absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-pink-500/80" />
              <Input
                ref={inputRef}
                value={busca}
                onChange={(e) => setBusca(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    e.preventDefault()
                    applyBusca()
                  }
                }}
                placeholder={placeholderBusca}
                className="h-12 rounded-2xl border-pink-200 bg-white pl-12 pr-4 text-base shadow-none placeholder:text-gray-400 focus-visible:border-[#FC1EAD] focus-visible:ring-4 focus-visible:ring-[#FC1EAD]/15 md:h-14"
              />
            </div>
          </div>

          <Button
            className="h-12 rounded-2xl bg-[#FC1EAD] px-7 text-sm font-semibold text-white shadow-[0_12px_28px_rgba(252,30,173,0.26)] hover:bg-[#e01a9a] md:h-14"
            onClick={() => applyBusca()}
          >
            Buscar
          </Button>
        </div>

        <Button
          type="button"
          variant="outline"
          aria-expanded={mobileRefinadoresOpen}
          aria-controls="barra-localizacao-refinadores"
          onClick={() => setMobileRefinadoresOpen((open) => !open)}
          className="h-11 w-full justify-between rounded-2xl border-gray-200 bg-white px-4 text-sm font-medium text-gray-700 md:hidden"
        >
          <span className="inline-flex items-center gap-2">
            <MapPinIcon className="h-4 w-4 text-gray-500" aria-hidden />
            Filtros
            {refinadoresAtivos ? (
              <span className="rounded-full bg-pink-100 px-2 py-0.5 text-[10px] font-semibold text-pink-700">
                ativos
              </span>
            ) : null}
          </span>
          <ChevronDownIcon
            className={cn(
              "h-5 w-5 text-gray-500 transition-transform duration-200",
              mobileRefinadoresOpen && "rotate-180"
            )}
            aria-hidden
          />
        </Button>

        <div
          id="barra-localizacao-refinadores"
          className={cn(
            "rounded-[24px] border border-gray-100 bg-gray-50/70 p-3 sm:p-4",
            mobileRefinadoresOpen ? "block" : "hidden",
            "md:block"
          )}
        >
          <div className="mb-3 hidden items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.16em] text-gray-500 md:flex">
            <MapPinIcon className="h-4 w-4 text-gray-400" />
            Localização e refinadores
          </div>

          <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_minmax(0,1fr)_120px]">
            <Combo<EstadoItem>
              open={openEstado}
              onOpenChange={setOpenEstado}
              disabled={loadingEstados}
              label={labelEstado}
              items={estados}
              placeholder="Buscar estado..."
              emptyText={loadingEstados ? "Carregando..." : "Nenhum estado encontrado."}
              itemToValue={(item) => `${item.nome} ${item.uf ?? ""}`}
              isSelected={(item) => selectedEstado?.id === item.id}
              onSelect={(item) => {
                setSelectedEstado(item)
                setOpenEstado(false)
                setPendingCidadeKey("")
                setPendingBairroKey("")
                didAutoResolveRef.current = true

                setParamsBatch({
                  estadoId: String(item.id),
                  cidadeId: undefined,
                  bairroId: undefined,
                  busca: busca.trim() || undefined,
                })
              }}
            />

            <Combo<CidadeItem>
              open={openCidade}
              onOpenChange={setOpenCidade}
              disabled={!selectedEstado || loadingCidades}
              label={labelCidade}
              items={cidades}
              placeholder={!selectedEstado ? "Selecione um estado" : "Buscar cidade..."}
              emptyText={
                !selectedEstado
                  ? "Selecione o estado primeiro."
                  : loadingCidades
                    ? "Carregando..."
                    : "Nenhuma cidade encontrada."
              }
              itemToValue={(item) => item.nome}
              isSelected={(item) => selectedCidade?.id === item.id}
              onSelect={(item) => {
                setSelectedCidade(item)
                setOpenCidade(false)
                setPendingBairroKey("")
                didAutoResolveRef.current = true

                setParamsBatch({
                  cidadeId: String(item.id),
                  bairroId: undefined,
                  busca: busca.trim() || undefined,
                })
              }}
            />

            <Combo<BairroItem>
              open={openBairro}
              onOpenChange={setOpenBairro}
              disabled={!selectedCidade || loadingBairros}
              label={labelBairro}
              items={bairros}
              placeholder={!selectedCidade ? "Selecione uma cidade" : "Buscar bairro..."}
              emptyText={
                !selectedCidade
                  ? "Selecione a cidade primeiro."
                  : loadingBairros
                    ? "Carregando..."
                    : "Nenhum bairro encontrado."
              }
              itemToValue={(item) => item.nome}
              isSelected={(item) => selectedBairro?.id === item.id}
              onSelect={(item) => {
                setSelectedBairro(item)
                setOpenBairro(false)
                didAutoResolveRef.current = true

                setParamsBatch({
                  bairroId: String(item.id),
                  busca: busca.trim() || undefined,
                })
              }}
            />

            <Button
              variant="outline"
              className="h-12 rounded-2xl border-gray-200 bg-white px-5 text-sm font-medium text-gray-500 hover:bg-gray-100 hover:text-gray-700"
              onClick={limparTudo}
            >
              Limpar
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}

function Combo<T extends { id: number; nome: string }>({
  open,
  onOpenChange,
  disabled,
  label,
  items,
  placeholder,
  emptyText,
  itemToValue,
  isSelected,
  onSelect,
}: {
  open: boolean
  onOpenChange: (value: boolean) => void
  disabled?: boolean
  label: string
  items: T[]
  placeholder: string
  emptyText: string
  itemToValue?: (item: T) => string
  isSelected: (item: T) => boolean
  onSelect: (item: T) => void
}) {
  return (
    <Popover open={open} onOpenChange={onOpenChange}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          disabled={disabled}
          className="h-12 w-full justify-between rounded-2xl border-gray-200 bg-white px-4 text-sm text-gray-600 shadow-none hover:bg-white"
        >
          <span className="truncate">{label}</span>
          <ChevronUpDownIcon className="h-4 w-4 opacity-60" />
        </Button>
      </PopoverTrigger>

      <PopoverContent className="w-[var(--radix-popover-trigger-width)] p-0">
        <Command
          filter={(value, search) => {
            const normalizedValue = normalizeKey(value)
            const normalizedSearch = normalizeKey(search)
            if (!normalizedSearch) return 1
            return normalizedValue.includes(normalizedSearch) ? 1 : 0
          }}
        >
          <CommandInput placeholder={placeholder} className="text-base" />
          <CommandList>
            <CommandEmpty>{emptyText}</CommandEmpty>
            <CommandGroup>
              {items.map((item) => {
                const value = itemToValue ? itemToValue(item) : item.nome
                return (
                  <CommandItem key={item.id} value={value} onSelect={() => onSelect(item)}>
                    <CheckIcon
                      className={cn("mr-2 h-4 w-4", isSelected(item) ? "opacity-100" : "opacity-0")}
                    />
                    {item.nome}
                  </CommandItem>
                )
              })}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  )
}
