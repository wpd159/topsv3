// Utilitario puro para data de nascimento no formato visual brasileiro
// (DD/MM/AAAA), convertido na fronteira para o formato canonico ISO
// (YYYY-MM-DD) usado pelo estado/API. Nao usa Date.parse em string
// brasileira e nao depende de timezone: idade e validade sao calculadas
// comparando partes numericas de dia/mes/ano.

const DAYS_IN_MONTH = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]

type BirthDateParts = {
  day: number
  month: number
  year: number
}

function isLeapYear(year: number) {
  return (year % 4 === 0 && year % 100 !== 0) || year % 400 === 0
}

export function maskBirthDateBR(value: string): string {
  const digits = value.replace(/\D/g, '').slice(0, 8)
  const day = digits.slice(0, 2)
  const month = digits.slice(2, 4)
  const year = digits.slice(4, 8)

  let out = day
  if (month) out += '/' + month
  if (year) out += '/' + year
  return out
}

export function parseBirthDateBR(value: string): BirthDateParts | null {
  const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(value.trim())
  if (!match) return null

  const day = Number(match[1])
  const month = Number(match[2])
  const year = Number(match[3])

  if (year < 1900 || year > 2100) return null
  if (month < 1 || month > 12) return null

  const maxDay = month === 2 && isLeapYear(year) ? 29 : DAYS_IN_MONTH[month - 1]
  if (day < 1 || day > maxDay) return null

  return { day, month, year }
}

export function birthDateBRToIso(value: string): string | null {
  const parts = parseBirthDateBR(value)
  if (!parts) return null

  const dd = String(parts.day).padStart(2, '0')
  const mm = String(parts.month).padStart(2, '0')
  return `${parts.year}-${mm}-${dd}`
}

export function isoToBirthDateBR(value: string | null | undefined): string {
  if (!value) return ''

  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value.trim())
  if (!match) return ''

  return `${match[3]}/${match[2]}/${match[1]}`
}

export function isAdultBirthDateBR(value: string, minimumAge = 18): boolean {
  const parts = parseBirthDateBR(value)
  if (!parts) return false

  const today = new Date()
  const todayYear = today.getFullYear()
  const todayMonth = today.getMonth() + 1
  const todayDay = today.getDate()

  let age = todayYear - parts.year
  if (todayMonth < parts.month || (todayMonth === parts.month && todayDay < parts.day)) {
    age -= 1
  }

  if (age < 0) return false

  return age >= minimumAge
}
