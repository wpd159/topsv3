'use client'

import { useEffect, useState } from 'react'
import Image from 'next/image'
import { CheckCircleIcon, EnvelopeIcon, KeyIcon, LockClosedIcon } from '@heroicons/react/24/outline'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { toast } from 'sonner'
import { getPublicLogoUrl } from '@/lib/public-site-assets'
import { PublicAuthApiError, requestPublicPasswordReset, resetPublicCredential, validatePublicResetCode } from '@/lib/public-auth-api'

type Props = { open: boolean; onOpenChange: (value: boolean) => void }
const stepDescriptions = ['Digite seu e-mail para recuperar sua senha', 'Digite o código enviado para seu e-mail', 'Redefina sua senha', 'Senha redefinida']

export function RecuperarSenhaModal({ open, onOpenChange }: Props) {
  const [step, setStep] = useState(1)
  const [loading, setLoading] = useState(false)
  const [email, setEmail] = useState('')
  const [codigo, setCodigo] = useState('')
  const [novaSenha, setNovaSenha] = useState('')
  const [confirmarSenha, setConfirmarSenha] = useState('')

  useEffect(() => { if (!open) { setStep(1); setEmail(''); setCodigo(''); setNovaSenha(''); setConfirmarSenha('') } }, [open])
  const report = (error: unknown, fallback: string) => toast.error(error instanceof PublicAuthApiError ? error.message : fallback)

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
    if (!novaSenha || !confirmarSenha) { toast.warning('Preencha todos os campos.'); return }
    if (novaSenha !== confirmarSenha) { toast.warning('As senhas não coincidem.'); return }
    try { setLoading(true); await resetPublicCredential(email.trim().toLowerCase(), codigo, novaSenha, confirmarSenha); toast.success('Senha redefinida com sucesso!'); setStep(4) }
    catch (error) { report(error, 'Erro ao redefinir senha.') } finally { setLoading(false) }
  }

  return <Dialog open={open} onOpenChange={onOpenChange}><DialogContent className="sm:max-w-md p-6 rounded-xl">
    <DialogHeader><DialogTitle className="flex justify-center mb-2"><Image src={getPublicLogoUrl()} alt="Logo" width={150} height={50} priority unoptimized className="h-10 w-auto max-w-[180px] object-contain"/></DialogTitle>
      <DialogDescription className="text-center text-gray-600">{stepDescriptions[step - 1]}</DialogDescription></DialogHeader>
    {step === 1 && <div className="mt-4 space-y-5"><Field icon="email"><Input type="email" autoComplete="email" placeholder="Seu e-mail" className="pl-10 py-5" value={email} onChange={e => setEmail(e.target.value)} disabled={loading}/></Field><Action onClick={requestCode} loading={loading} label="Enviar código"/></div>}
    {step === 2 && <div className="mt-4 space-y-5"><Field icon="code"><Input inputMode="numeric" autoComplete="one-time-code" placeholder="Código de verificação" className="pl-10 py-5 tracking-widest text-center font-medium" maxLength={6} value={codigo} onChange={e => setCodigo(e.target.value.replace(/\D/g, '').slice(0, 6))} disabled={loading}/></Field><Nav back={() => setStep(1)} next={validateCode} loading={loading} label="Verificar código"/></div>}
    {step === 3 && <div className="mt-4 space-y-5"><Field icon="password"><Input type="password" autoComplete="new-password" placeholder="Nova senha" className="pl-10 py-5" value={novaSenha} onChange={e => setNovaSenha(e.target.value)} disabled={loading}/></Field><Field icon="password"><Input type="password" autoComplete="new-password" placeholder="Confirmar nova senha" className="pl-10 py-5" value={confirmarSenha} onChange={e => setConfirmarSenha(e.target.value)} disabled={loading}/></Field><Nav back={() => setStep(2)} next={reset} loading={loading} label="Redefinir senha"/></div>}
    {step === 4 && <div className="flex flex-col items-center py-8 text-center"><CheckCircleIcon className="w-12 h-12 text-green-500 mb-3"/><p className="text-gray-600 text-sm mb-5">Você já pode fazer login novamente.</p><Action onClick={() => onOpenChange(false)} loading={false} label="Voltar ao login"/></div>}
  </DialogContent></Dialog>
}

function Field({ icon, children }: { icon: 'email' | 'code' | 'password'; children: React.ReactNode }) {
  const Icon = icon === 'email' ? EnvelopeIcon : icon === 'code' ? KeyIcon : LockClosedIcon
  return <div className="relative"><Icon className="absolute left-3 top-3 w-5 h-5 text-gray-400"/>{children}</div>
}
function Action({ onClick, loading, label }: { onClick: () => void | Promise<void>; loading: boolean; label: string }) {
  return <Button className="w-full py-5 bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-semibold" onClick={() => void onClick()} disabled={loading}>{loading ? 'Aguarde...' : label}</Button>
}
function Nav({ back, next, loading, label }: { back: () => void; next: () => Promise<void>; loading: boolean; label: string }) {
  return <div className="flex justify-between"><Button variant="outline" className="py-5" onClick={back} disabled={loading}>Voltar</Button><Button className="py-5 bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-semibold" onClick={() => void next()} disabled={loading}>{loading ? 'Aguarde...' : label}</Button></div>
}
