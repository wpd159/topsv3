'use client'

import { useEffect, useRef, useState } from 'react'
import { toast } from 'sonner'

export type EstadoItem = { id: number; nome: string; uf?: string }
export type CidadeItem = { id: number; nome: string }
export type BairroItem = { id: number; nome: string }

export function useLocalidades(API: string) {
  const [estados, setEstados] = useState<EstadoItem[]>([])
  const [cidades, setCidades] = useState<CidadeItem[]>([])
  const [bairros, setBairros] = useState<BairroItem[]>([])

  const [loadingEstados, setLoadingEstados] = useState(false)
  const [loadingCidades, setLoadingCidades] = useState(false)
  const [loadingBairros, setLoadingBairros] = useState(false)

  const loadEstados = async () => {
    setLoadingEstados(true)
    try {
      const res = await fetch(`${API}/localidades/estados`, { credentials: 'include', cache: 'no-store' })
      if (!res.ok) throw new Error(await res.text())
      const data = (await res.json()) as EstadoItem[]
      setEstados(Array.isArray(data) ? data : [])
    } catch {
      toast.error('Falha ao carregar estados.')
      setEstados([])
    } finally {
      setLoadingEstados(false)
    }
  }

  const loadCidades = async (estadoIdStr: string) => {
    if (!estadoIdStr) return
    setLoadingCidades(true)
    try {
      const res = await fetch(
        `${API}/localidades/estados/${encodeURIComponent(estadoIdStr)}/cidades?ativas=true`,
        { credentials: 'include', cache: 'no-store' }
      )
      if (!res.ok) throw new Error(await res.text())
      const data = (await res.json()) as CidadeItem[]
      setCidades(Array.isArray(data) ? data : [])
    } catch {
      toast.error('Falha ao carregar cidades.')
      setCidades([])
    } finally {
      setLoadingCidades(false)
    }
  }

  const loadBairros = async (cidadeIdStr: string) => {
    if (!cidadeIdStr) return
    setLoadingBairros(true)
    try {
      const res = await fetch(
        `${API}/localidades/cidades/${encodeURIComponent(cidadeIdStr)}/bairros?ativas=true`,
        { credentials: 'include', cache: 'no-store' }
      )
      if (!res.ok) throw new Error(await res.text())
      const data = (await res.json()) as BairroItem[]
      setBairros(Array.isArray(data) ? data : [])
    } catch {
      toast.error('Falha ao carregar bairros.')
      setBairros([])
    } finally {
      setLoadingBairros(false)
    }
  }

  useEffect(() => {
    if (!API) return
    loadEstados()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [API])

  return {
    estados, cidades, bairros,
    setCidades, setBairros,
    loadingEstados, loadingCidades, loadingBairros,
    loadCidades, loadBairros,
  }
}