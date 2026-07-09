function normalizeKey(s: string) {
  return s
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .trim()
    .toLowerCase()
}

const UF_BY_STATE: Record<string, string> = {
  acre: 'AC',
  alagoas: 'AL',
  amapa: 'AP',
  amazonas: 'AM',
  bahia: 'BA',
  ceara: 'CE',
  'distrito federal': 'DF',
  'espirito santo': 'ES',
  goias: 'GO',
  maranhao: 'MA',
  'mato grosso': 'MT',
  'mato grosso do sul': 'MS',
  'minas gerais': 'MG',
  para: 'PA',
  paraiba: 'PB',
  parana: 'PR',
  pernambuco: 'PE',
  piaui: 'PI',
  'rio de janeiro': 'RJ',
  'rio grande do norte': 'RN',
  'rio grande do sul': 'RS',
  rondonia: 'RO',
  roraima: 'RR',
  'santa catarina': 'SC',
  'sao paulo': 'SP',
  sergipe: 'SE',
  tocantins: 'TO',
}

function cleanStr(v: any) {
  if (typeof v !== 'string') return ''
  const s = v.trim()
  if (!s) return ''
  const low = s.toLowerCase()
  if (low === 'não informado' || low === 'nao informado') return ''
  return s
}

function toUf(v?: any) {
  const s = cleanStr(v)
  if (!s) return ''
  if (s.length === 2) return s.toUpperCase()
  const key = normalizeKey(s)
  return UF_BY_STATE[key] ?? ''
}

function parseFallbackLocalizacao(raw?: any) {
  const fallback = cleanStr(raw)
  if (!fallback) return { uf: '', cidade: '', bairro: '' }

  const parts = fallback.split(/\s*-\s*/).map((p) => cleanStr(p)).filter(Boolean)

  // "UF - Cidade - Bairro"
  if (parts.length >= 3) {
    const uf = toUf(parts[0])
    return { uf, cidade: parts[1], bairro: parts.slice(2).join(' - ') }
  }

  // "Cidade - Bairro" (muito comum no teu print)
  if (parts.length === 2) {
    const maybeUf = toUf(parts[0]) // se o 1º for um estado por extenso, vira UF
    if (maybeUf) return { uf: maybeUf, cidade: '', bairro: parts[1] }
    return { uf: '', cidade: parts[0], bairro: parts[1] }
  }

  // só 1 pedaço
  const maybeUf = toUf(parts[0])
  if (maybeUf) return { uf: maybeUf, cidade: '', bairro: '' }
  return { uf: '', cidade: parts[0], bairro: '' }
}

function abreviarBairro(bairro: string) {
  return bairro
    .replace(/\bJardim\b/gi, 'Jd.')
    .replace(/\bAvenida\b/gi, 'Av.')
    .replace(/\bConjunto\b/gi, 'Conj.')
    .replace(/\bVila\b/gi, 'Vl.')
}

export function buildLocalizacaoLabel(anuncio: any, mode: 'full' | 'compact' = 'full') {
  const uf =
    toUf(anuncio?.estadoUf) ||
    toUf(anuncio?.estado?.uf) ||
    toUf(anuncio?.cidade?.estado?.uf) ||
    toUf(anuncio?.estadoNome) ||
    ''

  const cidade =
    cleanStr(anuncio?.cidadeNome) ||
    cleanStr(anuncio?.cidade?.nome) ||
    cleanStr(anuncio?.cidade) ||
    ''

  const bairroRaw =
    cleanStr(anuncio?.bairroNome) ||
    cleanStr(anuncio?.bairro?.nome) ||
    cleanStr(anuncio?.bairro) ||
    ''

  const bairro = abreviarBairro(bairroRaw)

  // fallback antigo (localizacao string)
  const fb = parseFallbackLocalizacao(anuncio?.localizacao)

  const finalUf = uf || fb.uf
  const finalCidade = cidade || fb.cidade
  const finalBairro = bairro || fb.bairro
  const ponto = cleanStr(anuncio?.pontoReferenciaTexto)

  if (mode === 'compact') {
    // Compacto pra card: evita quebra feia tipo teu print
    // Ex: "SP • Jd. das Imbuias" ou "São Paulo • Centro"
    const left = finalUf || finalCidade || 'Não informado'
    const right = finalBairro ? ` • ${finalBairro}` : ''
    const base = `${left}${right}`.trim()
    return ponto ? `${base} · ${ponto}` : base
  }

  // Full (Sidebar / detalhe)
  const parts = [finalUf, finalCidade, finalBairro].filter(Boolean)
  const base = parts.length ? parts.join(' - ') : ''
  if (!base) return ponto || 'Localização não informada'
  return ponto ? `${base} · ${ponto}` : base
}

export function buildMapaQuery(anuncio: any) {
  // pro Google Maps é melhor "bairro, cidade - UF"
  const uf =
    toUf(anuncio?.estadoUf) ||
    toUf(anuncio?.cidade?.estado?.uf) ||
    parseFallbackLocalizacao(anuncio?.localizacao).uf

  const cidade =
    cleanStr(anuncio?.cidadeNome) ||
    cleanStr(anuncio?.cidade?.nome) ||
    parseFallbackLocalizacao(anuncio?.localizacao).cidade

  const bairro =
    cleanStr(anuncio?.bairroNome) ||
    cleanStr(anuncio?.bairro?.nome) ||
    parseFallbackLocalizacao(anuncio?.localizacao).bairro

  const parts = [bairro, cidade, uf].filter(Boolean)
  return parts.length ? parts.join(', ') : cleanStr(anuncio?.localizacao) || 'Brasil'
}
