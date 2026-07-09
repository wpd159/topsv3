/** Apenas dígitos do CPF (remove pontuação). */
export function normalizarCpfDigitos(input: string): string {
  return input.replace(/\D/g, '')
}

export function maskCPF(value: string): string {
  return value
    .replace(/\D/g, '')
    .replace(/(\d{3})(\d)/, '$1.$2')
    .replace(/(\d{3})(\d)/, '$1.$2')
    .replace(/(\d{3})(\d{1,2})$/, '$1-$2')
    .slice(0, 14)
}

/** 11 dígitos após remover pontuação; rejeita sequência óbvia inválida. */
export function cpfFormatoBasicoValido(input: string): boolean {
  const digitos = normalizarCpfDigitos(input)
  if (digitos.length !== 11) return false
  return !/^(\d)\1{10}$/.test(digitos)
}
