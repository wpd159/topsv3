'use client'

import { useEffect, useState, type FormEvent } from 'react'
import {
  CheckCircleIcon,
  ExclamationTriangleIcon,
} from '@heroicons/react/24/outline'
import { PasswordInput } from '@/components/auth/password-input'
import {
  PasswordRequirements,
  passwordMeetsPolicy,
} from '@/components/auth/password-requirements'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { ApiContractError } from '@/lib/api-contract'
import { changeAdminPassword } from '@/lib/admin-auth-api'

type AdminChangePasswordDialogProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
}

function passwordChangeErrorMessage(error: unknown) {
  if (!(error instanceof ApiContractError)) {
    return 'Não foi possível alterar a senha agora. Tente novamente.'
  }
  if (error.kind === 'SESSION_REQUIRED' && error.message !== 'Senha atual incorreta.') {
    return 'Sua sessão expirou. Entre novamente.'
  }
  return error.message
}

export function AdminChangePasswordDialog({
  open,
  onOpenChange,
}: AdminChangePasswordDialogProps) {
  const [currentCredential, setCurrentCredential] = useState('')
  const [nextCredential, setNextCredential] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)

  useEffect(() => {
    if (open) return
    setCurrentCredential('')
    setNextCredential('')
    setConfirmation('')
    setSubmitting(false)
    setError(null)
    setSuccess(false)
  }, [open])

  const policySatisfied = passwordMeetsPolicy(nextCredential)
  const confirmationStarted = confirmation.length > 0
  const valuesMatch = confirmationStarted && nextCredential === confirmation
  const canSubmit =
    currentCredential.length > 0 && policySatisfied && valuesMatch && !submitting

  function goToAdminLogin() {
    window.location.assign('/admin/login')
  }

  function handleOpenChange(nextOpen: boolean) {
    if (!nextOpen && success) {
      goToAdminLogin()
      return
    }
    onOpenChange(nextOpen)
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!canSubmit) return

    setSubmitting(true)
    setError(null)
    try {
      await changeAdminPassword(currentCredential, nextCredential, confirmation)
      setSuccess(true)
    } catch (cause) {
      setError(passwordChangeErrorMessage(cause))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent
        className="max-h-[calc(100dvh-2rem)] overflow-y-auto rounded-lg sm:max-w-md"
        showCloseButton={!success}
      >
        {success ? (
          <div className="flex flex-col items-center py-5 text-center">
            <CheckCircleIcon
              className="mb-3 h-12 w-12 text-emerald-600"
              aria-hidden="true"
            />
            <DialogTitle>Senha alterada com sucesso.</DialogTitle>
            <DialogDescription className="mt-2">
              Entre novamente com sua nova senha.
            </DialogDescription>
            <Button className="mt-6 w-full" onClick={goToAdminLogin}>
              Voltar ao login
            </Button>
          </div>
        ) : (
          <>
            <DialogHeader>
              <DialogTitle>Alterar senha</DialogTitle>
              <DialogDescription>
                A nova senha encerrará as sessões atuais da conta.
              </DialogDescription>
            </DialogHeader>

            <form className="mt-2 space-y-4" onSubmit={submit}>
              <div className="space-y-2">
                <Label htmlFor="admin-current-password">Senha atual</Label>
                <PasswordInput
                  id="admin-current-password"
                  name="currentPassword"
                  autoComplete="current-password"
                  value={currentCredential}
                  onChange={setCurrentCredential}
                  placeholder="Informe sua senha atual"
                  visibilityContext="senha atual"
                  disabled={submitting}
                  required
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="admin-new-password">Nova senha</Label>
                <PasswordInput
                  id="admin-new-password"
                  name="newPassword"
                  autoComplete="new-password"
                  value={nextCredential}
                  onChange={setNextCredential}
                  placeholder="Crie uma nova senha"
                  visibilityContext="nova senha"
                  disabled={submitting}
                  describedBy="admin-password-requirements"
                  required
                />
              </div>

              <div className="rounded-md border border-zinc-200 bg-zinc-50 px-3 py-3">
                <PasswordRequirements
                  id="admin-password-requirements"
                  value={nextCredential}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="admin-confirm-password">Confirmar nova senha</Label>
                <PasswordInput
                  id="admin-confirm-password"
                  name="confirmPassword"
                  autoComplete="new-password"
                  value={confirmation}
                  onChange={setConfirmation}
                  placeholder="Repita a nova senha"
                  visibilityContext="confirmação da nova senha"
                  disabled={submitting}
                  invalid={confirmationStarted && !valuesMatch}
                  describedBy={confirmationStarted ? 'admin-password-match' : undefined}
                  required
                />
                {confirmationStarted ? (
                  <p
                    id="admin-password-match"
                    className={`flex items-center gap-2 text-xs font-medium ${
                      valuesMatch ? 'text-emerald-700' : 'text-red-600'
                    }`}
                    aria-live="polite"
                  >
                    {valuesMatch ? (
                      <CheckCircleIcon className="h-4 w-4" aria-hidden="true" />
                    ) : (
                      <ExclamationTriangleIcon className="h-4 w-4" aria-hidden="true" />
                    )}
                    {valuesMatch ? 'As senhas coincidem.' : 'As senhas não coincidem.'}
                  </p>
                ) : null}
              </div>

              {error ? (
                <p role="alert" className="text-sm text-red-700">
                  {error}
                </p>
              ) : null}

              <div className="flex gap-3 pt-1">
                <Button
                  type="button"
                  variant="outline"
                  className="min-w-0 flex-1"
                  onClick={() => handleOpenChange(false)}
                  disabled={submitting}
                >
                  Cancelar
                </Button>
                <Button
                  type="submit"
                  className="min-w-0 flex-1"
                  disabled={!canSubmit}
                >
                  {submitting ? 'Alterando...' : 'Alterar senha'}
                </Button>
              </div>
            </form>
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}
