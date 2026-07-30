export function calculateAge(value?: string | null) {
  if (!value) return null
  const date = new Date(`${String(value).slice(0, 10)}T00:00:00`)
  if (Number.isNaN(date.getTime())) return null
  const today = new Date()
  let age = today.getFullYear() - date.getFullYear()
  const monthDiff = today.getMonth() - date.getMonth()
  if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < date.getDate())) age -= 1
  return age >= 18 && age < 100 ? age : null
}

export function digitsOnly(value: string) {
  return value.replace(/\D/g, '')
}

export function formatCpf(value: string) {
  const v = digitsOnly(value).slice(0, 11)
  return v
    .replace(/^(\d{3})(\d)/, '$1.$2')
    .replace(/^(\d{3})\.(\d{3})(\d)/, '$1.$2.$3')
    .replace(/\.(\d{3})(\d)/, '.$1-$2')
}

export function selectClassName() {
  return 'h-12 w-full rounded-xl border border-zinc-200 bg-white px-4 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-zinc-900 focus-visible:ring-offset-2'
}

export function normalizeSearchValue(value: string) {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim()
}

function labelFor(items: Array<{ value: string; label: string }>, value: string) {
  return items.find((item) => item.value === value)?.label
    ?? value.toLowerCase().replaceAll('_', ' ').replace(/^\p{L}/u, (letter) => letter.toUpperCase())
}

export function formatWizardCategory(value: string) {
  return value ? labelFor(categorias, value) : 'Não informado'
}

export function formatWizardSchedule(value: string) {
  return value ? labelFor(horarios, value) : 'Não informado'
}

export function formatWizardServices(values: string[]) {
  return values.length ? values.map((value) => labelFor(servicos, value)).join(', ') : 'Não informado'
}

export function formatWizardLocations(values: string[]) {
  return values.length ? values.map((value) => labelFor(locais, value)).join(', ') : 'Não informado'
}

export function formatWizardPhotoCount(total: number) {
  return `${total} ${total === 1 ? 'foto selecionada' : 'fotos selecionadas'}`
}
import { categorias, horarios, locais, servicos } from './wizard-constants'
