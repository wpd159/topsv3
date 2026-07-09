// src/utils/masks.ts
export function onlyDigits(value: string = ""): string {
  return value.replace(/\D/g, "")
}

export function maskCPF(value: string = ""): string {
  const d = onlyDigits(value).slice(0, 11)

  const p1 = d.slice(0, 3)
  const p2 = d.slice(3, 6)
  const p3 = d.slice(6, 9)
  const p4 = d.slice(9, 11)

  if (d.length <= 3) return p1
  if (d.length <= 6) return `${p1}.${p2}`
  if (d.length <= 9) return `${p1}.${p2}.${p3}`
  return `${p1}.${p2}.${p3}-${p4}`
}

// (11) 99999-9999 ou (11) 9999-9999
export function maskPhoneBR(value: string = ""): string {
  let d = onlyDigits(value)

  // se vier com DDI 55, tira
  if (d.startsWith("55") && d.length > 11) d = d.slice(2)

  d = d.slice(0, 11)

  const dd = d.slice(0, 2)
  const part1 = d.slice(2)

  if (d.length === 0) return ""
  if (d.length < 3) return `(${dd}`
  if (d.length < 7) return `(${dd}) ${part1}`

  // 10 dígitos (fixo) => (11) 9999-9999
  if (d.length <= 10) {
    const a = d.slice(2, 6)
    const b = d.slice(6, 10)
    return `(${dd}) ${a}${b ? "-" + b : ""}`
  }

  // 11 dígitos (cel) => (11) 99999-9999
  const a = d.slice(2, 7)
  const b = d.slice(7, 11)
  return `(${dd}) ${a}${b ? "-" + b : ""}`
}

export function normalizeCPF(value: string = ""): string {
  return onlyDigits(value).slice(0, 11)
}

export function normalizePhoneBR(value: string = ""): string {
  let d = onlyDigits(value)
  if (d.startsWith("55") && d.length > 11) d = d.slice(2)
  return d.slice(0, 11)
}
