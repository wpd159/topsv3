'use client'

import { useEffect, useRef, useState } from 'react'
import { KeyRound, ShieldAlert, Trash2 } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { toast } from 'sonner'
import { PasswordInput } from '@/components/auth/password-input'
import {
  PasswordRequirements,
  passwordMeetsPolicy,
} from '@/components/auth/password-requirements'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useAuth } from '@/context/AuthContext'
import {
  changeMyAccountPassword,
  deleteMyAccount,
  getMyAccountDeletionEligibility,
  PublicAuthApiError,
  type MyAccountDeletionEligibility,
} from '@/lib/public-auth-api'

const DELETE_CONFIRMATION = 'EXCLUIR MINHA CONTA'

function errorMessage(error: unknown, fallback: string) {
  return error instanceof PublicAuthApiError
    ? error.message
    : fallback
}

export function MinhaContaSeguranca() {
  const router = useRouter()
  const { refresh } = useAuth()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [passwordBusy, setPasswordBusy] = useState(false)
  const [passwordError, setPasswordError] = useState<string | null>(null)
  const passwordLock = useRef(false)

  const [deleteOpen, setDeleteOpen] = useState(false)
  const [eligibility, setEligibility] = useState<MyAccountDeletionEligibility | null>(null)
  const [eligibilityLoading, setEligibilityLoading] = useState(false)
  const [deletePassword, setDeletePassword] = useState('')
  const [deleteConfirmation, setDeleteConfirmation] = useState('')
  const [consent, setConsent] = useState(false)
  const [deleteBusy, setDeleteBusy] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const deleteLock = useRef(false)
  const idempotencyKey = useRef('')

  useEffect(() => {
    if (!deleteOpen) {
      setEligibility(null)
      setDeletePassword('')
      setDeleteConfirmation('')
      setConsent(false)
      setDeleteError(null)
      idempotencyKey.current = ''
      return
    }
    idempotencyKey.current ||= crypto.randomUUID()
    let active = true
    setEligibilityLoading(true)
    getMyAccountDeletionEligibility()
      .then((result) => {
        if (active) setEligibility(result)
      })
      .catch((error) => {
        if (active) setDeleteError(errorMessage(
          error,
          'Não foi possível analisar a exclusão da conta. Tente novamente.'
        ))
      })
      .finally(() => {
        if (active) setEligibilityLoading(false)
      })
    return () => {
      active = false
    }
  }, [deleteOpen])

  async function changePassword(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (
      passwordLock.current
      || !passwordMeetsPolicy(newPassword)
      || newPassword !== confirmation
    ) return
    passwordLock.current = true
    setPasswordBusy(true)
    setPasswordError(null)
    try {
      await changeMyAccountPassword({
        ['senhaAtual']: currentPassword,
        ['novaSenha']: newPassword,
        ['confirmarSenha']: confirmation,
      })
      toast.success('Sua senha foi alterada com sucesso. Entre novamente.')
      await refresh()
      router.replace('/entrar')
    } catch (error) {
      const message = errorMessage(
        error,
        'Não foi possível alterar sua senha. Tente novamente.'
      )
      setPasswordError(message)
      toast.error(message)
    } finally {
      passwordLock.current = false
      setPasswordBusy(false)
    }
  }

  async function confirmDeletion() {
    if (
      deleteLock.current
      || !eligibility?.podeExcluir
      || !consent
      || deleteConfirmation !== DELETE_CONFIRMATION
      || !deletePassword
    ) return
    deleteLock.current = true
    setDeleteBusy(true)
    setDeleteError(null)
    try {
      await deleteMyAccount({
        senhaAtual: deletePassword,
        confirmacao: deleteConfirmation,
        cienteConsequencias: consent,
      }, idempotencyKey.current)
      toast.success('Sua conta foi excluída.')
      await refresh()
      router.replace('/')
    } catch (error) {
      const message = errorMessage(
        error,
        'Não foi possível excluir sua conta. Tente novamente.'
      )
      setDeleteError(message)
      toast.error(message)
    } finally {
      deleteLock.current = false
      setDeleteBusy(false)
    }
  }

  const anonymized = eligibility?.tipoExclusao === 'EXCLUSAO_COM_ANONIMIZACAO'
  const deletionTitle = anonymized
    ? 'Encerrar e anonimizar minha conta'
    : 'Excluir minha conta'
  const deletionDescription = anonymized
    ? 'Seu acesso será encerrado e seus dados pessoais serão anonimizados. Registros que precisam ser preservados por segurança, auditoria, obrigações legais ou integridade financeira permanecerão sem identificação pessoal.'
    : 'Sua conta será excluída definitivamente. Você perderá o acesso e essa ação não poderá ser desfeita.'

  return (
    <>
      <Card className="mx-auto max-w-3xl border border-slate-200 shadow-sm">
        <CardContent className="p-6 md:p-8">
          <div className="flex items-center gap-3 border-b border-slate-100 pb-5">
            <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-pink-50 text-[#FC1EAD]">
              <KeyRound className="h-5 w-5" />
            </span>
            <div>
              <h2 className="text-xl font-bold text-slate-900">Segurança</h2>
              <p className="text-sm text-slate-500">
                Proteja seu acesso e encerre sessões antigas ao trocar sua credencial.
              </p>
            </div>
          </div>

          <form className="mt-6 space-y-5" onSubmit={changePassword}>
            <div>
              <h3 className="font-semibold text-slate-900">Alterar senha</h3>
              <p className="mt-1 text-sm text-slate-500">
                A alteração encerra todas as sessões e invalida códigos de segurança ativos.
              </p>
            </div>
            <div className="space-y-2">
              <Label htmlFor="account-current-password">Senha atual</Label>
              <PasswordInput
                id="account-current-password"
                name="senhaAtual"
                value={currentPassword}
                onChange={setCurrentPassword}
                placeholder="Informe sua senha atual"
                visibilityContext="senha atual"
                autoComplete="current-password"
                disabled={passwordBusy}
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="account-new-password">Nova senha</Label>
              <PasswordInput
                id="account-new-password"
                name="novaSenha"
                value={newPassword}
                onChange={setNewPassword}
                placeholder="Crie uma nova senha"
                visibilityContext="nova senha"
                describedBy="account-password-requirements"
                disabled={passwordBusy}
                required
              />
              <PasswordRequirements id="account-password-requirements" value={newPassword} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="account-password-confirmation">Confirmar nova senha</Label>
              <PasswordInput
                id="account-password-confirmation"
                name="confirmarSenha"
                value={confirmation}
                onChange={setConfirmation}
                placeholder="Repita a nova senha"
                visibilityContext="confirmação da nova senha"
                invalid={Boolean(confirmation && confirmation !== newPassword)}
                disabled={passwordBusy}
                required
              />
              {confirmation && confirmation !== newPassword ? (
                <p role="alert" className="text-xs font-medium text-red-700">
                  As senhas não coincidem.
                </p>
              ) : null}
            </div>
            {passwordError ? (
              <p role="alert" className="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">
                {passwordError}
              </p>
            ) : null}
            <div className="flex justify-end">
              <Button
                type="submit"
                disabled={
                  passwordBusy
                  || !currentPassword
                  || !passwordMeetsPolicy(newPassword)
                  || confirmation !== newPassword
                }
              >
                {passwordBusy ? 'Alterando...' : 'Alterar senha'}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      <Card className="mx-auto max-w-3xl border border-red-200 shadow-sm">
        <CardContent className="flex flex-col gap-4 p-6 md:flex-row md:items-center md:justify-between md:p-8">
          <div className="flex min-w-0 items-start gap-3">
            <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-red-50 text-red-700">
              <ShieldAlert className="h-5 w-5" />
            </span>
            <div>
              <p className="text-sm font-semibold uppercase text-red-700">Zona de risco</p>
              <h2 className="text-xl font-bold text-slate-900">Excluir conta</h2>
              <p className="mt-1 text-sm leading-6 text-slate-600">
                Você pode encerrar sua conta e remover seus dados pessoais. Essa ação é irreversível.
                Alguns registros poderão ser preservados de forma anonimizada quando necessários para
                segurança, auditoria, obrigações legais ou integridade financeira.
              </p>
            </div>
          </div>
          <Button type="button" variant="destructive" onClick={() => setDeleteOpen(true)}>
            <Trash2 className="mr-2 h-4 w-4" />
            Excluir minha conta
          </Button>
        </CardContent>
      </Card>

      <Dialog open={deleteOpen} onOpenChange={(open) => { if (!deleteBusy) setDeleteOpen(open) }}>
        <DialogContent className="max-h-[calc(100dvh-2rem)] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{deletionTitle}</DialogTitle>
            <DialogDescription>
              {eligibility ? deletionDescription : 'Analisando o tratamento seguro para esta conta.'}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            {eligibilityLoading ? (
              <p className="text-sm text-slate-500">Analisando vínculos da conta...</p>
            ) : null}
            {eligibility ? (
              <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-700">
                <p className="font-semibold text-slate-900">
                  {anonymized ? 'Exclusão com anonimização' : 'Exclusão física'}
                </p>
                <ul className="mt-2 list-disc space-y-1 pl-5">
                  <li>Anúncios serão retirados e Stories serão encerrados.</li>
                  <li>Benefícios deixarão de produzir efeitos.</li>
                  <li>Saldo não será transferido.</li>
                  <li>Pagamentos e lançamentos necessários não serão apagados.</li>
                  <li>Documentos KYC não ficarão acessíveis.</li>
                  <li>Todas as sessões serão encerradas.</li>
                  <li>E-mail, CPF e telefone serão liberados.</li>
                  <li>Uma nova conta não herdará dados, saldo ou histórico.</li>
                  <li>A operação é irreversível.</li>
                </ul>
              </div>
            ) : null}
            {eligibility?.bloqueios.length ? (
              <p role="alert" className="rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-900">
                {eligibility.bloqueios.includes('CONTA_STAFF')
                  ? 'Contas staff devem ser tratadas em Gerenciar staff.'
                  : 'Esta conta não pode ser excluída enquanto houver uma operação concorrente.'}
              </p>
            ) : null}
            {eligibility?.podeExcluir ? (
              <>
                <div className="space-y-2">
                  <Label htmlFor="delete-account-password">Senha atual</Label>
                  <PasswordInput
                    id="delete-account-password"
                    name="senhaAtualExclusao"
                    value={deletePassword}
                    onChange={setDeletePassword}
                    placeholder="Confirme sua senha"
                    visibilityContext="senha para exclusão"
                    autoComplete="current-password"
                    disabled={deleteBusy}
                    required
                  />
                </div>
                <label className="flex items-start gap-3 rounded-xl border border-slate-200 p-3 text-sm text-slate-700">
                  <input
                    type="checkbox"
                    checked={consent}
                    onChange={(event) => setConsent(event.target.checked)}
                    disabled={deleteBusy}
                    className="mt-0.5 h-4 w-4 accent-pink-600"
                  />
                  <span>Li e compreendi as consequências irreversíveis da exclusão.</span>
                </label>
                <div className="space-y-2">
                  <Label htmlFor="delete-account-confirmation">
                    Digite {DELETE_CONFIRMATION}
                  </Label>
                  <Input
                    id="delete-account-confirmation"
                    value={deleteConfirmation}
                    onChange={(event) => setDeleteConfirmation(event.target.value)}
                    autoComplete="off"
                    disabled={deleteBusy}
                  />
                </div>
              </>
            ) : null}
            {deleteError ? (
              <p role="alert" className="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">
                {deleteError}
              </p>
            ) : null}
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" disabled={deleteBusy} onClick={() => setDeleteOpen(false)}>
              Cancelar
            </Button>
            <Button
              type="button"
              variant="destructive"
              disabled={
                deleteBusy
                || !eligibility?.podeExcluir
                || !deletePassword
                || !consent
                || deleteConfirmation !== DELETE_CONFIRMATION
              }
              onClick={() => void confirmDeletion()}
            >
              {deleteBusy ? 'Excluindo...' : 'Excluir definitivamente'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
