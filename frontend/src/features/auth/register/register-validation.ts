import { formatPhone, validateEmail, validatePassword } from '@/utils/formatter'

export { validateEmail }

export const PASSWORD_HINT =
  'Use pelo menos 8 caracteres, com letra maiúscula, minúscula, número e símbolo.'

export function maskPhoneBR(value: string): string {
  return formatPhone(value)
}

export function isPhoneComplete(value: string): boolean {
  return value.replace(/\D/g, '').length >= 10
}

export function isPasswordValid(value: string): boolean {
  return Object.values(validatePassword(value)).every(Boolean)
}

export function passwordsMatch(passwordValue: string, confirmPasswordValue: string): boolean {
  return confirmPasswordValue.length > 0 && passwordValue === confirmPasswordValue
}

// --- Data de nascimento (DD/MM/AAAA, sem seletor nativo) ---------------

const DAYS_IN_MONTH = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]

type BirthDateParts = { day: number; month: number; year: number }

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

export function isValidBirthDateBR(value: string): boolean {
  return parseBirthDateBR(value) !== null
}

// Compara apenas partes numericas de dia/mes/ano; nao usa Date.parse em
// string BR e nao depende de timezone.
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

// Conversao para ISO (YYYY-MM-DD) somente na fronteira com o adapter de envio.
export function birthDateBRToIso(value: string): string | null {
  const parts = parseBirthDateBR(value)
  if (!parts) return null

  const dd = String(parts.day).padStart(2, '0')
  const mm = String(parts.month).padStart(2, '0')
  return `${parts.year}-${mm}-${dd}`
}

// --- next (redirecionamento pos-cadastro) ------------------------------

export function getSafeNext(value: string | null | undefined): string | null {
  if (!value) return null
  if (!value.startsWith('/') || value.startsWith('//')) return null
  return value
}
