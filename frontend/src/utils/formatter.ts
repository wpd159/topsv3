export function formatPhone(value: string): string {
  if (!value) return ''

  // remove tudo que não for número
  let onlyNums = value.replace(/\D/g, '')

  // remove o código do país "55" se estiver no início
  if (onlyNums.startsWith('55') && onlyNums.length > 11) {
    onlyNums = onlyNums.slice(2)
  }

  // aplica máscara conforme o tamanho
  if (onlyNums.length <= 2) return `(${onlyNums}`
  if (onlyNums.length <= 6)
    return `(${onlyNums.slice(0, 2)}) ${onlyNums.slice(2)}`
  if (onlyNums.length <= 10)
    return `(${onlyNums.slice(0, 2)}) ${onlyNums.slice(2, 6)}-${onlyNums.slice(6)}`
  if (onlyNums.length <= 11)
    return `(${onlyNums.slice(0, 2)}) ${onlyNums.slice(2, 7)}-${onlyNums.slice(7)}`

  // se for maior que 11, corta o excedente
  return `(${onlyNums.slice(0, 2)}) ${onlyNums.slice(2, 7)}-${onlyNums.slice(7, 11)}`
}
export function formatCPF(value: string): string {
  if (!value) return ''

  // remove tudo que não for número
  let onlyNums = value.replace(/\D/g, '')

  // corta o excedente (CPF tem 11 dígitos)
  if (onlyNums.length > 11) {
    onlyNums = onlyNums.slice(0, 11)
  }

  // aplica máscara conforme o tamanho
  if (onlyNums.length <= 3) return onlyNums
  if (onlyNums.length <= 6)
    return `${onlyNums.slice(0, 3)}.${onlyNums.slice(3)}`
  if (onlyNums.length <= 9)
    return `${onlyNums.slice(0, 3)}.${onlyNums.slice(3, 6)}.${onlyNums.slice(6)}`
  return `${onlyNums.slice(0, 3)}.${onlyNums.slice(3, 6)}.${onlyNums.slice(6, 9)}-${onlyNums.slice(9)}`
}


// 📧 Normaliza e valida e-mail básico
export function validateEmail(email: string): boolean {
  const regex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  return regex.test(email.toLowerCase())
}

// 🔐 Valida senha e retorna feedback granular
export function validatePassword(value: string) {
  return {
    length: value.length >= 8,
    uppercase: /[A-Z]/.test(value),
    lowercase: /[a-z]/.test(value),
    number: /\d/.test(value),
    symbol: /[!@#$%^&*(),.?":{}|<>]/.test(value),
    noCommon: !/(1234|abcd|senha|password|qwerty)/i.test(value),
  }
}

// 💰 Formata valor em moeda brasileira (R$ 1.234,56)
export function formatCurrencyBRL(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === '') return ''

  let numero: number

  if (typeof value === 'number') {
    numero = value
  } else {
    // remove tudo que não for número, vírgula ou ponto
    const cleaned = value.replace(/[^\d.,]/g, '')

    // troca vírgula por ponto e remove pontos de milhar
    const normalized = cleaned.replace(/\./g, '').replace(/,/g, '.')

    numero = Number(normalized)
  }

  if (Number.isNaN(numero)) return ''

  return numero.toLocaleString('pt-BR', {
    style: 'currency',
    currency: 'BRL',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}
