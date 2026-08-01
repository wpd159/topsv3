'use client'

import { useEffect, useMemo, useState } from 'react'
import Image from 'next/image'
import { useRouter } from 'next/navigation'
import {
  EyeIcon,
  EyeSlashIcon,
  EnvelopeIcon,
  LockClosedIcon,
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
import { RecuperarSenhaModal } from './recuperar-senha-modal'
import { ConfirmarContaModal } from './confirmar-conta-modal'
import { useAuth } from '@/context/AuthContext'
import { toast } from 'sonner'
import { getPublicLogoUrl } from '@/lib/public-site-assets'
import { loginPublic, PublicAuthApiError } from '@/lib/public-auth-api'

interface LoginModalProps {
  open: boolean
  onOpenChange: (value: boolean) => void
  redirectAfterSuccess?: string | null
  onOpenRegister?: () => void
}

export function LoginModal({
  open,
  onOpenChange,
  redirectAfterSuccess,
  onOpenRegister,
}: LoginModalProps) {
  const router = useRouter()
  const { login } = useAuth()
  const [showPassword, setShowPassword] = useState(false)
  const [forgotPasswordModalOpen, setForgotPasswordModalOpen] = useState(false)
  const [confirmAccountOpen, setConfirmAccountOpen] = useState(false)
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [loading, setLoading] = useState(false)
  const [queryRedirectTarget, setQueryRedirectTarget] = useState<string | null>(null)

  useEffect(() => {
    if (redirectAfterSuccess) {
      setQueryRedirectTarget(null)
      return
    }

    if (typeof window === 'undefined') return

    const candidate = new URLSearchParams(window.location.search).get('next')
    setQueryRedirectTarget(
      candidate && candidate.startsWith('/') && !candidate.startsWith('//') ? candidate : null,
    )
  }, [redirectAfterSuccess, open])

  const redirectTarget = useMemo(() => {
    const candidate = redirectAfterSuccess || queryRedirectTarget
    if (candidate && candidate.startsWith('/') && !candidate.startsWith('//')) return candidate
    return '/meus-anuncios'
  }, [queryRedirectTarget, redirectAfterSuccess])

  useEffect(() => {
    if (!redirectTarget) return

    const handleLoginSuccess = () => {
      router.push(redirectTarget)
    }

    window.addEventListener('tops:login-success', handleLoginSuccess)
    return () => window.removeEventListener('tops:login-success', handleLoginSuccess)
  }, [redirectTarget, router])

  const handleOpenRegister = () => {
    onOpenChange(false)
    window.setTimeout(() => {
      if (onOpenRegister) {
        onOpenRegister()
        return
      }

      window.dispatchEvent(new CustomEvent('tops:open-register'))
    }, 180)
  }

  const handleLogin = async () => {
    if (loading) return

    if (!email || !senha) {
      toast.warning('Preencha e-mail e senha')
      return
    }

    try {
      setLoading(true)
      await loginPublic(email, senha)
      await login()
      toast.success('Login realizado com sucesso 🎉')
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new Event('tops:login-success'))
      }
      onOpenChange(false)
    } catch (error) {
      if (error instanceof PublicAuthApiError && error.status === 401 && error.message.includes('confirmada')) {
        setConfirmAccountOpen(true)
        return
      }
      toast.error(error instanceof PublicAuthApiError ? error.message : 'Falha na conexão com o servidor')
    } finally {
      setLoading(false)
    }
  }

  return (
    <>
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
              Entre na sua conta para continuar
            </DialogDescription>
          </DialogHeader>

          <form
            onSubmit={(e) => {
              e.preventDefault()
              handleLogin()
            }}
            className="mt-4 space-y-4"
          >
            <div className="relative">
              <EnvelopeIcon className="absolute left-3 top-2.5 w-5 h-5 text-gray-400" />
              <Input
                type="email"
                placeholder="Seu e-mail"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="pl-10 py-5"
              />
            </div>

            <div className="relative">
              <LockClosedIcon className="absolute left-3 top-2.5 w-5 h-5 text-gray-400" />
              <Input
                type={showPassword ? 'text' : 'password'}
                placeholder="Senha"
                value={senha}
                onChange={(e) => setSenha(e.target.value)}
                className="pl-10 pr-10 py-5"
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

            <div className="flex justify-between items-center">
              <button
                type="button"
                onClick={() => setForgotPasswordModalOpen(true)}
                className="text-sm text-[#FC1EAD] cursor-pointer hover:underline"
              >
                Esqueci minha senha
              </button>

              <button
                type="button"
                onClick={handleOpenRegister}
                className="text-sm text-gray-500 hover:underline"
              >
                Criar conta
              </button>
            </div>

            <Button
              type="submit"
              disabled={loading}
              className="w-full py-5 mt-2 bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-semibold"
            >
              {loading ? 'Entrando...' : 'Entrar'}
            </Button>
          </form>
        </DialogContent>
      </Dialog>

      <RecuperarSenhaModal
        open={forgotPasswordModalOpen}
        onOpenChange={setForgotPasswordModalOpen}
      />

      <ConfirmarContaModal
        open={confirmAccountOpen}
        onOpenChange={setConfirmAccountOpen}
        email={email}
        onVerified={handleLogin}
      />

    </>
  )
}
