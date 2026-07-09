'use client'

import type React from 'react'
import { useEffect, useMemo, useState } from 'react'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Label } from '@/components/ui/label'
import { toast } from 'sonner'
import { useAuth } from '@/context/AuthContext'
import { formatPhone } from '@/utils/formatter'
import { cn } from '@/lib/utils'

import { CheckIcon, XMarkIcon, PencilIcon, ChevronUpDownIcon } from '@heroicons/react/24/solid'

import { Popover, PopoverTrigger, PopoverContent } from '@/components/ui/popover'
import {
  Command,
  CommandInput,
  CommandItem,
  CommandList,
  CommandEmpty,
  CommandGroup,
} from '@/components/ui/command'

type Dados = {
  nome: string
  email: string
  telefone: string
  descricao: string

  // ✅ ids vindos do backend (UsuarioResponseDTO)
  estadoId?: number | null
  cidadeId?: number | null
  bairroId?: number | null

  // legado: você chama de "cidade" no form, mas na prática é o bairro
  cidade?: string
}

type EstadoItem = { id: number; nome: string; uf?: string }
type CidadeItem = { id: number; nome: string }
type BairroItem = { id: number; nome: string }

function onlyDigits(v: string) {
  return (v ?? '').replace(/\D/g, '')
}

export default function InformacoesPessoaisCard({
  dados,
  setDados,
}: {
  dados: Dados
  setDados: (d: any) => void
}) {
  const { usuario, refresh } = useAuth()
  const API_URL = process.env.NEXT_PUBLIC_API_URL

  const [editando, setEditando] = useState(false)
  const [carregando, setCarregando] = useState(false)

  // ✅ só manda localização no PUT se o user mexer nela
  const [locationDirty, setLocationDirty] = useState(false)

  const [original, setOriginal] = useState<Dados>({
    ...dados,
    telefone: onlyDigits(dados.telefone || ''),
  })

  const [form, setForm] = useState<Dados>({
    ...dados,
    telefone: formatPhone(dados.telefone || ''),
  })

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

  // ---------- sincroniza dados -> form ----------
  useEffect(() => {
    setForm({ ...dados, telefone: formatPhone(dados.telefone || '') })
    setOriginal({ ...dados, telefone: onlyDigits(dados.telefone || '') })

    // ✅ reset "dirty" quando chegou dado novo do backend
    setLocationDirty(false)
  }, [dados])

  // ---------- carrega estados ----------
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

  // ---------- hidrata selectedEstado quando estados chegam ----------
  useEffect(() => {
    if (locationDirty) return
    if (!dados.estadoId || estados.length === 0) return
    if (selectedEstado?.id === dados.estadoId) return

    const e = estados.find((x) => x.id === dados.estadoId)
    if (e) setSelectedEstado(e)
  }, [dados.estadoId, estados, selectedEstado?.id, locationDirty])

  // ---------- quando estado muda: zera cidade/bairro e carrega cidades ----------
  useEffect(() => {
    setCidades([])
    setBairros([])
    setSelectedCidade(null)
    setSelectedBairro(null)

    // ✅ não apaga display fora do modo edição
    if (editando) setForm((p) => ({ ...p, cidade: '' }))

    if (!selectedEstado || !API_URL) return

    const load = async () => {
      try {
        setLoadingCidades(true)
        const res = await fetch(
          `${API_URL}/localidades/estados/${selectedEstado.id}/cidades?ativas=true`,
          { credentials: 'include', cache: 'no-store' }
        )
        if (!res.ok) throw new Error()
        const data = (await res.json()) as CidadeItem[]
        setCidades(Array.isArray(data) ? data : [])
      } catch {
        setCidades([])
      } finally {
        setLoadingCidades(false)
      }
    }

    load()
  }, [selectedEstado?.id, API_URL, editando])

  // ---------- hidrata selectedCidade quando cidades chegam ----------
  useEffect(() => {
    if (locationDirty) return
    if (!dados.cidadeId || cidades.length === 0) return
    if (selectedCidade?.id === dados.cidadeId) return

    const c = cidades.find((x) => x.id === dados.cidadeId)
    if (c) setSelectedCidade(c)
  }, [dados.cidadeId, cidades, selectedCidade?.id, locationDirty])

  // ---------- quando cidade muda: zera bairro e carrega bairros ----------
  useEffect(() => {
    setBairros([])
    setSelectedBairro(null)

    // ✅ não apaga display fora do modo edição
    if (editando) setForm((p) => ({ ...p, cidade: '' }))

    if (!selectedCidade || !API_URL) return

    const load = async () => {
      try {
        setLoadingBairros(true)
        const res = await fetch(
          `${API_URL}/localidades/cidades/${selectedCidade.id}/bairros?ativas=true`,
          { credentials: 'include', cache: 'no-store' }
        )
        if (!res.ok) throw new Error()
        const data = (await res.json()) as BairroItem[]
        setBairros(Array.isArray(data) ? data : [])
      } catch {
        setBairros([])
      } finally {
        setLoadingBairros(false)
      }
    }

    load()
  }, [selectedCidade?.id, API_URL, editando])

  // ---------- hidrata selectedBairro quando bairros chegam ----------
  useEffect(() => {
    if (locationDirty) return
    if (!dados.bairroId || bairros.length === 0) return
    if (selectedBairro?.id === dados.bairroId) return

    const b = bairros.find((x) => x.id === dados.bairroId)
    if (b) {
      setSelectedBairro(b)
      setForm((p) => ({ ...p, cidade: b.nome })) // legado: mostra bairro no form
    }
  }, [dados.bairroId, bairros, selectedBairro?.id, locationDirty])

  const controlBase =
    'h-11 w-full rounded-full border bg-gray-100 text-gray-700 placeholder:text-gray-400'
  const controlBorder =
    'border-gray-300 focus-visible:ring-0 focus-visible:outline-none focus-visible:border-gray-400'
  const controlPadding = 'pl-4 pr-10 text-sm'
  const inputClass = cn(controlBase, controlBorder, controlPadding, !editando && 'cursor-default')
  const comboboxClass = cn(
    'min-w-0 justify-between',
    controlBase,
    controlBorder,
    controlPadding,
    !editando && 'cursor-default'
  )

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const { name, value } = e.target
    if (name === 'telefone') {
      setForm((prev) => ({ ...prev, telefone: formatPhone(value) }))
      return
    }
    setForm((prev) => ({ ...prev, [name]: value }))
  }

  const buildPayloadSomenteMudancas = () => {
    const payload: Record<string, any> = {}

    if ((form.nome ?? '').trim() && form.nome.trim() !== (original.nome ?? '').trim()) {
      payload.username = form.nome.trim()
    }

    if ((form.descricao ?? '') !== (original.descricao ?? '')) {
      payload.descricaoPerfil = form.descricao
    }

    const novoTelefone = onlyDigits(form.telefone || '')
    if (novoTelefone && novoTelefone !== (original.telefone ?? '')) {
      payload.telefone = novoTelefone
    }

    // ✅ só mexe em localização se o user realmente alterou os selects
    if (locationDirty) {
      if (!selectedEstado?.id || !selectedCidade?.id || !selectedBairro?.id) {
        payload.__invalidLocalizacao = true
        return payload
      }

      payload.estadoId = selectedEstado.id
      payload.cidadeId = selectedCidade.id
      payload.bairroId = selectedBairro.id
      payload.localizacao = `${selectedCidade.nome} - ${selectedEstado.uf || ''} • ${selectedBairro.nome}`
    }

    return payload
  }

  const handleSalvar = async () => {
    if (!usuario?.email) {
      toast.error('Usuário não autenticado')
      return
    }

    const payload = buildPayloadSomenteMudancas()

    if (payload.__invalidLocalizacao) {
      toast.warning('Selecione Estado, Cidade e Bairro pela lista.')
      return
    }
    delete payload.__invalidLocalizacao

    if (Object.keys(payload).length === 0) {
      toast.message('Nada mudou. Salvar pra quê? 😅')
      setEditando(false)
      return
    }

    try {
      setCarregando(true)

      const res = await fetch(`${API_URL}/usuarios/${usuario.email}/editar`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify(payload),
      })

      if (!res.ok) {
        const msg = await res.text().catch(() => '')
        throw new Error(msg || 'Erro ao salvar alterações')
      }

      setDados((prev: any) => ({
        ...prev,
        nome: payload.username ?? prev.nome,
        descricao: payload.descricaoPerfil ?? prev.descricao,
        telefone: payload.telefone ? formatPhone(payload.telefone) : prev.telefone,

        // ✅ mantém ids no estado local
        estadoId: payload.estadoId ?? prev.estadoId,
        cidadeId: payload.cidadeId ?? prev.cidadeId,
        bairroId: payload.bairroId ?? prev.bairroId,

        // legado: você exibe "cidade" como bairro
        cidade: selectedBairro?.nome ?? prev.cidade,
      }))

      await refresh()
      toast.success('Informações atualizadas com sucesso!')
      setEditando(false)
      setLocationDirty(false)
    } catch (err: any) {
      toast.error(err?.message || 'Falha ao atualizar informações')
    } finally {
      setCarregando(false)
    }
  }

  const handleCancelar = () => {
    // volta inputs
    setForm({ ...dados, telefone: formatPhone(dados.telefone || '') })

    // fecha popovers
    setOpenEstado(false)
    setOpenCidade(false)
    setOpenBairro(false)

    // ✅ deixa a hidratação repor o selected... automaticamente
    setLocationDirty(false)
    setSelectedEstado(null)
    setSelectedCidade(null)
    setSelectedBairro(null)

    setCidades([])
    setBairros([])

    setEditando(false)
  }

  const labels: Record<'nome' | 'email' | 'telefone' | 'descricao', string> = {
    nome: 'Nome de Usuário (NOME VISÍVEL)',
    email: 'Email',
    telefone: 'Telefone',
    descricao: 'Descrição do perfil',
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

  return (
    <Card className="border border-gray-300/50">
      <CardContent className="p-6 space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-3 sm:flex-nowrap">
          <h2 className="text-base font-semibold text-gray-900">Informações pessoais</h2>

          {!editando ? (
            <Button
              variant="outline"
              size="sm"
              className="text-gray-700 border-gray-300 hover:bg-gray-100 w-full sm:w-auto"
              onClick={() => setEditando(true)}
            >
              <PencilIcon className="w-4 h-4 mr-2" />
              Editar
            </Button>
          ) : (
            <div className="flex flex-wrap gap-2 w-full sm:w-auto justify-end">
              <Button
                size="sm"
                className="bg-green-600 hover:bg-green-700 text-white w-full sm:w-auto"
                onClick={handleSalvar}
                disabled={carregando}
              >
                <CheckIcon className="w-4 h-4 mr-2" />
                {carregando ? 'Salvando...' : 'Salvar'}
              </Button>
              <Button
                size="sm"
                variant="outline"
                className="border-gray-300 text-gray-700 hover:bg-gray-100 w-full sm:w-auto"
                onClick={handleCancelar}
              >
                <XMarkIcon className="w-4 h-4 mr-2" />
                Cancelar
              </Button>
            </div>
          )}
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
          <div>
            <Label className="text-gray-700">{labels.nome}</Label>
            <Input
              name="nome"
              value={form.nome || ''}
              onChange={handleChange}
              disabled={!editando}
              className={inputClass}
            />
          </div>

          <div>
            <Label className="text-gray-700">{labels.email}</Label>
            <Input
              name="email"
              value={form.email || ''}
              onChange={handleChange}
              disabled
              className={cn(inputClass, 'opacity-80')}
            />
          </div>

          <div>
            <Label className="text-gray-700">{labels.telefone}</Label>
            <Input
              name="telefone"
              value={form.telefone || ''}
              onChange={handleChange}
              disabled={!editando}
              className={inputClass}
              placeholder="(11) 99999-9999"
            />
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-5">
          {/* Estado */}
          <div>
            <Label className="text-gray-700">Estado</Label>
            <Popover open={openEstado} onOpenChange={setOpenEstado}>
              <PopoverTrigger asChild>
                <Button
                  variant="outline"
                  role="combobox"
                  disabled={!editando || loadingEstados}
                  className={cn(comboboxClass, 'mt-1')}
                >
                  <span className={cn('min-w-0 flex-1 truncate', !selectedEstado && 'text-gray-400')}>
                    {labelEstado}
                  </span>
                  <ChevronUpDownIcon className="ml-2 h-4 w-4 shrink-0 opacity-60" />
                </Button>
              </PopoverTrigger>
              <PopoverContent className="p-0 w-[var(--radix-popover-trigger-width)]">
                <Command>
                  <CommandInput placeholder="Buscar estado..." disabled={!editando} />
                  <CommandList>
                    <CommandEmpty>
                      {loadingEstados ? 'Carregando...' : 'Nenhum estado encontrado.'}
                    </CommandEmpty>
                    <CommandGroup>
                      {estados.map((e) => {
                        const isSelected = selectedEstado?.id === e.id
                        return (
                          <CommandItem
                            key={e.id}
                            onSelect={() => {
                              setSelectedEstado(e)
                              setSelectedCidade(null)
                              setSelectedBairro(null)
                              setForm((p) => ({ ...p, cidade: '' })) // bairro legado
                              setOpenEstado(false)
                              setLocationDirty(true)
                            }}
                          >
                            <CheckIcon
                              className={cn('mr-2 w-4 h-4', isSelected ? 'opacity-100' : 'opacity-0')}
                            />
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
          <div>
            <Label className="text-gray-700">Cidade</Label>
            <Popover open={openCidade} onOpenChange={setOpenCidade}>
              <PopoverTrigger asChild>
                <Button
                  variant="outline"
                  role="combobox"
                  disabled={!editando || !selectedEstado || loadingCidades}
                  className={cn(comboboxClass, 'mt-1')}
                >
                  <span className={cn('min-w-0 flex-1 truncate', !selectedCidade && 'text-gray-400')}>
                    {labelCidade}
                  </span>
                  <ChevronUpDownIcon className="ml-2 h-4 w-4 shrink-0 opacity-60" />
                </Button>
              </PopoverTrigger>
              <PopoverContent className="p-0 w-[var(--radix-popover-trigger-width)]">
                <Command>
                  <CommandInput
                    placeholder={!selectedEstado ? 'Selecione o estado' : 'Buscar cidade...'}
                    disabled={!editando || !selectedEstado}
                  />
                  <CommandList>
                    <CommandEmpty>
                      {!selectedEstado
                        ? 'Selecione o estado primeiro.'
                        : loadingCidades
                          ? 'Carregando...'
                          : 'Nenhuma cidade encontrada.'}
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
                                setSelectedBairro(null)
                                setForm((p) => ({ ...p, cidade: '' })) // bairro legado
                                setOpenCidade(false)
                                setLocationDirty(true)
                              }}
                            >
                              <CheckIcon
                                className={cn('mr-2 w-4 h-4', isSelected ? 'opacity-100' : 'opacity-0')}
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

          {/* Bairro */}
          <div>
            <Label className="text-gray-700">Bairro</Label>
            <Popover open={openBairro} onOpenChange={setOpenBairro}>
              <PopoverTrigger asChild>
                <Button
                  variant="outline"
                  role="combobox"
                  disabled={!editando || !selectedCidade || loadingBairros}
                  className={cn(comboboxClass, 'mt-1')}
                >
                  <span className={cn('min-w-0 flex-1 truncate', !selectedBairro && 'text-gray-400')}>
                    {labelBairro}
                  </span>
                  <ChevronUpDownIcon className="ml-2 h-4 w-4 shrink-0 opacity-60" />
                </Button>
              </PopoverTrigger>
              <PopoverContent className="p-0 w-[var(--radix-popover-trigger-width)]">
                <Command>
                  <CommandInput
                    placeholder={!selectedCidade ? 'Selecione a cidade' : 'Buscar bairro...'}
                    disabled={!editando || !selectedCidade}
                  />
                  <CommandList>
                    <CommandEmpty>
                      {!selectedCidade
                        ? 'Selecione a cidade primeiro.'
                        : loadingBairros
                          ? 'Carregando...'
                          : 'Nenhum bairro encontrado.'}
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
                                setForm((p) => ({ ...p, cidade: b.nome })) // legado
                                setOpenBairro(false)
                                setLocationDirty(true)
                              }}
                            >
                              <CheckIcon
                                className={cn('mr-2 w-4 h-4', isSelected ? 'opacity-100' : 'opacity-0')}
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
        </div>

        <div>
          <Label className="text-gray-700">{labels.descricao}</Label>
          <Textarea
            name="descricao"
            value={form.descricao || ''}
            onChange={handleChange}
            disabled={!editando}
            rows={5}
            placeholder="Fale um pouco sobre você, seus diferenciais e seu estilo de atendimento."
            className={cn(
              'h-24',
              editando
                ? 'bg-gray-100 border border-gray-300 rounded-2xl'
                : 'bg-gray-100 border border-gray-300 rounded-3xl cursor-default'
            )}
          />
        </div>
      </CardContent>
    </Card>
  )
}
