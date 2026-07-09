// src/hooks/useCidadesBR.ts
'use client'

import { useEffect, useState } from 'react'

export type CidadeBR = {
  id: string
  ibgeId: string
  name: string
}

export function useCidadesBR(uf: string | null) {
  const [cidades, setCidades] = useState<CidadeBR[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!uf || uf.length !== 2) {
      setCidades([])
      return
    }
    const fetcher = async () => {
      try {
        setLoading(true)
        setError(null)
        const res = await fetch(`https://brasilapi.com.br/api/ibge/municipios/v1/${uf.toUpperCase()}`)
        if (!res.ok) throw new Error(`Erro ao buscar cidades (${res.status})`)
        const data = await res.json()
        const arr: CidadeBR[] = data.map((c: any) => ({
          id: String(c.codigo_ibge),
          ibgeId: String(c.codigo_ibge),
          name: c.nome,
        }))
        setCidades(arr)
      } catch (e: any) {
        setError(e?.message || 'Falha ao buscar cidades')
        setCidades([])
      } finally {
        setLoading(false)
      }
    }
    fetcher()
  }, [uf])

  return { cidades, loading, error }
}
