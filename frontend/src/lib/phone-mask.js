export function phoneDigitsBR(value = '') {
  let digits = String(value).replace(/\D/g, '')
  if (digits.startsWith('55') && digits.length > 11) digits = digits.slice(2)
  return digits.slice(0, 11)
}

export function maskPhoneBR(value = '') {
  const digits = phoneDigitsBR(value)
  if (!digits) return ''
  if (digits.length < 3) return `(${digits}`

  const areaCode = digits.slice(0, 2)
  if (digits.length <= 6) return `(${areaCode}) ${digits.slice(2)}`

  const mobile = digits.length === 11
  const prefixEnd = mobile ? 7 : 6
  const prefix = digits.slice(2, prefixEnd)
  const suffix = digits.slice(prefixEnd)
  return `(${areaCode}) ${prefix}${suffix ? `-${suffix}` : ''}`
}

export function phoneToE164BR(value = '') {
  const digits = phoneDigitsBR(value)
  return digits.length === 10 || digits.length === 11 ? `+55${digits}` : null
}

export function phoneDigitOffset(value, caret) {
  return phoneDigitsBR(String(value).slice(0, Math.max(0, caret ?? 0))).length
}

export function phoneCaretFromDigitOffset(masked, digitOffset) {
  if (digitOffset <= 0) return 0
  let seen = 0
  for (let index = 0; index < masked.length; index += 1) {
    if (/\d/.test(masked[index])) seen += 1
    if (seen === digitOffset) return index + 1
  }
  return masked.length
}
