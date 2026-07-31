export function cpfDigits(value = '') {
  return String(value).replace(/\D/g, '').slice(0, 11)
}

export function isValidCpf(value = '') {
  const digits = String(value).replace(/\D/g, '')
  if (digits.length !== 11 || new Set(digits).size === 1) return false

  const digit = (length: number) => {
    let sum = 0
    let weight = length + 1
    for (let index = 0; index < length; index += 1) {
      sum += Number(digits[index]) * weight
      weight -= 1
    }
    const remainder = sum % 11
    return remainder < 2 ? 0 : 11 - remainder
  }

  return digit(9) === Number(digits[9]) && digit(10) === Number(digits[10])
}

export function maskCpf(value = '') {
  const digits = cpfDigits(value)
  if (digits.length <= 3) return digits
  if (digits.length <= 6) return `${digits.slice(0, 3)}.${digits.slice(3)}`
  if (digits.length <= 9) {
    return `${digits.slice(0, 3)}.${digits.slice(3, 6)}.${digits.slice(6)}`
  }
  return `${digits.slice(0, 3)}.${digits.slice(3, 6)}.${digits.slice(6, 9)}-${digits.slice(9)}`
}
