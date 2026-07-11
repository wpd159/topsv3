"use client"

import { useState, type ChangeEvent } from "react"
import { createPortal } from "react-dom"
import { XMarkIcon } from "@heroicons/react/24/solid"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import type { StatusVisitante } from "@/lib/compliance/visitor-access"

type VisitorVerificationModalProps = {
  open: boolean
  level?: "LIGHT" | "REINFORCED" | "STRONG"
  context?: {
    anuncioId?: string | number
    route?: string
    midiaId?: string
  }
  onOpenChange: (open: boolean) => void
  onVerified?: (status: StatusVisitante) => void
}

type StatusIdade = {
  confirmada: boolean
  expiraEm?: string | null
  motivoPublico?: string | null
}

function apenasDigitos(value: string) {
  return value.replace(/\D+/g, "").slice(0, 8)
}

function formatarData(value: string) {
  const digits = apenasDigitos(value)
  if (digits.length <= 2) return digits
  if (digits.length <= 4) return `${digits.slice(0, 2)}/${digits.slice(2)}`
  return `${digits.slice(0, 2)}/${digits.slice(2, 4)}/${digits.slice(4)}`
}

function dataIso(value: string): string | null {
  const match = value.match(/^(\d{2})\/(\d{2})\/(\d{4})$/)
  if (!match) return null
  const day = Number(match[1])
  const month = Number(match[2])
  const year = Number(match[3])
  const parsed = new Date(year, month - 1, day)
  if (parsed.getFullYear() !== year || parsed.getMonth() !== month - 1 || parsed.getDate() !== day) return null
  return `${match[3]}-${match[2]}-${match[1]}`
}

function mensagemErro(payload: unknown) {
  if (payload && typeof payload === "object") {
    const candidate = payload as Record<string, unknown>
    for (const key of ["message", "detail", "error", "motivoPublico"]) {
      if (typeof candidate[key] === "string" && candidate[key]) return candidate[key] as string
    }
  }
  return "Não foi possível confirmar a idade."
}

export function VisitorVerificationModal({ open, onOpenChange, onVerified }: VisitorVerificationModalProps) {
  const [dataNascimento, setDataNascimento] = useState("")
  const [declaracao, setDeclaracao] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const api = process.env.NEXT_PUBLIC_API_URL

  const confirmar = async () => {
    const iso = dataIso(dataNascimento)
    if (!iso) {
      toast.error("Informe uma data de nascimento válida.")
      return
    }
    if (!declaracao) {
      toast.error("Confirme a declaração de maioridade.")
      return
    }
    if (!api) {
      toast.error("Confirmação de idade indisponível no momento.")
      return
    }

    setSubmitting(true)
    try {
      const response = await fetch(`${api}/api/public/idade/confirmar`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ dataNascimento: iso, declaracaoMaioridade: true }),
      })
      const payload = (await response.json().catch(() => null)) as StatusIdade | null
      if (!response.ok || !payload?.confirmada) throw new Error(mensagemErro(payload))

      toast.success("Idade confirmada. A mídia protegida pode ser solicitada ao servidor.")
      onOpenChange(false)
      onVerified?.({
        verified: true,
        explicitVerified: true,
        expiresAt: payload.expiraEm ?? undefined,
        explicitExpiresAt: payload.expiraEm ?? undefined,
      })
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Não foi possível confirmar a idade.")
    } finally {
      setSubmitting(false)
    }
  }

  if (!open || typeof document === "undefined") return null

  return createPortal(
    <div className="fixed inset-0 z-[120] bg-black/50 px-4 py-6" role="dialog" aria-modal="true" onClick={() => onOpenChange(false)}>
      <div className="mx-auto flex min-h-full w-full max-w-[480px] items-center justify-center">
        <div className="w-full max-h-[90vh] overflow-y-auto rounded-[28px] bg-white p-6 shadow-2xl" onClick={(event) => event.stopPropagation()}>
          <div className="mb-5 flex items-start justify-between gap-4">
            <div>
              <h2 className="text-xl font-semibold text-gray-900">Conteúdo restrito para maiores de 18 anos</h2>
              <p className="mt-1 text-sm text-gray-500">A mídia original só é entregue após confirmação válida pelo backend.</p>
            </div>
            <button type="button" onClick={() => onOpenChange(false)} className="rounded-full border border-gray-200 p-2 text-gray-500 transition hover:bg-gray-50" aria-label="Fechar">
              <XMarkIcon className="h-5 w-5" />
            </button>
          </div>

          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="visitor-date-of-birth">Data de nascimento</Label>
              <Input
                id="visitor-date-of-birth"
                inputMode="numeric"
                autoComplete="bday"
                placeholder="dd/mm/aaaa"
                maxLength={10}
                value={dataNascimento}
                onChange={(event: ChangeEvent<HTMLInputElement>) => setDataNascimento(formatarData(event.target.value))}
              />
            </div>

            <label className="flex items-start gap-3 rounded-2xl border border-gray-100 bg-gray-50 px-4 py-4 text-sm text-gray-700">
              <input type="checkbox" checked={declaracao} onChange={(event) => setDeclaracao(event.target.checked)} className="mt-1 accent-pink-500" />
              <span>Declaro que tenho 18 anos ou mais e que a data informada é verdadeira.</span>
            </label>

            <Button type="button" onClick={() => void confirmar()} disabled={submitting} className="h-12 w-full bg-[#FC1EAD] text-white hover:bg-[#e01a9a]">
              {submitting ? "Confirmando..." : "Confirmar maioridade"}
            </Button>
          </div>
        </div>
      </div>
    </div>,
    document.body
  )
}

export default VisitorVerificationModal
