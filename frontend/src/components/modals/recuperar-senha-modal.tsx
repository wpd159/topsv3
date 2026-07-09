'use client'

import { useState } from 'react'
import Image from 'next/image'
import {
  EnvelopeIcon,
  KeyIcon,
  LockClosedIcon,
  EyeIcon,
  EyeSlashIcon,
  CheckCircleIcon,
} from '@heroicons/react/24/outline'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog'
import { toast } from 'sonner'
import { getPublicLogoUrl } from '@/lib/public-site-assets'

interface RecuperarSenhaModalProps {
  open: boolean
  onOpenChange: (value: boolean) => void
}

export function RecuperarSenhaModal({ open, onOpenChange }: RecuperarSenhaModalProps) {
  const [step, setStep] = useState(1)
  const [loading, setLoading] = useState(false)
  const [email, setEmail] = useState('')
  const [codigo, setCodigo] = useState('')
  const [novaSenha, setNovaSenha] = useState('')
  const [confirmarSenha, setConfirmarSenha] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)

  const next = () => setStep((s) => s + 1)
  const back = () => setStep((s) => s - 1)

  // ✅ Etapa 1: Enviar código de recuperação
  const handleEnviarCodigo = async () => {
    if (!email.trim()) {
      toast.warning('Digite seu e-mail.')
      return
    }
    try {
      setLoading(true)
      const res = await fetch(
        `${process.env.NEXT_PUBLIC_API_URL}/auth/forgot-password?email=${encodeURIComponent(email)}`,
        { method: 'POST' }
      )

      // ⚠️ Se o e-mail não existir, backend devolve 404
      if (res.status === 404) {
        toast.error('E-mail não encontrado na base de dados.')
        return
      }

      if (!res.ok) throw new Error(await res.text())

      toast.success('Código de recuperação enviado ao seu e-mail!')
      next()
    } catch (err: any) {
      toast.error(err?.message || 'Erro ao enviar código.')
    } finally {
      setLoading(false)
    }
  }

  // ✅ Etapa 2: Validar código
  const handleValidarCodigo = async () => {
    if (!codigo.trim()) {
      toast.warning('Digite o código recebido por e-mail.')
      return
    }
    try {
      setLoading(true)
      const res = await fetch(
        `${process.env.NEXT_PUBLIC_API_URL}/auth/validate-reset-code?email=${encodeURIComponent(
          email
        )}&codigo=${encodeURIComponent(codigo)}`,
        { method: 'POST' }
      )

      const text = await res.text()
      if (!res.ok || text.toLowerCase().includes('inválido')) {
        throw new Error('Código inválido ou expirado.')
      }

      toast.success('Código verificado com sucesso!')
      next()
    } catch (err: any) {
      toast.error(err?.message || 'Falha ao validar código.')
    } finally {
      setLoading(false)
    }
  }

  // ✅ Etapa 3: Redefinir senha
  const handleRedefinirCredencial = async () => {
    if (!novaSenha || !confirmarSenha) {
      toast.warning('Preencha todos os campos.')
      return
    }
    if (novaSenha !== confirmarSenha) {
      toast.warning('As senhas não coincidem.')
      return
    }

    try {
      setLoading(true)
      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/auth/reset-password`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email,
          codigo,
          novaSenha,
          confirmarSenha,
        }),
      })

      if (!res.ok) throw new Error(await res.text())

      toast.success('Senha redefinida com sucesso!')
      next()
    } catch (err: any) {
      toast.error(err?.message || 'Erro ao redefinir senha.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md p-6 rounded-xl">
        <DialogHeader>
          <DialogTitle className="flex justify-center mb-2">
            <Image
              src={getPublicLogoUrl()}
              alt="Logo"
              width={150}
              height={50}
              priority
              fetchPriority='high'
              unoptimized
              className="h-10 w-auto max-w-[180px] object-contain"
            />
          </DialogTitle>
          <DialogDescription className="text-center text-gray-600">
            {step === 1 && 'Digite seu e-mail para recuperar sua senha'}
            {step === 2 && 'Digite o código enviado para seu e-mail'}
            {step === 3 && 'Redefina sua senha'}
          </DialogDescription>
        </DialogHeader>

        {/* Etapa 1 — E-mail */}
        {step === 1 && (
          <div className="mt-4 space-y-5">
            <div className="relative">
              <EnvelopeIcon className="absolute left-3 top-3 w-5 h-5 text-gray-400" />
              <Input
                type="email"
                placeholder="Seu e-mail"
                className="pl-10 py-5"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                disabled={loading}
              />
            </div>

            <Button
              className="w-full py-5 bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-semibold"
              onClick={handleEnviarCodigo}
              disabled={loading}
            >
              {loading ? 'Enviando...' : 'Enviar código'}
            </Button>
          </div>
        )}

        {/* Etapa 2 — Código */}
        {step === 2 && (
          <div className="mt-4 space-y-5">
            <div className="relative">
              <KeyIcon className="absolute left-3 top-3 w-5 h-5 text-gray-400" />
              <Input
                type="text"
                placeholder="Código de verificação"
                className="pl-10 py-5 tracking-widest text-center font-medium"
                maxLength={6}
                value={codigo}
                onChange={(e) => setCodigo(e.target.value)}
                disabled={loading}
              />
            </div>

            <div className="flex justify-between">
              <Button variant="outline" className="py-5" onClick={back} disabled={loading}>
                Voltar
              </Button>

              <Button
                className="py-5 bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-semibold"
                onClick={handleValidarCodigo}
                disabled={loading}
              >
                {loading ? 'Verificando...' : 'Verificar código'}
              </Button>
            </div>
          </div>
        )}

        {/* Etapa 3 — Redefinir senha */}
        {step === 3 && (
          <div className="mt-4 space-y-5">
            <div className="relative">
              <LockClosedIcon className="absolute left-3 top-3 w-5 h-5 text-gray-400" />
              <Input
                type={showPassword ? 'text' : 'password'}
                placeholder="Nova senha"
                className="pl-10 pr-10 py-5"
                value={novaSenha}
                onChange={(e) => setNovaSenha(e.target.value)}
                disabled={loading}
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute right-3 top-3 text-gray-400 hover:text-gray-600"
              >
                {showPassword ? (
                  <EyeSlashIcon className="w-5 h-5" />
                ) : (
                  <EyeIcon className="w-5 h-5" />
                )}
              </button>
            </div>

            <div className="relative">
              <LockClosedIcon className="absolute left-3 top-3 w-5 h-5 text-gray-400" />
              <Input
                type={showConfirmPassword ? 'text' : 'password'}
                placeholder="Confirmar nova senha"
                className="pl-10 pr-10 py-5"
                value={confirmarSenha}
                onChange={(e) => setConfirmarSenha(e.target.value)}
                disabled={loading}
              />
              <button
                type="button"
                onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                className="absolute right-3 top-3 text-gray-400 hover:text-gray-600"
              >
                {showConfirmPassword ? (
                  <EyeSlashIcon className="w-5 h-5" />
                ) : (
                  <EyeIcon className="w-5 h-5" />
                )}
              </button>
            </div>

            <div className="flex justify-between">
              <Button variant="outline" className="py-5" onClick={back} disabled={loading}>
                Voltar
              </Button>

              <Button
                className="py-5 bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-semibold"
                onClick={handleRedefinirCredencial}
                disabled={loading}
              >
                {loading ? 'Salvando...' : 'Redefinir senha'}
              </Button>
            </div>
          </div>
        )}

        {/* Etapa 4 — Sucesso */}
        {step === 4 && (
          <div className="flex flex-col items-center justify-center py-8 text-center">
            <CheckCircleIcon className="w-12 h-12 text-green-500 mb-3" />
            <h2 className="text-lg font-semibold text-gray-800 mb-1">
              Senha redefinida!
            </h2>
            <p className="text-gray-600 text-sm mb-5">
              Você já pode fazer login novamente.
            </p>
            <Button
              className="w-full bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-semibold py-3"
              onClick={() => {
                onOpenChange(false)
                setStep(1)
                setEmail('')
                setCodigo('')
                setNovaSenha('')
                setConfirmarSenha('')
              }}
            >
              Voltar ao login
            </Button>
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
