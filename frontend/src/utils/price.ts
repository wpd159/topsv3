export function parseBRLToNumberString(masked: string) {
  return (masked ?? '')
    .replace(/[^\d,]/g, '')
    .replace(/\./g, '')
    .replace(',', '.')
    .trim()
}

export function coercePriceFromApi(precoBruto: unknown): number {
  if (typeof precoBruto === 'number') return precoBruto

  const str = String(precoBruto ?? '')
    .replace(/[^\d,.-]/g, '')
    .replace(/\./g, '')
    .replace(',', '.')
  const parsed = parseFloat(str)
  return Number.isFinite(parsed) ? parsed : 0
}