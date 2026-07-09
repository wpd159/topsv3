const TOKENS_MOJIBAKE = [
  "\u00c3",
  "\u00c2",
  "\u00e2\u20ac",
  "\u00ef\u00bf\u00bd",
  "\ufffd",
  "\u00c3\u0192",
  "\u00c3\u201a",
  "\u00c3\u00a2",
] as const

const PADRAO_MOJIBAKE = new RegExp(
  TOKENS_MOJIBAKE.map((token) => token.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")).join("|"),
  "u"
)

function decodificarLatin1ComoUtf8(value: string) {
  const bytes = Uint8Array.from([...value].map((char) => char.charCodeAt(0) & 0xff))
  return new TextDecoder("utf-8", { fatal: false }).decode(bytes)
}

function pontuarMojibake(value: string) {
  return TOKENS_MOJIBAKE.reduce((score, token) => {
    const ocorrencias = value.split(token).length - 1
    return score + Math.max(ocorrencias, 0)
  }, 0)
}

export function corrigirTextoCorrompido(value?: string | null) {
  if (!value) return ""
  if (!PADRAO_MOJIBAKE.test(value)) return value

  let atual = value
  let pontuacaoAtual = pontuarMojibake(atual)

  for (let tentativa = 0; tentativa < 3; tentativa += 1) {
    if (!PADRAO_MOJIBAKE.test(atual) || pontuacaoAtual === 0) break

    try {
      const decodificado = decodificarLatin1ComoUtf8(atual)
      const pontuacaoDecodificada = pontuarMojibake(decodificado)

      if (
        !decodificado ||
        decodificado.includes("\u0000") ||
        decodificado === atual ||
        pontuacaoDecodificada >= pontuacaoAtual
      ) {
        break
      }

      atual = decodificado
      pontuacaoAtual = pontuacaoDecodificada
    } catch {
      break
    }
  }

  return atual
}

export function corrigirListaTextos<T extends string | null | undefined>(items: T[]) {
  return items.map((item) => corrigirTextoCorrompido(item))
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return Object.prototype.toString.call(value) === "[object Object]"
}

export function corrigirEstruturaTexto<T>(value: T): T {
  if (typeof value === "string") {
    return corrigirTextoCorrompido(value) as T
  }

  if (Array.isArray(value)) {
    return value.map((item) => corrigirEstruturaTexto(item)) as T
  }

  if (!value || typeof value !== "object") {
    return value
  }

  if (value instanceof Date) {
    return value
  }

  if (typeof File !== "undefined" && value instanceof File) {
    return value
  }

  if (typeof Blob !== "undefined" && value instanceof Blob) {
    return value
  }

  if (!isPlainObject(value)) {
    return value
  }

  return Object.fromEntries(
    Object.entries(value).map(([key, nestedValue]) => [key, corrigirEstruturaTexto(nestedValue)])
  ) as T
}
