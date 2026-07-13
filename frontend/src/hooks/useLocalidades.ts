'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { toast } from 'sonner'
import {
  descobrirLocalidadesPublicas,
  type PublicCatalogDiscovery,
} from '@/lib/public-catalog-api'

export type EstadoItem = { id: string; nome: string; uf: string }
export type CidadeItem = { id: string; nome: string }
export type BairroItem = { id: string; nome: string }

function publicErrorMessage(level: 'estados' | 'cidades' | 'bairros') {
  return `Não foi possível carregar ${level}. Tente novamente.`
}

export function useLocalidades() {
  const [estados, setEstados] = useState<EstadoItem[]>([])
  const [cidades, setCidades] = useState<CidadeItem[]>([])
  const [bairros, setBairros] = useState<BairroItem[]>([])
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [loadingEstados, setLoadingEstados] = useState(false)
  const [loadingCidades, setLoadingCidades] = useState(false)
  const [loadingBairros, setLoadingBairros] = useState(false)
  const catalogoRef = useRef<PublicCatalogDiscovery | null>(null)
  const estadoSelecionadoRef = useRef('')

  const carregarCatalogo = useCallback(async () => {
    if (catalogoRef.current) return catalogoRef.current
    const catalogo = await descobrirLocalidadesPublicas()
    catalogoRef.current = catalogo
    return catalogo
  }, [])

  const fail = useCallback((level: 'estados' | 'cidades' | 'bairros') => {
    const message = publicErrorMessage(level)
    setErrorMessage(message)
    toast.error(message)
  }, [])

  const loadEstados = useCallback(async () => {
    setLoadingEstados(true)
    setErrorMessage(null)
    try {
      const catalogo = await carregarCatalogo()
      setEstados(catalogo.estados.map((item) => ({ id: item.uf, nome: item.nome, uf: item.uf })))
    } catch {
      fail('estados')
    } finally {
      setLoadingEstados(false)
    }
  }, [carregarCatalogo, fail])

  const loadCidades = useCallback(async (estadoUf: string) => {
    if (!estadoUf) return
    estadoSelecionadoRef.current = estadoUf
    setLoadingCidades(true)
    setErrorMessage(null)
    try {
      const catalogo = await carregarCatalogo()
      const estado = catalogo.estados.find((item) => item.uf === estadoUf)
      if (!estado) throw new Error('Estado ausente no catálogo público.')
      setCidades(estado.cidades.map((item) => ({ id: item.slug, nome: item.nome })))
    } catch {
      fail('cidades')
    } finally {
      setLoadingCidades(false)
    }
  }, [carregarCatalogo, fail])

  const loadBairros = useCallback(async (cidadeSlug: string) => {
    if (!cidadeSlug) return
    setLoadingBairros(true)
    setErrorMessage(null)
    try {
      const catalogo = await carregarCatalogo()
      const estado = catalogo.estados.find((item) => item.uf === estadoSelecionadoRef.current)
      const cidade = estado?.cidades.find((item) => item.slug === cidadeSlug)
      if (!cidade) throw new Error('Cidade ausente no catálogo público.')
      setBairros(cidade.bairros.map((item) => ({ id: item.slug, nome: item.nome })))
    } catch {
      fail('bairros')
    } finally {
      setLoadingBairros(false)
    }
  }, [carregarCatalogo, fail])

  useEffect(() => {
    void loadEstados()
  }, [loadEstados])

  return {
    estados,
    cidades,
    bairros,
    errorMessage,
    setCidades,
    setBairros,
    loadingEstados,
    loadingCidades,
    loadingBairros,
    loadCidades,
    loadBairros,
  }
}
