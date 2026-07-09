'use client'

import type React from 'react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useRouter } from 'next/navigation'

import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Label } from '@/components/ui/label'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import {
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
  SelectItem,
} from '@/components/ui/select'

import { Popover, PopoverTrigger, PopoverContent } from '@/components/ui/popover'
import {
  Command,
  CommandInput,
  CommandItem,
  CommandList,
  CommandEmpty,
  CommandGroup,
} from '@/components/ui/command'

import { PlayIcon, MapPinIcon, CheckIcon, ChevronUpDownIcon } from '@heroicons/react/24/solid'
import { cn } from '@/lib/utils'
import { formatCurrencyBRL } from '@/utils/formatter'

type EstadoItem = { id: number; nome: string; uf?: string }
type CidadeItem = { id: number; nome: string }
type BairroItem = { id: number; nome: string }

type AnuncioEditDTO = {
  id: number
  slug: string
  titulo: string
  categoria: string
  preco?: string | number | null
  horario?: string | null
  locaisAtendimento: string[]
  servicos: string[]
  descricao?: string | null
  linkConteudo?: string | null
  pontoReferenciaTexto?: string | null

  estadoId?: number | null
  estadoNome?: string | null
  estadoUf?: string | null

  cidadeId?: number | null
  cidadeNome?: string | null

  bairroId?: number | null
  bairroNome?: string | null

  localizacaoLabel?: string | null

  fotos: string[]
  videosAnuncio?: string[]
}

const CATEGORIAS = [
  { value: 'ACOMPANHANTE_FEMININA', label: 'Acompanhante Feminina' },
  { value: 'ACOMPANHANTE_MASCULINO', label: 'Acompanhante Masculino' },
  { value: 'TRANSEX_TRAVESTIS', label: 'Trans / Travestis' },
  { value: 'MASSAGENS', label: 'Massagens' },
  { value: 'VENDA_DE_CONTEUDO', label: 'Sexo Virtual' },
] as const

const HORARIOS = [
  { value: 'MANHA', label: 'Manhã' },
  { value: 'TARDE', label: 'Tarde' },
  { value: 'NOITE', label: 'Noite' },
  { value: 'QUALQUER_HORARIO', label: 'Qualquer horário' },
] as const

const LOCAIS = [
  { value: 'A_COMBINAR', label: 'A combinar' },
  { value: 'HOTEL_MOTEL', label: 'Hotel/Motel' },
  { value: 'MEU_LOCAL', label: 'Meu Local' },
]

const SERVICOS = [
  { value: 'ANAL', label: 'Anal' },
  { value: 'ATIVO', label: 'Ativo' },
  { value: 'ATRIZ_PORNO', label: 'Atriz Pornô' },
  { value: 'BDSM', label: 'BDSM' },
  { value: 'MASSAGEM_EROTICA', label: 'Massagem Erótica' },
  { value: 'MASSAGEM_TANTRICA', label: 'Massagem Tântrica' },
  { value: 'ORAL', label: 'Oral' },
  { value: 'PASSIVO', label: 'Passivo' },
  { value: 'NAMORADAS', label: 'Namorados' },
  { value: 'TRIO', label: 'Trio' },
  { value: 'VIDEOCHAMADA', label: 'Sexo virtual' },
]

function isValidEnumValue(value: string, allowed: readonly { value: string }[]) {
  return allowed.some((a) => a.value === value)
}

type AnuncioStaffEditFormProps = {
  anuncioId: number
  embedded?: boolean
  onSaved?: () => void
}

