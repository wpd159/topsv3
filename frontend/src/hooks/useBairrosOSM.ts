// src/hooks/useBairrosOSM.ts
'use client'

import { useEffect, useMemo, useState } from 'react'

export type BairroOSM = { id: string; name: string }

const UF_TO_ISO: Record<string, string> = {
  AC: 'BR-AC', AL: 'BR-AL', AP: 'BR-AP', AM: 'BR-AM', BA: 'BR-BA', CE: 'BR-CE',
  DF: 'BR-DF', ES: 'BR-ES', GO: 'BR-GO', MA: 'BR-MA', MT: 'BR-MT', MS: 'BR-MS',
  MG: 'BR-MG', PA: 'BR-PA', PB: 'BR-PB', PR: 'BR-PR', PE: 'BR-PE', PI: 'BR-PI',
  RJ: 'BR-RJ', RN: 'BR-RN', RS: 'BR-RS', RO: 'BR-RO', RR: 'BR-RR', SC: 'BR-SC',
  SP: 'BR-SP', SE: 'BR-SE', TO: 'BR-TO',
}

export function useBairrosOSM(cityName?: string | null, uf?: string | null) {
  const [bairros, setBairros] = useState<BairroOSM[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const iso = useMemo(() => (uf ? UF_TO_ISO[uf.toUpperCase()] : undefined), [uf])
  const namePattern = useMemo(() => (cityName ? diacriticRegex(cityName) : ''), [cityName])

  useEffect(() => {
    if (!cityName || !uf || !iso) {
      setBairros([])
      return
    }

    const run = async () => {
      setLoading(true)
      setError(null)
      try {
        // 1) tenta via nome dentro da área do estado (acento-insensível)
        let list = await queryBairrosByStateAndName(iso, namePattern)
        if (list.length === 0) {
          // 2) fallback: resolve a área exata via Nominatim e consulta por areaId
          const areaId = await resolveAreaIdByNominatim(cityName, uf)
          if (areaId) list = await queryBairrosByAreaId(areaId)
        }

        // ordena e seta
        list.sort((a, b) => a.name.localeCompare(b.name, 'pt-BR', { sensitivity: 'base' }))
        setBairros(list)
      } catch (e: any) {
        setError(e?.message || 'Falha ao buscar bairros')
        setBairros([])
      } finally {
        setLoading(false)
      }
    }

    run()
  }, [cityName, uf, iso, namePattern])

  return { bairros, loading, error }
}

/* ---------- Helpers ---------- */

function normalizeName(s: string) {
  return s.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim()
}

/** Constrói um regex Overpass acento-insensível para o nome da cidade */
function diacriticRegex(input: string) {
  // remove espaços duplicados e mantém palavras
  const cleaned = normalizeName(input).replace(/\s+/g, ' ').trim()
  // mapa mínimo de vogais com variações
  const map: Record<string, string> = {
    a: '[aàáâãä]',
    e: '[eèéêë]',
    i: '[iìíîï]',
    o: '[oòóôõö]',
    u: '[uùúûü]',
    c: '[cç]',
  }
  const pattern = cleaned
    .split('')
    .map((ch) => map[ch] || (/[a-z0-9]/.test(ch) ? ch : ch.replace(/[^ ]/g, '\\$&')))
    .join('')
    .replace(/ /g, '\\s+')
  // ^...$ para bater o nome completo, com flag i no Overpass
  return `^${pattern}$`
}

function escapeOverpass(s: string) {
  return String(s).replace(/["\\]/g, '\\$&')
}

async function overpass(query: string) {
  const res = await fetch('https://overpass-api.de/api/interpreter', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
      'User-Agent': 'topsdojob/1.0 (bairro-fetch)',
    },
    body: new URLSearchParams({ data: query }).toString(),
    cache: 'no-store',
  })
  if (!res.ok) throw new Error(`Overpass ${res.status}`)
  return res.json()
}

async function queryBairrosByStateAndName(iso: string, namePattern: string): Promise<BairroOSM[]> {
  const q = `
[out:json][timeout:30];
area["ISO3166-2"="${escapeOverpass(iso)}"]->.state;
(
  rel(area.state)["boundary"="administrative"]["admin_level"~"^(7|8|9)$"]["name"~"${namePattern}",i];
  rel(area.state)["boundary"="administrative"]["admin_level"~"^(7|8|9)$"]["name:pt"~"${namePattern}",i];
)->.city;
(
  node(area.city)["place"~"^(suburb|neighbourhood|quarter)$"];
  way(area.city)["place"~"^(suburb|neighbourhood|quarter)$"];
  relation(area.city)["place"~"^(suburb|neighbourhood|quarter)$"];
);
out tags center;
`
  const data = await overpass(q)
  return extractBairros(data)
}

/** Fallback: usa Nominatim para resolver a área exata da cidade (com UF) */
async function resolveAreaIdByNominatim(city: string, uf: string): Promise<number | null> {
  const url = new URL('https://nominatim.openstreetmap.org/search')
  url.searchParams.set('format', 'json')
  url.searchParams.set('addressdetails', '1')
  url.searchParams.set('limit', '1')
  url.searchParams.set('country', 'Brazil')
  url.searchParams.set('state', uf)
  url.searchParams.set('city', city)

  const res = await fetch(url.toString(), {
    headers: { 'User-Agent': 'topsdojob/1.0 (bairro-fetch)' },
    cache: 'no-store',
  })
  if (!res.ok) return null
  const arr: any[] = await res.json()
  const hit = arr?.[0]
  if (!hit?.osm_id || !hit?.osm_type) return null

  // areaId = 3600000000 + relationId | 2400000000 + wayId | 1200000000 + nodeId
  const base =
    hit.osm_type === 'relation' ? 3600000000 :
    hit.osm_type === 'way' ? 2400000000 :
    hit.osm_type === 'node' ? 1200000000 : 0

  return base ? base + Number(hit.osm_id) : null
}

async function queryBairrosByAreaId(areaId: number): Promise<BairroOSM[]> {
  const q = `
[out:json][timeout:30];
area(${areaId})->.city;
(
  node(area.city)["place"~"^(suburb|neighbourhood|quarter)$"];
  way(area.city)["place"~"^(suburb|neighbourhood|quarter)$"];
  relation(area.city)["place"~"^(suburb|neighbourhood|quarter)$"];
);
out tags center;
`
  const data = await overpass(q)
  return extractBairros(data)
}

function extractBairros(data: any): BairroOSM[] {
  const names = new Set<string>()
  const list: BairroOSM[] = []
  for (const el of data?.elements ?? []) {
    const name: string | undefined = el?.tags?.name
    if (!name) continue
    const key = normalizeName(name)
    if (key && !names.has(key)) {
      names.add(key)
      list.push({ id: String(el.id), name: name.trim() })
    }
  }
  return list
}
