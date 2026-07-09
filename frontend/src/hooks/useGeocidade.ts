// /src/hooks/useGeoCidade.ts
'use client'

import { useEffect, useRef, useState } from 'react'

type Geo = {
  uf: string | null
  cidade: string | null
  lat: number | null
  lon: number | null
}

const UF_BY_STATE: Record<string, string> = {
  'Acre': 'AC','Alagoas':'AL','Amapá':'AP','Amazonas':'AM','Bahia':'BA','Ceará':'CE','Distrito Federal':'DF',
  'Espírito Santo':'ES','Goiás':'GO','Maranhão':'MA','Mato Grosso':'MT','Mato Grosso do Sul':'MS',
  'Minas Gerais':'MG','Pará':'PA','Paraíba':'PB','Paraná':'PR','Pernambuco':'PE','Piauí':'PI',
  'Rio de Janeiro':'RJ','Rio Grande do Norte':'RN','Rio Grande do Sul':'RS','Rondônia':'RO','Roraima':'RR',
  'Santa Catarina':'SC','São Paulo':'SP','Sergipe':'SE','Tocantins':'TO'
}

function pickCity(addr: any): string | null {
  return addr?.city ?? addr?.town ?? addr?.municipality ?? addr?.village ?? null
}

export function useGeoCidade() {
  const [geo, setGeo] = useState<Geo>({ uf: null, cidade: null, lat: null, lon: null })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [permission, setPermission] = useState<'granted' | 'denied' | 'prompt' | 'unknown'>('unknown')
  const askedOnce = useRef(false)

  // Observa o status da permissão (quando suportado)
  useEffect(() => {
    let mounted = true
    if ('permissions' in navigator && (navigator as any).permissions?.query) {
      ;(navigator as any).permissions.query({ name: 'geolocation' as PermissionName })
        .then((p: PermissionStatus) => {
          if (!mounted) return
          const map = { 'granted': 'granted', 'denied': 'denied', 'prompt': 'prompt' } as const
          setPermission(map[p.state] ?? 'unknown')
          p.onchange = () => mounted && setPermission(map[p.state] ?? 'unknown')
        })
        .catch(() => setPermission('unknown'))
    }
    return () => { mounted = false }
  }, [])

  async function reverseGeocode(lat: number, lon: number) {
    const url = new URL('https://nominatim.openstreetmap.org/reverse')
    url.searchParams.set('format', 'jsonv2')
    url.searchParams.set('lat', String(lat))
    url.searchParams.set('lon', String(lon))
    url.searchParams.set('addressdetails', '1')
    url.searchParams.set('zoom', '10')

    const res = await fetch(url.toString(), { headers: { 'Accept-Language': 'pt-BR,pt;q=0.9' } })
    if (!res.ok) throw new Error(`Reverse geocode falhou (${res.status})`)
    const data = await res.json()
    const addr = data?.address ?? {}
    const stateName: string | undefined = addr.state
    const uf = stateName ? UF_BY_STATE[stateName] ?? null : null
    const cidade = pickCity(addr)
    setGeo({ uf, cidade, lat, lon })
  }

  // Chamada IMPERATIVA: dispara o prompt
  const requestGeo = () =>
    new Promise<void>((resolve) => {
      if (typeof window === 'undefined' || !('geolocation' in navigator)) {
        setPermission('denied')
        setError('Geolocalização indisponível neste navegador.')
        resolve()
        return
      }
      setLoading(true)
      setError(null)
      navigator.geolocation.getCurrentPosition(
        async (pos) => {
          setPermission('granted')
          try {
            await reverseGeocode(pos.coords.latitude, pos.coords.longitude)
          } catch (e: any) {
            setError(e?.message || 'Falha no reverse geocoding')
          } finally {
            setLoading(false)
            resolve()
          }
        },
        (err) => {
          setPermission('denied')
          setError(err?.message || 'Permissão negada ou indisponível')
          setLoading(false)
          resolve()
        },
        { enableHighAccuracy: false, timeout: 8000, maximumAge: 60_000 }
      )
    })

  // Opcional: tenta UMA vez automaticamente ao montar (sem travar UX)
  useEffect(() => {
    if (askedOnce.current) return
    askedOnce.current = true
    // Só tenta auto se ainda não sabemos UF/cidade
    if (geo.uf || geo.cidade) return
    // Não força prompt aqui; deixa para o botão.
    // Se quiser tentar automático e deixar o navegador decidir, descomente abaixo:
    // requestGeo()
  }, [geo.uf, geo.cidade])

  return { geo, loading, error, permission, requestGeo }
}
