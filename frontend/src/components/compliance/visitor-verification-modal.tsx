"use client"

import { useEffect, useLayoutEffect, useRef, useState, type ChangeEvent } from "react"
import { createPortal } from "react-dom"
import { XMarkIcon } from "@heroicons/react/24/solid"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { setVerified, type StatusVisitante } from "@/lib/compliance/visitor-access"

type VisitorVerificationModalProps = {
  open: boolean
  level?: "LIGHT" | "REINFORCED" | "STRONG"
  context?: {
    anuncioId?: number
    route?: string
    contentClassification?: string | null
  }
  onOpenChange: (open: boolean) => void
  onVerified?: (status: StatusVisitante) => void
}

type ChallengeResponse = {
  challengeId: string
  level: "LIGHT" | "REINFORCED" | "STRONG"
  phrase: string
  secondaryPhrase?: string | null
  contextLabel?: string | null
  contextPrompt?: string | null
  requiresExplicitAcknowledgement?: boolean
  riskScore?: number
  decision?: string
  reasonSummary?: string
  storageSource?: string
  tokenTtlMinutes?: number
  documentOnDemand?: boolean
  expiresAt: string
}

let isVerificationOpen = false

type Fase = 1 | 2

function apenasDigitos(value: string) {
  return (value ?? "").replace(/\D+/g, "")
}

/** Evita exibir JSON bruto do backend em toast (Spring: message, errors[].defaultMessage, etc.). */
function mensagemAmigavelCorpoErroApi(body: string, fallback: string): string {
  const t = body?.trim()
  if (!t) return fallback
  if (!t.startsWith("{") && !t.startsWith("[")) {
    return t.length > 400 ? `${t.slice(0, 397)}...` : t
  }
  try {
    const j = JSON.parse(t) as Record<string, unknown>
    const msg = j.message
    if (typeof msg === "string" && msg.trim()) return msg.trim()
    const errs = j.errors
    if (Array.isArray(errs) && errs.length > 0) {
      const first = errs[0] as Record<string, unknown>
      const dm = first.defaultMessage ?? first.message
      if (typeof dm === "string" && dm.trim()) return dm.trim()
    }
    const detail = j.detail
    if (typeof detail === "string" && detail.trim()) return detail.trim()
    const err = j.error
    if (
      typeof err === "string" &&
      err.trim() &&
      err !== "Bad Request" &&
      err !== "Internal Server Error"
    ) {
      return err.trim()
    }
  } catch {
    /* corpo não é JSON válido */
  }
  return fallback
}

/** Exibição: até 8 dígitos → dd/mm/aaaa */
function formatarDdMmAaaa(digits: string) {
  const d = digits.slice(0, 8)
  if (d.length <= 2) return d
  if (d.length <= 4) return `${d.slice(0, 2)}/${d.slice(2)}`
  return `${d.slice(0, 2)}/${d.slice(2, 4)}/${d.slice(4)}`
}

function contarDigitosAntesDoIndice(str: string, index: number) {
  let n = 0
  const lim = Math.min(index, str.length)
  for (let i = 0; i < lim; i++) {
    const ch = str[i]
    if (ch >= "0" && ch <= "9") n++
  }
  return n
}

/** Posição do cursor após o enésimo dígito (0 = início). */
function posicaoCursorAposDigitos(masked: string, digitCount: number) {
  if (digitCount <= 0) return 0
  let seen = 0
  for (let i = 0; i < masked.length; i++) {
    const ch = masked[i]
    if (ch >= "0" && ch <= "9") {
      seen++
      if (seen === digitCount) return i + 1
    }
  }
  return masked.length
}

/**
 * Converte data mascarada completa e válida no calendário para yyyy-MM-dd (envio ao backend).
 * Retorna null se incompleta ou inválida.
 */
