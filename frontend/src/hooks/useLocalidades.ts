'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { toast } from 'sonner'

type BairroCatalogo = { nome: string; slug: string }
type CidadeCatalogo = { nome: string; slug: string; bairros: BairroCatalogo[] }
type EstadoCatalogo = { uf: string; nome: string; cidades: CidadeCatalogo[] }
type LocalidadesCatalogo = { estados: EstadoCatalogo[] }

export type EstadoItem = { id: string | number; nome: string; uf?: string }
export type CidadeItem = { id: string | number; nome: string }
export type BairroItem = { id: string | number; nome: string }

export function useLocalidades(API: string, mode: 'create' | 'edit' = 'create') {
  const [estados, setEstados] = useState<EstadoItem[]>([])
  const [cidades, setCidades] = useState<CidadeItem[]>([])
  const [bairros, setBairros] = useState<BairroItem[]>([])

  const [loadingEstados, setLoadingEstados] = useState(false)
  const [loadingCidades, setLoadingCidades] = useState(false)
  const [loadingBairros, setLoadingBairros] = useState(false)
  const catalogoRef = useRef<LocalidadesCatalogo | null>(null)
  const estadoSelecionadoRef = useRef<string>('')
  const apiBase = (API || '/api/public').replace(/\/$/, '')

  const carregarCatalogo = useCallback(async () => {
    if (catalogoRef.current) return catalogoRef.current
    const res = await fetch(`${apiBase}/localidades`, {
      credentials: 'include',
      cache: 'no-store',
    })
    if (!res.ok) throw new Error(await res.text())
    const data = (await res.json()) as LocalidadesCatalogo
    const catalogo = { estados: Array.isArray(data?.estados) ? data.estados : [] }
    catalogoRef.current = catalogo
    return catalogo
  }, [apiBase])

  const loadEstados = useCallback(async () => {
    setLoadingEstados(true)
    try {
      if (mode === 'create') {
        const res = await fetch(`${API}/localidades/estados`, {
          credentials: 'include',
          cache: 'no-store',
        })
        if (!res.ok) throw new Error(await res.text())
        const data = (await res.json()) as EstadoItem[]
        setEstados(Array.isArray(data) ? data : [])
        return
      }
      const catalogo = await carregarCatalogo()
      setEstados(catalogo.estados.map((item) => ({ id: item.uf, nome: item.nome, uf: item.uf })))
    } catch {
      toast.error('Falha ao carregar estados.')
      setEstados([])
    } finally {
      setLoadingEstados(false)
    }
  }, [API, carregarCatalogo, mode])

  const loadCidades = useCallback(async (estadoIdStr: string) => {
    if (!estadoIdStr) return
    estadoSelecionadoRef.current = estadoIdStr
    setLoadingCidades(true)
    try {
      if (mode === 'create') {
        const res = await fetch(
          `${API}/localidades/estados/${encodeURIComponent(estadoIdStr)}/cidades?ativas=true`,
          { credentials: 'include', cache: 'no-store' }
        )
        if (!res.ok) throw new Error(await res.text())
        const data = (await res.json()) as CidadeItem[]
        setCidades(Array.isArray(data) ? data : [])
        return
      }
      const catalogo = await carregarCatalogo()
      const estado = catalogo.estados.find((item) => item.uf === estadoIdStr)
      setCidades((estado?.cidades ?? []).map((item) => ({ id: item.slug, nome: item.nome })))
    } catch {
      toast.error('Falha ao carregar cidades.')
      setCidades([])
    } finally {
      setLoadingCidades(false)
    }
  }, [API, carregarCatalogo, mode])

  const loadBairros = useCallback(async (cidadeIdStr: string) => {
    if (!cidadeIdStr) return
    setLoadingBairros(true)
    try {
      if (mode === 'create') {
        const res = await fetch(
          `${API}/localidades/cidades/${encodeURIComponent(cidadeIdStr)}/bairros?ativas=true`,
          { credentials: 'include', cache: 'no-store' }
        )
        if (!res.ok) throw new Error(await res.text())
        const data = (await res.json()) as BairroItem[]
        setBairros(Array.isArray(data) ? data : [])
        return
      }
      const catalogo = await carregarCatalogo()
      const estado = catalogo.estados.find((item) => item.uf === estadoSelecionadoRef.current)
      const cidade = estado?.cidades.find((item) => item.slug === cidadeIdStr)
      setBairros((cidade?.bairros ?? []).map((item) => ({ id: item.slug, nome: item.nome })))
    } catch {
      toast.error('Falha ao carregar bairros.')
      setBairros([])
    } finally {
      setLoadingBairros(false)
    }
  }, [API, carregarCatalogo, mode])

  useEffect(() => {
    if (mode === 'create' && !API) return
    void loadEstados()
  }, [API, loadEstados, mode])

  return {
    estados, cidades, bairros,
    setCidades, setBairros,
    loadingEstados, loadingCidades, loadingBairros,
    loadCidades, loadBairros,
  }
}
