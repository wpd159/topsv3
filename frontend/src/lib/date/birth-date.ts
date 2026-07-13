export function maskBirthDate(value: string) {
  const digits = value.replace(/\D/g, '').slice(0, 8)
  if (digits.length <= 2) return digits
  if (digits.length <= 4) return `${digits.slice(0, 2)}/${digits.slice(2)}`
  return `${digits.slice(0, 2)}/${digits.slice(2, 4)}/${digits.slice(4)}`
}

export function birthDateToIso(value: string) {
  const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(value.trim())
  if (!match) return null
  const day = Number(match[1])
  const month = Number(match[2])
  const year = Number(match[3])
  const date = new Date(Date.UTC(year, month - 1, day))
  if (
    date.getUTCFullYear() !== year ||
    date.getUTCMonth() !== month - 1 ||
    date.getUTCDate() !== day
  ) return null
  return `${String(year).padStart(4, '0')}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
}

export function isoToBirthDate(value?: string | null) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(value || '').slice(0, 10))
  return match ? `${match[3]}/${match[2]}/${match[1]}` : ''
}

export function isAdultBirthDate(value: string, now = new Date()) {
  const iso = birthDateToIso(value)
  if (!iso) return false
  const [year, month, day] = iso.split('-').map(Number)
  let age = now.getFullYear() - year
  const monthDelta = now.getMonth() + 1 - month
  if (monthDelta < 0 || (monthDelta === 0 && now.getDate() < day)) age -= 1
  return age >= 18
}
