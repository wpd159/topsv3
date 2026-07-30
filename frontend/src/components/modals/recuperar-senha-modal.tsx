'use client'

import { useEffect, useState, type FormEvent } from 'react'
import Image from 'next/image'
import {
  CheckCircleIcon,
  EnvelopeIcon,
  ExclamationTriangleIcon,
  KeyIcon,
} from '@heroicons/react/24/outline'
import { PasswordInput } from '@/components/auth/password-input'
import {
  PasswordRequirements,
  passwordMeetsPolicy,
} from '@/components/auth/password-requirements'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { toast } from 'sonner'
import { getPublicLogoUrl } from '@/lib/public-site-assets'
import { PublicAuthApiError, requestPublicPasswordReset, resetPublicCredential, validatePublicResetCode } from '@/lib/public-auth-api'

type Props = { open: boolean; onOpenChange: (value: boolean) => void }
const stepDescriptions = ['Digite seu e-mail para recuperar sua senha', 'Digite o código enviado para seu e-mail', 'Redefina sua senha', 'Senha redefinida']

function normalizedErrorMessage(error: PublicAuthApiError) {
  return error.message.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase()
}

function passwordResetErrorMessage(error: unknown) {
  if (!(error instanceof PublicAuthApiError)) {
    return 'Não foi possível redefinir a senha agora. Tente novamente.'
  }

  const message = normalizedErrorMessage(error)
  if (message.includes('senha nao atende')) {
    return 'A senha não atende aos requisitos de segurança exibidos.'
  }
  if (message.includes('senhas nao coincidem') || message.includes('confirmacao de senha')) {
    return 'As senhas não coincidem.'
  }
  if (message.includes('codigo ja utilizado')) {
    return 'Este código já foi utilizado. Solicite um novo código.'
  }
  if (message.includes('codigo expirado')) {
    return 'Este código expirou. Solicite um novo código.'
  }
  if (message.includes('codigo invalido')) {
    return 'O código informado é inválido.'
  }
  if (error.status >= 500) {
    return 'O serviço está temporariamente indisponível. Tente novamente.'
  }
  return error.message
}

