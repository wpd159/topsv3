"use client"

import {
  useEffect,
  useRef,
  useState,
  type ChangeEvent,
} from "react"
import { createPortal } from "react-dom"
import { XMarkIcon } from "@heroicons/react/24/solid"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  createVisitorChallenge,
  newIdempotencyKey,
  submitVisitorDocument,
  verifyVisitor,
  type VisitorAccessStatus,
  type VisitorChallenge,
} from "@/lib/compliance/age-gate-api"
import {
  notificarMudancaVerificacao,
  obterStatusVisitante,
  recarregarStatusVisitante,
  statusSatisfazEscopo,
  type StatusVisitante,
} from "@/lib/compliance/visitor-access"

type VisitorVerificationModalProps = {
  open: boolean
  level?: "LIGHT" | "REINFORCED" | "STRONG"
  scope?: "MIDIA_RESTRITA" | "WHATSAPP" | "STORY" | "CONTEUDO_EXPLICITO"
  context?: {
    anuncioId?: string | number
    route?: string
    midiaId?: string
    storyId?: string
  }
  onOpenChange: (open: boolean) => void
  onVerified?: (status: StatusVisitante) => void
}

type Step = "loading" | "birth" | "identity" | "document" | "blocked"

function formatDate(value: string) {
  const digits = value.replace(/\D+/g, "").slice(0, 8)
  if (digits.length <= 2) return digits
  if (digits.length <= 4) return `${digits.slice(0, 2)}/${digits.slice(2)}`
  return `${digits.slice(0, 2)}/${digits.slice(2, 4)}/${digits.slice(4)}`
}

function formatCpf(value: string) {
  const digits = value.replace(/\D+/g, "").slice(0, 11)
  if (digits.length <= 3) return digits
  if (digits.length <= 6) return `${digits.slice(0, 3)}.${digits.slice(3)}`
  if (digits.length <= 9) {
    return `${digits.slice(0, 3)}.${digits.slice(3, 6)}.${digits.slice(6)}`
  }
  return `${digits.slice(0, 3)}.${digits.slice(3, 6)}.${digits.slice(6, 9)}-${digits.slice(9)}`
}

function validDate(value: string) {
  const match = value.match(/^(\d{2})\/(\d{2})\/(\d{4})$/)
  if (!match) return false
  const day = Number(match[1])
  const month = Number(match[2])
  const year = Number(match[3])
  const parsed = new Date(Date.UTC(year, month - 1, day))
  return parsed.getUTCFullYear() === year
    && parsed.getUTCMonth() === month - 1
    && parsed.getUTCDate() === day
}

function mapStatus(status: VisitorAccessStatus): StatusVisitante {
  return {
    globalAccepted: status.globalAccepted,
    verified: status.verified,
    level: status.level,
    expiresAt: status.expiresAt,
    explicitVerified: status.explicitVerified,
    explicitLevel: status.explicitLevel,
    explicitExpiresAt: status.explicitExpiresAt,
    state: status.state,
    riskScore: status.riskScore,
    decision: status.decision,
    documentStatus: status.documentStatus,
    reasonPublic: status.reasonPublic,
  }
}