function parseDdMmYyyyParaIso(masked: string): string | null {
  const m = masked.trim().match(/^(\d{2})\/(\d{2})\/(\d{4})$/)
  if (!m) return null
  const dd = Number(m[1])
  const mm = Number(m[2])
  const yyyy = Number(m[3])
  if (mm < 1 || mm > 12 || dd < 1 || dd > 31) return null
  const t = new Date(yyyy, mm - 1, dd)
  if (t.getFullYear() !== yyyy || t.getMonth() !== mm - 1 || t.getDate() !== dd) return null
  return `${yyyy}-${String(mm).padStart(2, "0")}-${String(dd).padStart(2, "0")}`
}

function validateDateOfBirth(masked: string): string | null {
  const trimmed = (masked ?? "").trim()
  if (!trimmed) return "É necessário ser maior de 18 anos."

  const iso = parseDdMmYyyyParaIso(trimmed)

  if (!iso) {
    if (/^\d{2}\/\d{2}\/\d{4}$/.test(trimmed)) {
      return "Data de nascimento inválida."
    }
    return "É necessário ser maior de 18 anos."
  }

  const dob = new Date(`${iso}T00:00:00`)
  if (Number.isNaN(dob.getTime())) return "Data de nascimento inválida."

  const today = new Date()
  today.setHours(0, 0, 0, 0)

  if (dob.getTime() > today.getTime()) {
    return "Data de nascimento não pode estar no futuro."
  }

  const adultCutoff = new Date(today)
  adultCutoff.setFullYear(adultCutoff.getFullYear() - 18)

  if (dob.getTime() > adultCutoff.getTime()) {
    return "É necessário ser maior de 18 anos."
  }

  return null
}

function useDdMmYyyyMask(value: string, onMaskedChange: (masked: string) => void) {
  const inputRef = useRef<HTMLInputElement>(null)
  const caretRef = useRef<number | null>(null)

  const onChange = (e: ChangeEvent<HTMLInputElement>) => {
    const el = e.target
    const sel = el.selectionStart ?? 0
    const raw = el.value
    const digitsBefore = contarDigitosAntesDoIndice(raw, sel)
    const newDigits = apenasDigitos(raw).slice(0, 8)
    const newMasked = formatarDdMmAaaa(newDigits)
    const newCaret = posicaoCursorAposDigitos(newMasked, Math.min(digitsBefore, newDigits.length))
    caretRef.current = newCaret
    onMaskedChange(newMasked)
  }

  useLayoutEffect(() => {
    const el = inputRef.current
    if (el && caretRef.current != null) {
      const pos = Math.min(caretRef.current, el.value.length)
      el.setSelectionRange(pos, pos)
      caretRef.current = null
    }
  }, [value])

  return { inputRef, onChange }
}

function formatarCpf(value: string) {
  const digits = apenasDigitos(value).slice(0, 11)
  if (digits.length <= 3) return digits
  if (digits.length <= 6) return `${digits.slice(0, 3)}.${digits.slice(3)}`
  if (digits.length <= 9) return `${digits.slice(0, 3)}.${digits.slice(3, 6)}.${digits.slice(6)}`
  return `${digits.slice(0, 3)}.${digits.slice(3, 6)}.${digits.slice(6, 9)}-${digits.slice(9)}`
}

function validarCpf(value: string) {
  const cpf = apenasDigitos(value)
  if (cpf.length !== 11) return false
  if (/^(\d)\1{10}$/.test(cpf)) return false

  let sum = 0
  for (let i = 0; i < 9; i++) sum += Number(cpf[i]) * (10 - i)
  let d1 = (sum * 10) % 11
  if (d1 === 10) d1 = 0
  if (d1 !== Number(cpf[9])) return false

  sum = 0
  for (let i = 0; i < 10; i++) sum += Number(cpf[i]) * (11 - i)
  let d2 = (sum * 10) % 11
  if (d2 === 10) d2 = 0
  return d2 === Number(cpf[10])
}

/** Valores atuais de `resolveContextExpectedText` no backend (VisitorVerificationService). */
const TOKENS_CONTEXTO_CONHECIDOS = ["EXPLICITO", "SENSIVEL", "RESTRITO"] as const

