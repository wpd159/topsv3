import { birthDateToIso, isoToBirthDate } from '@/lib/date/birth-date'

// Date-only birth data: compare civil dates, never parse midnight in a browser timezone.
export function ownerAge(birth: string | null | undefined, now = new Date()) {
  if (!birth || !/^\d{4}-\d{2}-\d{2}$/.test(birth) || birthDateToIso(isoToBirthDate(birth)) !== birth) return null
  const today = Object.fromEntries(new Intl.DateTimeFormat('en-US', {
    timeZone: 'America/Sao_Paulo', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(now).map(({ type, value }) => [type, value]))
  const [year, month, day] = birth.split('-').map(Number)
  const currentYear = Number(today.year), currentMonth = Number(today.month), currentDay = Number(today.day)
  let age = currentYear - year
  if (currentMonth < month || (currentMonth === month && currentDay < day)) age -= 1
  return age >= 0 ? age : null
}

export function ownerBirthLabel(birth: string | null | undefined) {
  return birth && birthDateToIso(isoToBirthDate(birth)) === birth ? isoToBirthDate(birth) : 'Não informado'
}