export function VisitorVerificationModal({
  open,
  level = "REINFORCED",
  scope = "MIDIA_RESTRITA",
  context,
  onOpenChange,
  onVerified,
}: VisitorVerificationModalProps) {
  const [step, setStep] = useState<Step>("loading")
  const [challenge, setChallenge] = useState<VisitorChallenge | null>(null)
  const [birthDate, setBirthDate] = useState("")
  const [birthDateConfirmation, setBirthDateConfirmation] = useState("")
  const [cpf, setCpf] = useState("")
  const [adultAccepted, setAdultAccepted] = useState(false)
  const [restrictedAccepted, setRestrictedAccepted] = useState(false)
  const [privacyAccepted, setPrivacyAccepted] = useState(false)
  const [explicitAccepted, setExplicitAccepted] = useState(false)
  const [documentFile, setDocumentFile] = useState<File | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const challengeKeyRef = useRef<string | null>(null)
  const verifyKeyRef = useRef<string | null>(null)
  const documentKeyRef = useRef<string | null>(null)
  const callbacksRef = useRef({ onOpenChange, onVerified })
  callbacksRef.current = { onOpenChange, onVerified }

  useEffect(() => {
    if (!open) {
      setStep("loading")
      setChallenge(null)
      setBirthDate("")
      setBirthDateConfirmation("")
      setCpf("")
      setAdultAccepted(false)
      setRestrictedAccepted(false)
      setPrivacyAccepted(false)
      setExplicitAccepted(false)
      setDocumentFile(null)
      setSubmitting(false)
      setError(null)
      setMessage(null)
      challengeKeyRef.current = null
      verifyKeyRef.current = null
      documentKeyRef.current = null
      return
    }
    let active = true
    setStep("loading")
    setError(null)
    setMessage(null)
    void (async () => {
      const global = await obterStatusVisitante(true)
      if (!global.globalAccepted) {
        throw new Error("Aceite o aviso geral antes de acessar o conteudo protegido.")
      }
      if (statusSatisfazEscopo(global, scope, level)) {
        if (!active) return
        notificarMudancaVerificacao(global)
        callbacksRef.current.onVerified?.(global)
        callbacksRef.current.onOpenChange(false)
        return
      }
      const storyContext = scope === "STORY"
      if (storyContext && !context?.storyId) {
        throw new Error("O contexto protegido do Story nao esta disponivel.")
      }
      if (!storyContext && !context?.anuncioId) {
        throw new Error("O contexto protegido do anuncio nao esta disponivel.")
      }
      const challengeKey = challengeKeyRef.current
        ?? newIdempotencyKey("visitor-challenge")
      challengeKeyRef.current = challengeKey
      const created = await createVisitorChallenge({
        level,
        scope,
        anuncioId: storyContext ? undefined : String(context?.anuncioId),
        midiaId: storyContext ? undefined : context?.midiaId || undefined,
        storyId: storyContext ? context?.storyId : undefined,
        route: context?.route || "/",
        idempotencyKey: challengeKey,
      })
      if (!active) return
      setChallenge(created)
      if (created.state === "BLOCKED") {
        setStep("blocked")
        setError(created.reasonPublic || "Verificacao temporariamente indisponivel.")
      } else {
        setStep("birth")
      }
    })().catch((nextError) => {
      if (!active) return
      setStep("blocked")
      setError(nextError instanceof Error
        ? nextError.message
        : "Nao foi possivel iniciar a verificacao.")
    })
    return () => {
      active = false
    }
  }, [
    context?.anuncioId,
    context?.midiaId,
    context?.route,
    context?.storyId,
    level,
    open,
    scope,
  ])

  function advanceBirth() {
    if (!validDate(birthDate) || !validDate(birthDateConfirmation)) {
      setError("Informe as duas datas no formato dd/mm/aaaa.")
      return
    }
    if (birthDate !== birthDateConfirmation) {
      setError("As datas de nascimento devem ser iguais.")
      return
    }
    setError(null)
    setStep("identity")
  }

  async function verify() {
    if (!challenge) return
    if (cpf.replace(/\D+/g, "").length !== 11) {
      setError("Informe um CPF valido.")
      return
    }
    if (!adultAccepted || !restrictedAccepted || !privacyAccepted) {
      setError("Confirme todos os aceites obrigatorios.")
      return
    }
    if (challenge.requiresExplicitAcknowledgement && !explicitAccepted) {
      setError("Confirme o acesso ao conteudo explicito.")
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      const verifyKey = verifyKeyRef.current
        ?? newIdempotencyKey("visitor-verify")
      verifyKeyRef.current = verifyKey
      const status = await verifyVisitor({
        challengeId: challenge.challengeId,
        dataNascimento: birthDate,
        confirmacaoDataNascimento: birthDateConfirmation,
        cpf,
        aceiteMaioridade: adultAccepted,
        aceiteConteudoRestrito: restrictedAccepted,
        aceitePrivacidade: privacyAccepted,
        confirmacaoExplicita: explicitAccepted,
        idempotencyKey: verifyKey,
      })
      if (status.state === "DOCUMENT_PENDING") {
        setStep("document")
        setMessage(status.reasonPublic || "Envie um documento para analise manual.")
        return
      }
      if (!status.verified) {
        throw new Error(status.reasonPublic || "A verificacao nao foi concluida.")
      }
      const mapped = mapStatus(status)
      notificarMudancaVerificacao(mapped)
      toast.success("Verificacao concluida.")
      onVerified?.(mapped)
      onOpenChange(false)
    } catch (nextError) {
      setError(nextError instanceof Error
        ? nextError.message
        : "Nao foi possivel concluir a verificacao.")
    } finally {
      setSubmitting(false)
    }
  }

  async function submitDocument() {
    if (!challenge || !documentFile) {
      setError("Selecione uma imagem ou PDF.")
      return
    }
    if (documentFile.size > 12 * 1024 * 1024) {
      setError("O arquivo deve ter no maximo 12 MB.")
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      const documentKey = documentKeyRef.current
        ?? newIdempotencyKey("visitor-document")
      documentKeyRef.current = documentKey
      const result = await submitVisitorDocument(
        challenge.challengeId,
        documentFile,
        documentKey,
      )
      setMessage(result.reasonPublic || "Documento recebido para analise.")
      setDocumentFile(null)
    } catch (nextError) {
      setError(nextError instanceof Error
        ? nextError.message
        : "Nao foi possivel enviar o documento.")
    } finally {
      setSubmitting(false)
    }
  }

  async function refreshDocumentStatus() {
    setSubmitting(true)
    setError(null)
    try {
      const status = await recarregarStatusVisitante()
      if (status.state === "DOCUMENT_APPROVED") {
        setStep("identity")
        setMessage("Documento aprovado. Confirme novamente para emitir o acesso.")
        return
      }
      if (status.state === "DOCUMENT_REJECTED") {
        documentKeyRef.current = null
        setMessage(status.reasonPublic || "Documento rejeitado. Envie um novo arquivo.")
        return
      }
      setMessage("O documento ainda esta em analise.")
    } catch (nextError) {
      setError(nextError instanceof Error
        ? nextError.message
        : "Nao foi possivel consultar a analise.")
    } finally {
      setSubmitting(false)
    }
  }

  if (!open || typeof document === "undefined") return null

  return createPortal(
    <div
      className="fixed inset-0 z-[120] overflow-y-auto bg-black/60 px-4 py-6"
      role="dialog"
      aria-modal="true"
      onClick={() => onOpenChange(false)}
    >
      <div className="mx-auto flex min-h-full w-full max-w-[520px] items-center justify-center">
        <div
          className="w-full max-h-[92vh] overflow-y-auto rounded-2xl bg-white p-5 shadow-2xl sm:p-6"
          onClick={(event) => event.stopPropagation()}
        >
          <div className="mb-5 flex items-start justify-between gap-4">
            <div>
              <h2 className="text-xl font-semibold text-gray-900">
                Verificacao para conteudo restrito
              </h2>
              <p className="mt-1 text-sm text-gray-600">
                Seus dados sao verificados pelo servidor e nao ficam gravados no navegador.
              </p>
            </div>
            <button
              type="button"
              onClick={() => onOpenChange(false)}
              className="rounded-full border border-gray-200 p-2 text-gray-500 hover:bg-gray-50"
              aria-label="Fechar"
            >
              <XMarkIcon className="h-5 w-5" />
            </button>
          </div>

          {error ? (
            <p role="alert" className="mb-4 rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700">
              {error}
            </p>
          ) : null}
          {message ? (
            <p className="mb-4 rounded-md border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
              {message}
            </p>
          ) : null}

          {step === "loading" ? (
            <p className="py-8 text-center text-sm text-gray-600">Preparando verificacao...</p>
          ) : null}

          {step === "birth" ? (
            <div className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="visitor-date-of-birth">Data de nascimento</Label>
                <Input
                  id="visitor-date-of-birth"
                  inputMode="numeric"
                  autoComplete="bday"
                  placeholder="dd/mm/aaaa"
                  maxLength={10}
                  value={birthDate}
                  onChange={(event: ChangeEvent<HTMLInputElement>) =>
                    setBirthDate(formatDate(event.target.value))}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="visitor-date-of-birth-confirmation">
                  Confirme a data de nascimento
                </Label>
                <Input
                  id="visitor-date-of-birth-confirmation"
                  inputMode="numeric"
                  placeholder="dd/mm/aaaa"
                  maxLength={10}
                  value={birthDateConfirmation}
                  onChange={(event: ChangeEvent<HTMLInputElement>) =>
                    setBirthDateConfirmation(formatDate(event.target.value))}
                />
              </div>
              <Button type="button" onClick={advanceBirth} className="h-11 w-full">
                Continuar
              </Button>
            </div>
          ) : null}

          {step === "identity" ? (
            <div className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="visitor-cpf">CPF</Label>
                <Input
                  id="visitor-cpf"
                  inputMode="numeric"
                  autoComplete="off"
                  placeholder="000.000.000-00"
                  maxLength={14}
                  value={cpf}
                  onChange={(event) => setCpf(formatCpf(event.target.value))}
                />
              </div>
              <Acceptance
                checked={adultAccepted}
                onChange={setAdultAccepted}
                text="Confirmo que tenho 18 anos ou mais."
              />
              <Acceptance
                checked={restrictedAccepted}
                onChange={setRestrictedAccepted}
                text="Estou ciente de que acessarei conteudo restrito a adultos."
              />
              <Acceptance
                checked={privacyAccepted}
                onChange={setPrivacyAccepted}
                text="Li e aceito o tratamento de dados para esta verificacao."
              />
              {challenge?.requiresExplicitAcknowledgement ? (
                <Acceptance
                  checked={explicitAccepted}
                  onChange={setExplicitAccepted}
                  text="Confirmo expressamente o acesso a este conteudo."
                />
              ) : null}
              <div className="grid grid-cols-2 gap-3">
                <Button type="button" variant="outline" onClick={() => setStep("birth")}>
                  Voltar
                </Button>
                <Button type="button" onClick={() => void verify()} disabled={submitting}>
                  {submitting ? "Verificando..." : "Verificar"}
                </Button>
              </div>
            </div>
          ) : null}

          {step === "document" ? (
            <div className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="visitor-document">Documento para analise</Label>
                <Input
                  id="visitor-document"
                  type="file"
                  accept="image/jpeg,image/png,application/pdf"
                  onChange={(event) => {
                    setDocumentFile(event.target.files?.[0] ?? null)
                    documentKeyRef.current = null
                  }}
                />
                <p className="text-xs text-gray-500">
                  Envie exatamente uma imagem ou PDF de ate 12 MB.
                </p>
              </div>
              <Button
                type="button"
                onClick={() => void submitDocument()}
                disabled={submitting || !documentFile}
                className="w-full"
              >
                {submitting ? "Enviando..." : "Enviar documento"}
              </Button>
              <Button
                type="button"
                variant="outline"
                onClick={() => void refreshDocumentStatus()}
                disabled={submitting}
                className="w-full"
              >
                Atualizar situacao da analise
              </Button>
            </div>
          ) : null}

          {step === "blocked" ? (
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)} className="w-full">
              Fechar
            </Button>
          ) : null}
        </div>
      </div>
    </div>,
    document.body,
  )
}

function Acceptance({
  checked,
  onChange,
  text,
}: {
  checked: boolean
  onChange: (checked: boolean) => void
  text: string
}) {
  return (
    <label className="flex items-start gap-3 rounded-md border border-gray-200 bg-gray-50 p-3 text-sm text-gray-700">
      <input
        type="checkbox"
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
        className="mt-0.5 accent-pink-500"
      />
      <span>{text}</span>
    </label>
  )
}

export default VisitorVerificationModal