export function AnuncioStaffEditForm({ anuncioId, embedded = false, onSaved }: AnuncioStaffEditFormProps) {
  const id = String(anuncioId)
  const router = useRouter()
  const API_URL = process.env.NEXT_PUBLIC_API_URL

  const [anuncio, setAnuncio] = useState<AnuncioEditDTO | null>(null)
  const [images, setImages] = useState<File[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [previewUrls, setPreviewUrls] = useState<string[]>([])

  // ✅ localização
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

  // ✅ preload correto (não resetar cidade/bairro no meio)
  const bootRef = useRef(true)
  const preloadLocRef = useRef<{ estadoId?: number | null; cidadeId?: number | null; bairroId?: number | null }>({})

  // estilos
  const controlBase =
    'h-11 w-full rounded-full border bg-gray-200 text-gray-800 placeholder:text-gray-400'
  const controlBorder =
    'border-gray-500/40 focus-visible:ring-0 focus-visible:outline-none focus-visible:border-gray-500/60'
  const controlPadding = 'pl-4 pr-10 text-sm'
  const comboboxClass = cn('min-w-0 justify-between', controlBase, controlBorder, controlPadding)

  // carrega estados
  useEffect(() => {
    if (!API_URL) return
    const load = async () => {
      try {
        setLoadingEstados(true)
        const res = await fetch(`${API_URL}/localidades/estados`, {
          credentials: 'include',
          cache: 'no-store',
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
    load()
  }, [API_URL])

  // quando muda estado -> carrega cidades
  useEffect(() => {
    if (!API_URL) return

    if (!selectedEstado?.id) {
      setCidades([])
      setBairros([])
      if (!bootRef.current) {
        setSelectedCidade(null)
        setSelectedBairro(null)
      }
      return
    }

    // ✅ se usuário trocou estado (não é boot), reseta já
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
          { credentials: 'include', cache: 'no-store' }
        )
        if (!res.ok) throw new Error()
        const data = (await res.json()) as CidadeItem[]
        const list = Array.isArray(data) ? data : []
        setCidades(list)

        // ✅ preload: acha a cidade real pelo ID
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

    load()
  }, [selectedEstado?.id, API_URL])

  // quando muda cidade -> carrega bairros
  useEffect(() => {
    if (!API_URL) return

    if (!selectedCidade?.id) {
      setBairros([])
      if (!bootRef.current) setSelectedBairro(null)
      return
    }

    // ✅ se usuário trocou cidade (não é boot), reseta já
    if (!bootRef.current) setSelectedBairro(null)

    const load = async () => {
      try {
        setLoadingBairros(true)
        const res = await fetch(
          `${API_URL}/localidades/cidades/${selectedCidade.id}/bairros?ativas=true`,
          { credentials: 'include', cache: 'no-store' }
        )
        if (!res.ok) throw new Error()
        const data = (await res.json()) as BairroItem[]
        const list = Array.isArray(data) ? data : []
        setBairros(list)

        // ✅ preload: acha o bairro real pelo ID
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

        // ✅ fim do preload (cadeia completa)
        if (bootRef.current) bootRef.current = false
      }
    }

    load()
  }, [selectedCidade?.id, API_URL])

  // carrega anúncio
  useEffect(() => {
    const load = async () => {
      try {
        setLoading(true)

        // ✅ garante que preload comece travado
        bootRef.current = true

        const res = await fetch(`${API_URL}/anuncios/staff/id/${id}/editar`, {
          credentials: 'include',
          cache: 'no-store',
        })
        if (!res.ok) throw new Error(`Erro ${res.status}`)
        const dto: AnuncioEditDTO = await res.json()

        // guarda ids do preload
        preloadLocRef.current = {
          estadoId: dto.estadoId ?? null,
          cidadeId: dto.cidadeId ?? null,
          bairroId: dto.bairroId ?? null,
        }

        // 🔢 preço formatado
        let precoFormatado = ''
        if (dto.preco !== null && dto.preco !== undefined) {
          let num = 0
          if (typeof dto.preco === 'number') num = dto.preco
          else {
            const s = String(dto.preco)
              .replace(/[^\d,.-]/g, '')
              .replace(/\./g, '')
              .replace(',', '.')
            const parsed = parseFloat(s)
            num = isNaN(parsed) ? 0 : parsed
          }
          if (num > 0) precoFormatado = formatCurrencyBRL(num)
        }

        const categoriaNormalizada = dto.categoria && isValidEnumValue(dto.categoria, CATEGORIAS) ? dto.categoria : ''
        const horarioNormalizado =
          dto.horario && isValidEnumValue(dto.horario, HORARIOS) ? dto.horario : ''

        setAnuncio({
          ...dto,
          categoria: categoriaNormalizada,
          horario: horarioNormalizado,
          locaisAtendimento: dto.locaisAtendimento ?? [],
          servicos: dto.servicos ?? [],
          pontoReferenciaTexto: dto.pontoReferenciaTexto ?? '',
          fotos: dto.fotos ?? [],
          videosAnuncio: dto.videosAnuncio ?? [],
          preco: precoFormatado,
        })

        // ✅ pré-seleciona estado imediatamente (cidade/bairro serão “confirmados” quando listas chegarem)
        if (dto.estadoId) {
          setSelectedEstado({
            id: dto.estadoId,
            nome: dto.estadoNome ?? '—',
            uf: dto.estadoUf ?? '',
          })
        } else {
          setSelectedEstado(null)
        }

        // seta placeholders (vai ser substituído pelo item real quando a lista carregar)
        if (dto.cidadeId) setSelectedCidade({ id: dto.cidadeId, nome: dto.cidadeNome ?? '—' })
        else setSelectedCidade(null)

        if (dto.bairroId) setSelectedBairro({ id: dto.bairroId, nome: dto.bairroNome ?? '—' })
        else setSelectedBairro(null)
      } catch (e) {
        alert('Não foi possível carregar o anúncio.')
        if (!embedded) router.back()
      } finally {
        setLoading(false)
      }
    }

    if (API_URL) load()
  }, [id, router, API_URL, embedded])

  const handleImageUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (!e.target.files) return
    setImages(Array.from(e.target.files))
  }

  useEffect(() => {
    if (!images.length) {
      setPreviewUrls([])
      return
    }

    const nextUrls = images.map((file) => URL.createObjectURL(file))
    setPreviewUrls(nextUrls)

    return () => {
      nextUrls.forEach((url) => {
        try {
          URL.revokeObjectURL(url)
        } catch {}
      })
    }
  }, [images])

  const handleChange = (field: keyof AnuncioEditDTO, value: any) => {
    setAnuncio((prev) => (prev ? { ...prev, [field]: value } : prev))
  }

  const toggleInArray = (arr: string[], value: string) =>
    arr.includes(value) ? arr.filter((v) => v !== value) : [...arr, value]

  const handleCheckboxChange = (field: 'locaisAtendimento' | 'servicos', value: string) => {
    setAnuncio((prev) => {
      if (!prev) return prev
      const atual = (prev as any)[field] as string[]
      return { ...prev, [field]: toggleInArray(atual, value) }
    })
  }

  const labelEstado = useMemo(() => {
    if (loadingEstados) return 'Carregando estados...'
    if (!selectedEstado) return 'Estado'
    return selectedEstado.nome
  }, [selectedEstado, loadingEstados])

  const labelCidade = useMemo(() => {
    if (!selectedEstado) return 'Cidade'
    if (loadingCidades) return 'Carregando cidades...'
    if (!selectedCidade) return 'Cidade'
    return selectedCidade.nome
  }, [selectedCidade, selectedEstado, loadingCidades])

  const labelBairro = useMemo(() => {
    if (!selectedCidade) return 'Bairro'
    if (loadingBairros) return 'Carregando bairros...'
    if (!selectedBairro) return 'Bairro'
    return selectedBairro.nome
  }, [selectedBairro, selectedCidade, loadingBairros])

  const localizacaoTexto = useMemo(() => {
    const ponto = anuncio?.pontoReferenciaTexto?.trim()
    if (selectedEstado && selectedCidade && selectedBairro) {
      const base = `${selectedCidade.nome} - ${selectedEstado.uf || ''} • ${selectedBairro.nome}`
      return ponto ? `${base} · ${ponto}` : base
    }
    return anuncio?.localizacaoLabel ?? ''
  }, [selectedEstado, selectedCidade, selectedBairro, anuncio?.localizacaoLabel, anuncio?.pontoReferenciaTexto])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!anuncio) return

    if (!anuncio.titulo?.trim()) return alert('Informe o título.')
    if (!anuncio.categoria) return alert('Selecione uma categoria.')
    if (!anuncio.horario) return alert('Selecione um horário.')

    if (!selectedEstado?.id || !selectedCidade?.id || !selectedBairro?.id) {
      return alert('Selecione Estado, Cidade e Bairro.')
    }

    // Mesma base do input (centavos em dígitos): evita enviar "0" quando vazio ou R$ 0,00
    const precoStr = (anuncio.preco as string) ?? ''
    const digitsOnly = precoStr.replace(/\D/g, '')
    if (digitsOnly) {
      const valorReais = Number(digitsOnly) / 100
      if (!Number.isFinite(valorReais) || valorReais <= 0) {
        return alert('Informe um preço maior que zero.')
      }
    }

    const form = new FormData()
    form.append('titulo', anuncio.titulo ?? '')
    form.append('categoria', anuncio.categoria ?? '')
    if (digitsOnly) {
      const valorReais = Number(digitsOnly) / 100
      form.append('preco', valorReais.toFixed(2))
    }
    form.append('horario', anuncio.horario ?? '')
    form.append('descricao', anuncio.descricao ?? '')
    form.append('linkConteudo', anuncio.linkConteudo ?? '')
    form.append('pontoReferenciaTexto', anuncio.pontoReferenciaTexto ?? '')

    form.append('cidadeId', String(selectedCidade.id))
    form.append('bairroId', String(selectedBairro.id))

    anuncio.locaisAtendimento.forEach((v) => form.append('locaisAtendimento', v))
    anuncio.servicos.forEach((v) => form.append('servicos', v))

    anuncio.fotos.forEach((url) => form.append('fotosExistentes', url))
    anuncio.videosAnuncio?.forEach((url) => form.append('videosExistentes', url))
    images.forEach((file) => form.append('novasFotos', file))

    setSaving(true)
    try {
      const res = await fetch(`${API_URL}/anuncios/staff/id/${id}/editar`, {
        method: 'PUT',
        credentials: 'include',
        body: form,
      })
      if (!res.ok) {
        let msg = `Erro ${res.status}`
        try {
          const errBody = await res.json()
          if (errBody?.error && typeof errBody.error === 'string') msg = errBody.error
        } catch {
          /* ignore */
        }
        throw new Error(msg)
      }
      const pendingPayload = await res.json().catch(() => null)
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new Event('admin-revisions-updated'))
      }
      const msg = pendingPayload?.pendingRevision
        ? 'Revisão pendente criada com sucesso!'
        : 'Anúncio atualizado com sucesso!'
      alert(msg)
      onSaved?.()
      if (!embedded) {
        router.push(`/admin/moderacao-v2/${id}`)
      }
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Falha ao salvar. Verifique os campos e tente novamente.')
    } finally {
      setSaving(false)
    }
  }

  if (loading || !anuncio) return <div className="p-10 text-gray-500">Carregando dados...</div>

  return (
    <div>
      {!embedded && (
        <div className="flex flex-col gap-1 mb-6">
          <h1 className="text-2xl font-bold text-gray-800">Editar Anúncios</h1>
          <p className="text-sm text-gray-500">Atualize as informações e as mídias do anúncio.</p>
        </div>
      )}

      <Card className={embedded ? 'p-5 shadow-sm border border-gray-200' : 'p-8 shadow-xs border border-gray-300/50'}>
        <form onSubmit={handleSubmit} className="space-y-10">
          {/* Título e Categoria */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
            <div className="flex flex-col gap-2">
              <Label htmlFor="titulo" className="font-semibold text-gray-800">
                Título do anúncio
              </Label>
              <Input
                id="titulo"
                value={anuncio.titulo}
                onChange={(e) => handleChange('titulo', e.target.value)}
                className="py-5"
                required
              />
            </div>

            <div className="flex flex-col gap-2">
              <Label htmlFor="categoria" className="font-semibold text-gray-800">
                Categoria
              </Label>
              <Select value={anuncio.categoria || ''} onValueChange={(v) => handleChange('categoria', v)}>
                <SelectTrigger className="py-5 w-full bg-gray-200 border-gray-500/40">
                  <SelectValue placeholder="Selecione uma categoria" />
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
          </div>

          {/* Preço e Horário */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
            <div className="flex flex-col gap-2">
              <Label htmlFor="preco" className="font-semibold text-gray-800">
                Preço
              </Label>
              <Input
                id="preco"
                type="text"
                inputMode="decimal"
                value={(anuncio.preco as string) ?? ''}
                onChange={(e) => {
                  const raw = e.target.value.replace(/\D/g, '')
                  if (!raw) return handleChange('preco', '')
                  const numero = Number(raw) / 100
                  handleChange('preco', formatCurrencyBRL(numero))
                }}
                placeholder="R$ 0,00"
                className="py-5"
              />
            </div>

            <div className="flex flex-col gap-2">
              <Label htmlFor="horario" className="font-semibold text-gray-800">
                Horários
              </Label>
              <Select value={anuncio.horario ?? ''} onValueChange={(v) => handleChange('horario', v)}>
                <SelectTrigger className="py-5 w-full bg-gray-200 border-gray-500/40">
                  <SelectValue placeholder="Selecione o horário de atendimento" />
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

          {/* Localização */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-6">
            {/* Estado */}
            <div className="flex flex-col gap-2">
              <Label className="font-semibold text-gray-800">Estado</Label>
              <Popover open={openEstado} onOpenChange={setOpenEstado}>
                <PopoverTrigger asChild>
                  <Button
                    variant="outline"
                    role="combobox"
                    disabled={loadingEstados}
                    className={comboboxClass}
                  >
                    <span className={cn('min-w-0 flex-1 truncate', !selectedEstado && 'text-gray-500')}>
                      {labelEstado}
                    </span>
                    <ChevronUpDownIcon className="ml-2 h-4 w-4 shrink-0 opacity-60" />
                  </Button>
                </PopoverTrigger>
                <PopoverContent className="p-0 w-[var(--radix-popover-trigger-width)]">
                  <Command>
                    <CommandInput placeholder="Buscar estado..." />
                    <CommandList>
                      <CommandEmpty>{loadingEstados ? 'Carregando...' : 'Nenhum estado encontrado.'}</CommandEmpty>
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
                              <CheckIcon className={cn('mr-2 w-4 h-4', isSelected ? 'opacity-100' : 'opacity-0')} />
                              {e.nome}
                            </CommandItem>
                          )
                        })}
                      </CommandGroup>
                    </CommandList>
                  </Command>
                </PopoverContent>
              </Popover>
            </div>

            {/* Cidade */}
            <div className="flex flex-col gap-2">
              <Label className="font-semibold text-gray-800">Cidade</Label>
              <Popover open={openCidade} onOpenChange={setOpenCidade}>
                <PopoverTrigger asChild>
                  <Button
                    variant="outline"
                    role="combobox"
                    disabled={!selectedEstado || loadingCidades}
                    className={comboboxClass}
                  >
                    <span className={cn('min-w-0 flex-1 truncate', !selectedCidade && 'text-gray-500')}>
                      {labelCidade}
                    </span>
                    <ChevronUpDownIcon className="ml-2 h-4 w-4 shrink-0 opacity-60" />
                  </Button>
                </PopoverTrigger>
                <PopoverContent className="p-0 w-[var(--radix-popover-trigger-width)]">
                  <Command>
                    <CommandInput placeholder={!selectedEstado ? 'Selecione o estado' : 'Buscar cidade...'} disabled={!selectedEstado} />
                    <CommandList>
                      <CommandEmpty>
                        {!selectedEstado ? 'Selecione o estado primeiro.' : loadingCidades ? 'Carregando...' : 'Nenhuma cidade encontrada.'}
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
                                <CheckIcon className={cn('mr-2 w-4 h-4', isSelected ? 'opacity-100' : 'opacity-0')} />
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

            {/* Bairro */}
            <div className="flex flex-col gap-2">
              <Label className="font-semibold text-gray-800">Bairro</Label>
              <Popover open={openBairro} onOpenChange={setOpenBairro}>
                <PopoverTrigger asChild>
                  <Button
                    variant="outline"
                    role="combobox"
                    disabled={!selectedCidade || loadingBairros}
                    className={comboboxClass}
                  >
                    <span className={cn('min-w-0 flex-1 truncate', !selectedBairro && 'text-gray-500')}>
                      {labelBairro}
                    </span>
                    <ChevronUpDownIcon className="ml-2 h-4 w-4 shrink-0 opacity-60" />
                  </Button>
                </PopoverTrigger>
                <PopoverContent className="p-0 w-[var(--radix-popover-trigger-width)]">
                  <Command>
                    <CommandInput placeholder={!selectedCidade ? 'Selecione a cidade' : 'Buscar bairro...'} disabled={!selectedCidade} />
                    <CommandList>
                      <CommandEmpty>
                        {!selectedCidade ? 'Selecione a cidade primeiro.' : loadingBairros ? 'Carregando...' : 'Nenhum bairro encontrado.'}
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
                                <CheckIcon className={cn('mr-2 w-4 h-4', isSelected ? 'opacity-100' : 'opacity-0')} />
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
          </div>

          <div className="flex flex-col gap-2">
            <Label htmlFor="pontoReferenciaTexto" className="font-semibold text-gray-800">
              Micro-região
            </Label>
            <Input
              id="pontoReferenciaTexto"
              value={anuncio.pontoReferenciaTexto ?? ''}
              maxLength={120}
              onChange={(e) => handleChange('pontoReferenciaTexto', e.target.value)}
              placeholder="Ex: Próx ao Bretas, Região da 44"
              className="py-5"
            />
          </div>

          {/* Locais */}
          <div className="flex flex-col gap-3">
            <Label className="font-semibold text-gray-800">Local de Atendimento</Label>
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
              {LOCAIS.map(({ value, label }) => (
                <label key={value} className="flex items-center gap-2 cursor-pointer text-gray-700 hover:text-pink-600 transition">
                  <input
                    type="checkbox"
                    checked={anuncio.locaisAtendimento.includes(value)}
                    onChange={() => handleCheckboxChange('locaisAtendimento', value)}
                    className="w-3 h-3 accent-pink-500 cursor-pointer"
                  />
                  {label}
                </label>
              ))}
            </div>
          </div>

          {/* Serviços */}
          <div className="flex flex-col gap-3 mt-8">
            <Label className="font-semibold text-gray-800">Serviços</Label>
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
              {SERVICOS.map(({ value, label }) => (
                <label key={value} className="flex items-center gap-2 cursor-pointer text-gray-700 hover:text-pink-600 transition">
                  <input
                    type="checkbox"
                    checked={anuncio.servicos.includes(value)}
                    onChange={() => handleCheckboxChange('servicos', value)}
                    className="w-3 h-3 accent-pink-500 cursor-pointer"
                  />
                  {label}
                </label>
              ))}
            </div>
          </div>

          {/* Link */}
          <div>
            <Label htmlFor="link" className="font-semibold text-gray-800">
              Venda de conteúdo (link)
            </Label>
            <Input
              id="link"
              value={anuncio.linkConteudo ?? ''}
              onChange={(e) => handleChange('linkConteudo', e.target.value)}
              placeholder="Ex: https://..."
              className="py-5 w-full"
            />
          </div>

          {/* Descrição */}
          <div className="flex flex-col gap-2">
            <Label htmlFor="descricao" className="font-semibold text-gray-800">
              Descrição
            </Label>
            <Textarea
              id="descricao"
              value={anuncio.descricao ?? ''}
              onChange={(e) => handleChange('descricao', e.target.value)}
              rows={6}
              className="py-3 h-[300px]"
            />
          </div>

          {/* Galeria de fotos */}
          <div className="flex flex-col gap-3">
            <Label className="font-semibold text-gray-800">Galeria de fotos</Label>

            <div className="border border-dashed border-gray-300 rounded-lg p-6 flex flex-col items-center justify-center text-center space-y-3">
              <PlayIcon className="w-10 h-10 text-gray-400" />
              <p className="text-sm text-gray-500">Atualize suas fotos — adicione novas ou mantenha as existentes.</p>

              <Input type="file" multiple accept="image/*" onChange={handleImageUpload} className="cursor-pointer" />

              {anuncio.fotos?.length > 0 && (
                <div className="grid grid-cols-3 sm:grid-cols-4 gap-3 mt-5 w-full">
                  {anuncio.fotos.map((img, i) => (
                    <div key={i} className="relative aspect-square rounded-lg overflow-hidden border border-gray-200">
                      <img src={img} alt={`foto-${i}`} className="h-full w-full object-cover" loading="lazy" />
                    </div>
                  ))}
                </div>
              )}

              {previewUrls.length > 0 && (
                <div className="grid grid-cols-3 sm:grid-cols-4 gap-3 mt-5 w-full">
                  {previewUrls.map((previewUrl, i) => (
                    <div key={i} className="relative aspect-square rounded-lg overflow-hidden border border-gray-200">
                      <img src={previewUrl} alt={`nova-${i}`} className="h-full w-full object-cover" loading="lazy" />
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* Vídeos */}
          {anuncio.videosAnuncio && anuncio.videosAnuncio.length > 0 && (
            <div className="flex flex-col gap-3">
              <Label className="font-semibold text-gray-800">Vídeos do anúncio</Label>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                {anuncio.videosAnuncio.map((src, i) => (
                  <div key={i} className="rounded-lg overflow-hidden border border-gray-200">
                    <video src={src} controls className="w-full h-[260px] object-cover" />
                  </div>
                ))}
              </div>
            </div>
          )}

          {!embedded && (
            <div className="flex flex-col gap-2">
              <Label className="font-semibold text-gray-800">Localização no mapa</Label>

              <div className="relative w-full h-[340px] rounded-lg overflow-hidden border border-gray-200 mt-2">
                <iframe
                  src={`https://www.google.com/maps?q=${encodeURIComponent(localizacaoTexto || '')}&output=embed`}
                  width="100%"
                  height="100%"
                  loading="lazy"
                  className="rounded-lg"
                  allowFullScreen
                />

                <div className="absolute top-3 left-3 bg-white/85 backdrop-blur-sm px-3 py-1.5 rounded-md flex items-center text-sm text-gray-700">
                  <MapPinIcon className="w-4 h-4 text-pink-600 mr-1" />
                  {localizacaoTexto || '—'}
                </div>
              </div>
            </div>
          )}

          {/* Botão */}
          <div className="pt-6 border-t border-gray-100">
            <Button
              type="submit"
              disabled={saving}
              className="w-full py-6 bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-semibold text-base disabled:opacity-60"
            >
              {saving ? 'Salvando...' : 'Salvar Alterações'}
            </Button>
          </div>
        </form>
      </Card>
    </div>
  )
}
