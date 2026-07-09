/**
 * Extrai mensagem de erro de um corpo já lido (JSON com message/error ou texto puro).
 */
export function messageFromApiBody(raw: string, httpStatus: number, fallback: string): string {
  const trimmed = raw.trim()
  if (!trimmed) return `${fallback} (HTTP ${httpStatus})`
  try {
    const parsed = JSON.parse(trimmed) as { message?: unknown; error?: unknown }
    const msg =
      (typeof parsed.message === 'string' && parsed.message.trim()) ||
      (typeof parsed.error === 'string' && parsed.error.trim()) ||
      ''
    if (msg) return msg
  } catch {
    return trimmed
  }
  return trimmed
}

/**
 * Lê Response.text() e extrai mensagem (JSON ou texto puro).
 */
export async function readApiErrorResponse(res: Response, fallback: string): Promise<string> {
  const raw = await res.text().catch(() => '')
  return messageFromApiBody(raw, res.status, fallback)
}