export function RecuperarSenhaModal({ open, onOpenChange }: Props) {
  const [step, setStep] = useState(1)
  const [loading, setLoading] = useState(false)
  const [email, setEmail] = useState('')
  const [codigo, setCodigo] = useState('')
  const [candidate, setCandidate] = useState('')
  const [confirmation, setConfirmation] = useState('')

  useEffect(() => { if (!open) { setStep(1); setEmail(''); setCodigo(''); setCandidate(''); setConfirmation('') } }, [open])
  const report = (error: unknown, fallback: string) => toast.error(error instanceof PublicAuthApiError ? error.message : fallback)
  const policySatisfied = passwordMeetsPolicy(candidate)
  const confirmationStarted = confirmation.length > 0
  const valuesMatch = confirmationStarted && candidate === confirmation
  const canReset = policySatisfied && valuesMatch && !loading

  const requestCode = async () => {
    if (!email.trim()) { toast.warning('Digite seu e-mail.'); return }
    try { setLoading(true); await requestPublicPasswordReset(email.trim().toLowerCase()); toast.success('Se houver uma conta elegível, enviaremos as instruções.'); setStep(2) }
    catch (error) { report(error, 'Erro ao solicitar recuperação.') } finally { setLoading(false) }
  }
  const validateCode = async () => {
    if (!/^\d{6}$/.test(codigo)) { toast.warning('Digite o código de 6 dígitos.'); return }
    try { setLoading(true); await validatePublicResetCode(email.trim().toLowerCase(), codigo); toast.success('Código verificado com sucesso!'); setStep(3) }
    catch (error) { report(error, 'Falha ao validar código.') } finally { setLoading(false) }
  }
  const reset = async () => {
    if (!candidate || !confirmation) { toast.warning('Preencha todos os campos.'); return }
    if (!policySatisfied) { toast.warning('A senha ainda não atende aos requisitos de segurança.'); return }
    if (!valuesMatch) { toast.warning('As senhas não coincidem.'); return }
    try { setLoading(true); await resetPublicCredential(email.trim().toLowerCase(), codigo, candidate, confirmation); toast.success('Senha redefinida com sucesso!'); setStep(4) }
    catch (error) { toast.error(passwordResetErrorMessage(error)) } finally { setLoading(false) }
  }

  const submitReset = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (canReset) void reset()
  }

  return <Dialog open={open} onOpenChange={onOpenChange}><DialogContent className="max-h-[calc(100dvh-2rem)] overflow-y-auto rounded-xl p-5 sm:max-w-md sm:p-6">
    <DialogHeader><DialogTitle className="mb-2 flex justify-center"><Image src={getPublicLogoUrl()} alt="Tops do Job" width={150} height={50} priority unoptimized className="h-10 w-auto max-w-[180px] object-contain"/></DialogTitle>
      <DialogDescription className="text-center text-gray-600">{stepDescriptions[step - 1]}</DialogDescription></DialogHeader>
    {step === 1 && <div className="mt-4 space-y-5"><Field icon="email"><Input type="email" autoComplete="email" placeholder="Seu e-mail" className="pl-10 py-5" value={email} onChange={e => setEmail(e.target.value)} disabled={loading}/></Field><Action onClick={requestCode} loading={loading} label="Enviar código"/></div>}
    {step === 2 && <div className="mt-4 space-y-5"><Field icon="code"><Input inputMode="numeric" autoComplete="one-time-code" placeholder="Código de verificação" className="pl-10 py-5 tracking-widest text-center font-medium" maxLength={6} value={codigo} onChange={e => setCodigo(e.target.value.replace(/\D/g, '').slice(0, 6))} disabled={loading}/></Field><Nav back={() => setStep(1)} next={validateCode} loading={loading} label="Verificar código"/></div>}
    {step === 3 && <form className="mt-4 space-y-4" onSubmit={submitReset}>
      <PasswordInput
        value={candidate}
        onChange={setCandidate}
        placeholder="Nova senha"
        visibilityContext="nova senha"
        disabled={loading}
        describedBy="reset-password-requirements"
      />
      <div className="rounded-md border border-zinc-200 bg-zinc-50 px-3 py-3">
        <p className="mb-2 text-xs font-semibold text-zinc-700">Sua senha deve atender a todos os requisitos:</p>
        <PasswordRequirements id="reset-password-requirements" value={candidate} />
      </div>
      <PasswordInput
        value={confirmation}
        onChange={setConfirmation}
        placeholder="Confirmar nova senha"
        visibilityContext="confirmação da nova senha"
        disabled={loading}
        invalid={confirmationStarted && !valuesMatch}
        describedBy={confirmationStarted ? 'reset-password-match' : undefined}
      />
      {confirmationStarted ? (
        <p
          id="reset-password-match"
          className={`flex items-center gap-2 text-xs font-medium ${valuesMatch ? 'text-emerald-700' : 'text-red-600'}`}
          aria-live="polite"
        >
          {valuesMatch ? (
            <CheckCircleIcon className="h-4 w-4 shrink-0" aria-hidden="true" />
          ) : (
            <ExclamationTriangleIcon className="h-4 w-4 shrink-0" aria-hidden="true" />
          )}
          {valuesMatch ? 'As senhas coincidem.' : 'As senhas não coincidem.'}
        </p>
      ) : null}
      <Nav
        back={() => setStep(2)}
        next={reset}
        loading={loading}
        label="Redefinir senha"
        submit
        disabled={!canReset}
      />
    </form>}
    {step === 4 && <div className="flex flex-col items-center py-8 text-center"><CheckCircleIcon className="mb-3 h-12 w-12 text-green-500"/><p className="mb-5 text-sm text-gray-600">Você já pode fazer login novamente.</p><Action onClick={() => onOpenChange(false)} loading={false} label="Voltar ao login"/></div>}
  </DialogContent></Dialog>
}

function Field({ icon, children }: { icon: 'email' | 'code'; children: React.ReactNode }) {
  const Icon = icon === 'email' ? EnvelopeIcon : KeyIcon
  return <div className="relative"><Icon className="absolute left-3 top-3 w-5 h-5 text-gray-400"/>{children}</div>
}
function Action({ onClick, loading, label }: { onClick: () => void | Promise<void>; loading: boolean; label: string }) {
  return <Button className="w-full py-5 bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-semibold" onClick={() => void onClick()} disabled={loading}>{loading ? 'Aguarde...' : label}</Button>
}
function Nav({ back, next, loading, label, submit = false, disabled = false }: { back: () => void; next: () => Promise<void>; loading: boolean; label: string; submit?: boolean; disabled?: boolean }) {
  return <div className="flex gap-3"><Button type="button" variant="outline" className="min-w-0 flex-1 py-5" onClick={back} disabled={loading}>Voltar</Button><Button type={submit ? 'submit' : 'button'} className="min-w-0 flex-1 py-5 bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-semibold" onClick={submit ? undefined : () => void next()} disabled={loading || disabled}>{loading ? (submit ? 'Redefinindo...' : 'Aguarde...') : label}</Button></div>
}