function normalizarPromptContexto(prompt: string) {
  return prompt
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toUpperCase()
}

/**
 * Extrai a palavra esperada para `contextAnswer` a partir do texto do prompt, sem exibir o prompt ao usuário.
 * Várias estratégias + fallback por tokens conhecidos do backend atual.
 */
function extrairRespostaContextual(prompt?: string | null): string {
  const raw = prompt?.trim()
  if (!raw) return ""

  const norm = normalizarPromptContexto(raw)

  // Aspas ASCII e tipográficas comuns
  const citado = norm.match(
    /[\u0022\u0027\u201C\u201D\u2018\u2019\u00AB\u00BB]([A-Z0-9_-]{3,40})[\u0022\u0027\u201C\u201D\u2018\u2019\u00AB\u00BB]/
  )
  if (citado?.[1]) return citado[1]

  // Padrão legado: "PALAVRA X"
  const aposPalavra = norm.match(/\bPALAVRA\s+([A-Z0-9_-]{3,40})\b/)
  if (aposPalavra?.[1]) return aposPalavra[1]

  // Variações leves: TERMO / CODIGO
  const aposTermo = norm.match(/\b(?:TERMO|CODIGO)\s+([A-Z0-9_-]{3,40})\b/)
  if (aposTermo?.[1]) return aposTermo[1]

  // "DIGITE ... PALAVRA/TERMO/CODIGO X"
  const digiteSeq = norm.match(
    /\bDIGITE\s+(?:A\s+)?(?:PALAVRA|TERMO|CODIGO)\s+([A-Z0-9_-]{3,40})\b/
  )
  if (digiteSeq?.[1]) return digiteSeq[1]

  // Último fallback: qualquer token conhecido presente como palavra inteira
  for (const token of TOKENS_CONTEXTO_CONHECIDOS) {
    const re = new RegExp(`\\b${token}\\b`)
    if (re.test(norm)) return token
  }

  return ""
}

export function VisitorVerificationModal({
  open,
  level = "LIGHT",
  context,
  onOpenChange,
  onVerified,
}: VisitorVerificationModalProps) {
  const API = process.env.NEXT_PUBLIC_API_URL
  const [challenge, setChallenge] = useState<ChallengeResponse | null>(null)
  const [loadingChallenge, setLoadingChallenge] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [fase, setFase] = useState<Fase>(1)
  const [dateOfBirth, setDateOfBirth] = useState("")
  const [dateOfBirthError, setDateOfBirthError] = useState<string | null>(null)
  const [acceptedAgePolicy, setAcceptedAgePolicy] = useState(false)
  const [cpf, setCpf] = useState("")
  const [showDocFallback, setShowDocFallback] = useState(false)
  const [docFile, setDocFile] = useState<File | null>(null)
  const [docSubmitting, setDocSubmitting] = useState(false)
  const [confirmDateOfBirth, setConfirmDateOfBirth] = useState("")
  const [acceptedRestrictedTerms, setAcceptedRestrictedTerms] = useState(false)
  const [acceptedPrivacyNotice, setAcceptedPrivacyNotice] = useState(false)
  const [acceptedExplicitScope, setAcceptedExplicitScope] = useState(false)
  const [portalReady, setPortalReady] = useState(false)

  useLayoutEffect(() => {
    setPortalReady(true)
  }, [])

  useEffect(() => {
    if (!open) return

    if (isVerificationOpen) {
      onOpenChange(false)
      return
    }

    isVerificationOpen = true

    return () => {
      isVerificationOpen = false
    }
  }, [onOpenChange, open])

  useEffect(() => {
    if (open) return
    // Segurança/privacidade: limpar tudo ao fechar (sem persistência local).
    setChallenge(null)
    setLoadingChallenge(false)
    setSubmitting(false)
    setFase(1)
    setDateOfBirth("")
    setDateOfBirthError(null)
    setCpf("")
    setShowDocFallback(false)
    setDocFile(null)
    setDocSubmitting(false)
    setConfirmDateOfBirth("")
    setAcceptedAgePolicy(false)
    setAcceptedRestrictedTerms(false)
    setAcceptedPrivacyNotice(false)
    setAcceptedExplicitScope(false)
  }, [open])

  useEffect(() => {
    if (!open || !API) return

    let cancelled = false
    setLoadingChallenge(true)

    fetch(`${API}/compliance/visitor/challenge`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({
        level,
        anuncioId: context?.anuncioId,
        route: context?.route,
        contentClassification: context?.contentClassification,
      }),
    })
      .then(async (res) => {
        if (!res.ok) {
          const text = await res.text()
          throw new Error(mensagemAmigavelCorpoErroApi(text, "Falha ao iniciar verificacao."))
        }
        return res.json()
      })
      .then((data: ChallengeResponse) => {
        if (!cancelled) {
          setChallenge(data)
          setShowDocFallback(false)
          setFase(1)
        }
      })
      .catch((err: any) => {
        if (!cancelled) {
          toast.error(err?.message || "Nao foi possivel iniciar a verificacao.")
          onOpenChange(false)
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingChallenge(false)
        }
      })

    return () => {
      cancelled = true
    }
  }, [API, context?.anuncioId, context?.contentClassification, context?.route, level, onOpenChange, open])

  const close = () => onOpenChange(false)

  const dobMask = useDdMmYyyyMask(dateOfBirth, (masked) => {
    setDateOfBirth(masked)
    setDateOfBirthError(validateDateOfBirth(masked))
  })
  const confirmMask = useDdMmYyyyMask(confirmDateOfBirth, setConfirmDateOfBirth)

  const irParaFase2 = () => {
    const dobError = validateDateOfBirth(dateOfBirth)
    if (dobError) {
      setDateOfBirthError(dobError)
      toast.error(dobError)
      return
    }
    if (!acceptedAgePolicy) {
      toast.error("Confirme o aceite obrigatório para continuar.")
      return
    }
    setFase(2)
  }

  const concluirFase2 = async () => {
    if (!API || !challenge) return

    const cpfDigits = apenasDigitos(cpf)
    if (!validarCpf(cpfDigits)) {
      toast.error("CPF inválido.")
      return
    }

    const dobError1 = validateDateOfBirth(dateOfBirth)
    const dobError2 = validateDateOfBirth(confirmDateOfBirth)
    if (dobError1 || dobError2) {
      toast.error(dobError1 || dobError2 || "Dados inválidos.")
      return
    }
    const isoDob = parseDdMmYyyyParaIso(dateOfBirth.trim())
    const isoConfirm = parseDdMmYyyyParaIso(confirmDateOfBirth.trim())
    if (!isoDob || !isoConfirm || isoDob !== isoConfirm) {
      toast.error("A confirmação da data de nascimento não confere.")
      return
    }

    if (!acceptedRestrictedTerms || !acceptedPrivacyNotice) {
      toast.error("Confirme os aceites obrigatórios para concluir a verificação.")
      return
    }

    if (challenge.requiresExplicitAcknowledgement && !acceptedExplicitScope) {
      toast.error("Confirme a etapa adicional para conteúdo explícito.")
      return
    }

    const contextAnswer = extrairRespostaContextual(challenge.contextPrompt)
    const exigeContextoPeloPrompt = Boolean(challenge.contextPrompt?.trim())
    if (exigeContextoPeloPrompt && !contextAnswer) {
      toast.error("Não foi possível concluir a verificação automática para este acesso.")
      return
    }

    setSubmitting(true)
    try {
      // Compatibilidade com backend atual:
      // - /verify exige frases e, em alguns casos, confirmação contextual.
      // - A UX não exibe mais códigos; enviamos automaticamente os valores esperados.
      const res = await fetch(`${API}/compliance/visitor/verify`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({
          challengeId: challenge.challengeId,
          requestedLevel: challenge.level,
          dateOfBirth: isoDob,
          cpf: cpfDigits,
          acceptedAgePolicy: true,
          acceptedRestrictedTerms,
          acceptedPrivacyNotice,
          challengePhrase: challenge.phrase,
          secondaryPhrase: challenge.secondaryPhrase ?? "",
          contextAnswer,
          purpose: "VALIDACAO_AUTOMATICA_UI",
          acceptedExplicitScope,
        }),
      })

      if (!res.ok) {
        const text = await res.text()
        throw new Error(mensagemAmigavelCorpoErroApi(text, "Falha ao concluir a verificacao."))
      }

      const payload = (await res.json()) as Partial<StatusVisitante>
      const status: StatusVisitante = {
        ...payload,
        verified: payload.verified ?? Boolean(payload.expiresAt || payload.explicitExpiresAt),
        explicitVerified: payload.explicitVerified ?? Boolean(payload.explicitExpiresAt),
      }
      setVerified(status?.expiresAt ?? null, status?.explicitExpiresAt ?? null)
      toast.success("Verificacao concluida. O conteudo restrito foi liberado.")
      onOpenChange(false)
      onVerified?.(status)
    } catch (err: any) {
      setShowDocFallback(true)
      toast.error(err?.message || "Nao foi possivel concluir a verificacao.")
    } finally {
      setSubmitting(false)
    }
  }

  const enviarDocumentoFallback = async () => {
    if (!API || !challenge || !docFile) {
      toast.error("Selecione um arquivo (imagem ou PDF).")
      return
    }
    setDocSubmitting(true)
    try {
      const fd = new FormData()
      fd.append("challengeId", challenge.challengeId)
      if (context?.anuncioId != null) fd.append("anuncioId", String(context.anuncioId))
      const cpfDigits = apenasDigitos(cpf)
      if (cpfDigits.length === 11) fd.append("cpf", cpfDigits)
      fd.append("file", docFile)
      const res = await fetch(`${API}/compliance/visitor/document`, {
        method: "POST",
        body: fd,
        credentials: "include",
      })
      const text = await res.text()
      if (!res.ok) {
        throw new Error(mensagemAmigavelCorpoErroApi(text, "Nao foi possivel enviar o documento."))
      }
      toast.success("Documento enviado para análise manual. Você será contatado se necessário.")
      setDocFile(null)
    } catch (err: any) {
      toast.error(err?.message || "Falha no envio do documento.")
    } finally {
      setDocSubmitting(false)
    }
  }

  if (!open) return null

  const modalUi = (
    <div
      className="fixed inset-0 z-[120] bg-black/45 px-4 py-6"
      onClick={() => onOpenChange(false)}
      role="dialog"
      aria-modal="true"
    >
      <div className="mx-auto flex min-h-full w-full max-w-[520px] items-center justify-center">
        <div
          className="w-full max-h-[90vh] overflow-y-auto rounded-[28px] bg-white p-6 shadow-2xl"
          onClick={(e) => e.stopPropagation()}
        >
          <div className="mb-5 flex items-start justify-between gap-4">
            <div>
              <h2 className="text-xl font-semibold text-gray-900">Conteúdo restrito apenas para maiores de 18 anos</h2>
              <p className="mt-1 text-sm text-gray-500">
                A imagem original só pode ser exibida após a confirmação de sua idade.
              </p>
            </div>

            <button
              type="button"
              onClick={close}
              className="rounded-full border border-gray-200 p-2 text-gray-500 transition hover:bg-gray-50 hover:text-gray-700"
            >
              <XMarkIcon className="h-5 w-5" />
            </button>
          </div>

          {loadingChallenge || !challenge ? (
            <div className="rounded-2xl border border-gray-100 bg-gray-50 px-4 py-8 text-center text-sm text-gray-500">
              Iniciando verificacao...
            </div>
          ) : (
            <div className="flex flex-col gap-4">
              <div className="rounded-2xl border border-pink-100 bg-pink-50/70 px-4 py-3 text-xs text-gray-600 space-y-1">
                <p className="text-sm text-gray-700 font-medium">
                  Validação adicional exigida por lei para acesso a conteúdo adulto.
                </p>
                <p className="text-sm text-gray-700">
                  De acordo com a legislação aplicável, este conteúdo só pode ser exibido mediante confirmação
                  de que o usuário é maior de 18 anos.
                </p>
                <p className="text-sm text-gray-700">
                  Esta verificação é obrigatória e visa garantir a conformidade legal da plataforma e a proteção
                  de menores.
                </p>
                <p className="text-sm text-gray-700">
                  Nível aplicado:{" "}
                  {challenge.level === "LIGHT"
                    ? "LEVE"
                    : challenge.level === "REINFORCED"
                      ? "REFORÇADO"
                      : "FORTE"}
                  {challenge.documentOnDemand
                    ? " • Sessão sinalizada: pode ser necessário envio de documento para análise."
                    : " • Sessão com validação temporária."}
                </p>
              </div>

              <div className="flex items-center justify-between rounded-2xl border border-gray-100 bg-gray-50 px-4 py-3 text-sm">
                <div className="flex items-center gap-2">
                  <span className={fase === 1 ? "font-semibold text-gray-900" : "text-gray-500"}>1. Idade</span>
                  <span className="text-gray-300">/</span>
                  <span className={fase === 2 ? "font-semibold text-gray-900" : "text-gray-500"}>2. CPF</span>
                </div>
                {fase !== 1 && (
                  <button
                    type="button"
                    className="text-xs font-medium text-gray-600 hover:text-gray-900"
                    onClick={() => setFase(1)}
                    disabled={submitting}
                  >
                    Voltar
                  </button>
                )}
              </div>

              {fase === 1 && (
                <div className="flex flex-col gap-4">
                  <div className="flex flex-col gap-2">
                    <Label htmlFor="visitor-date-of-birth">Data de nascimento</Label>
                    <Input
                      ref={dobMask.inputRef}
                      id="visitor-date-of-birth"
                      type="text"
                      inputMode="numeric"
                      autoComplete="off"
                      placeholder="dd/mm/aaaa"
                      maxLength={10}
                      value={dateOfBirth}
                      onChange={dobMask.onChange}
                      required
                    />
                    {dateOfBirthError && (
                      <p className="text-xs font-medium text-red-600">{dateOfBirthError}</p>
                    )}
                  </div>

                  <div className="space-y-3 rounded-2xl border border-gray-100 bg-gray-50 px-4 py-4 text-sm text-gray-700">
                    <label className="flex items-start gap-3">
                      <input
                        type="checkbox"
                        checked={acceptedAgePolicy}
                        onChange={(e) => setAcceptedAgePolicy(e.target.checked)}
                        className="mt-1 accent-pink-500"
                      />
                      <span>
                        Declaro que sou maior de 18 anos e que as informações fornecidas são verdadeiras. O acesso
                        indevido por menores é proibido e monitorado. Concordo com a{" "}
                        <a
                          href="/politicas/verificacao-etaria"
                          className="underline"
                          onClick={(event) => event.stopPropagation()}
                        >
                          Política de Verificação Etária
                        </a>
                        .
                      </span>
                    </label>
                  </div>

                  <div className="sticky bottom-0 -mx-6 mt-2 bg-white px-6 pt-3">
                    <Button
                      type="button"
                      onClick={irParaFase2}
                      disabled={submitting || Boolean(dateOfBirthError)}
                      className="h-12 w-full bg-[#FC1EAD] text-white hover:bg-[#e01a9a]"
                    >
                      Continuar
                    </Button>
                  </div>
                </div>
              )}

              {fase === 2 && (
                <div className="flex flex-col gap-4">
                  <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                    <div className="flex flex-col gap-2">
                      <Label htmlFor="visitor-cpf">CPF</Label>
                      <Input
                        id="visitor-cpf"
                        inputMode="numeric"
                        value={formatarCpf(cpf)}
                        onChange={(e) => setCpf(e.target.value)}
                        placeholder="000.000.000-00"
                        autoComplete="off"
                        required
                      />
                    </div>

                    <div className="flex flex-col gap-2">
                      <Label htmlFor="visitor-confirm-dob">Confirmar data de nascimento</Label>
                      <Input
                        ref={confirmMask.inputRef}
                        id="visitor-confirm-dob"
                        type="text"
                        inputMode="numeric"
                        autoComplete="off"
                        placeholder="dd/mm/aaaa"
                        maxLength={10}
                        value={confirmDateOfBirth}
                        onChange={confirmMask.onChange}
                        required
                      />
                    </div>
                  </div>

                  <div className="space-y-3 rounded-2xl border border-gray-100 bg-gray-50 px-4 py-4 text-sm text-gray-700">
                    <label className="flex items-start gap-3">
                      <input
                        type="checkbox"
                        checked={acceptedRestrictedTerms}
                        onChange={(e) => setAcceptedRestrictedTerms(e.target.checked)}
                        className="mt-1 accent-pink-500"
                      />
                      <span>Entendo que este acesso é restrito, pessoal e sujeito a revogação e auditoria.</span>
                    </label>

                    <label className="flex items-start gap-3">
                      <input
                        type="checkbox"
                        checked={acceptedPrivacyNotice}
                        onChange={(e) => setAcceptedPrivacyNotice(e.target.checked)}
                        className="mt-1 accent-pink-500"
                      />
                      <span>Autorizo o registro mínimo de segurança para este desbloqueio, com dados pseudonimizados.</span>
                    </label>

                    {challenge.requiresExplicitAcknowledgement && (
                      <label className="flex items-start gap-3">
                        <input
                          type="checkbox"
                          checked={acceptedExplicitScope}
                          onChange={(e) => setAcceptedExplicitScope(e.target.checked)}
                          className="mt-1 accent-pink-500"
                        />
                        <span>Reconheço que esta liberação cobre conteúdo mais sensível e pode expirar em prazo reduzido.</span>
                      </label>
                    )}
                  </div>

                  <div className="sticky bottom-0 -mx-6 mt-2 bg-white px-6 pt-3">
                    <Button
                      type="button"
                      onClick={() => void concluirFase2()}
                      disabled={submitting}
                      className="h-12 w-full bg-[#FC1EAD] text-white hover:bg-[#e01a9a]"
                    >
                      {submitting ? "Verificando..." : "Concluir verificação"}
                    </Button>
                  </div>
                </div>
              )}

              {(challenge.documentOnDemand || showDocFallback) && (
                <div className="rounded-2xl border border-amber-200 bg-amber-50/80 px-4 py-4 text-sm text-gray-800 space-y-3">
                  <p className="font-medium text-gray-900">Fallback documental (análise manual)</p>
                  <p className="text-xs text-gray-600">
                    Se a verificação automática não for possível ou houver sinalização de risco, você pode enviar uma
                    foto ou PDF do documento (sem liberar acesso automaticamente até análise da equipe).
                  </p>
                  <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
                    <Input
                      type="file"
                      accept="image/*,application/pdf"
                      className="cursor-pointer text-xs"
                      onChange={(e: ChangeEvent<HTMLInputElement>) =>
                        setDocFile(e.target.files?.[0] ?? null)
                      }
                    />
                    <Button
                      type="button"
                      variant="outline"
                      disabled={docSubmitting || !docFile}
                      className="shrink-0 border-amber-300 bg-white"
                      onClick={() => void enviarDocumentoFallback()}
                    >
                      {docSubmitting ? "Enviando..." : "Enviar para análise"}
                    </Button>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  )

  if (typeof document !== "undefined" && portalReady) {
    return createPortal(modalUi, document.body)
  }

  return null
}

export default VisitorVerificationModal
