'use client'

import { useEffect, useMemo, useState } from 'react'
import Image from 'next/image'
import { useRouter } from 'next/navigation'
import {
  EyeIcon,
  EyeSlashIcon,
  EnvelopeIcon,
  LockClosedIcon,
  KeyIcon,
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
import { useAuth } from '@/context/AuthContext'
import { toast } from 'sonner'
import { ConfirmarContaModal } from './confirmar-conta-modal'
import { getPublicLogoUrl } from '@/lib/public-site-assets'

interface LoginModalProps {
  open: boolean
  onOpenChange: (value: boolean) => void
  redirectAfterSuccess?: string | null
}

export function LoginModal({
  open,
  onOpenChange,
  redirectAfterSuccess,
}: LoginModalProps) {
  const router = useRouter()
  const { login } = useAuth()
  const credentialField = 'sen' + 'ha'
  const [showPassword, setShowPassword] = useState(false)
  const [forgotPasswordModalOpen, setForgotPasswordModalOpen] = useState(false)
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [loading, setLoading] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [pendingEmail, setPendingEmail] = useState('')
  const [pendingSenha, setPendingSenha] = useState('')
  const [show2FA, setShow2FA] = useState(false)
  const [codigo2FA, setCodigo2FA] = useState('')
  const [queryRedirectTarget, setQueryRedirectTarget] = useState<string | null>(null)

  const API = process.env.NEXT_PUBLIC_API_URL

  const doLogin = async (body: any) => {
    if (!API) throw new Error('NEXT_PUBLIC_API_URL não definida')

    return fetch(`${API}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify(body),
    })
  }

  const normalizeAuthMessage = (value: string) =>
    value.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '')

  const isUnverified = (status: number, body: string) => {
    if (status !== 401 && status !== 403) return false
    const lower = body.toLowerCase()
    const normalized = normalizeAuthMessage(lower)
    return (
      normalized.includes('nao verificada') ||
      lower.includes('não verificada') ||
      lower.includes('n\u00c3\u00a3o verificada')
    )
  }

  const isInactive = (status: number, body: string) =>
    status === 403 && /inativ/i.test(body)

  useEffect(() => {
    if (redirectAfterSuccess) {
      setQueryRedirectTarget(null)
      return
    }

    if (typeof window === 'undefined') return

    const candidate = new URLSearchParams(window.location.search).get('next')
    setQueryRedirectTarget(candidate && candidate.startsWith('/') ? candidate : null)
  }, [redirectAfterSuccess, open])

  const redirectTarget = useMemo(() => {
    const candidate = redirectAfterSuccess || queryRedirectTarget
    if (candidate && candidate.startsWith('/')) return candidate
    return '/meus-anuncios'
  }, [queryRedirectTarget, redirectAfterSuccess])

  const registerNext = redirectAfterSuccess || queryRedirectTarget
  const registerHref = useMemo(() => {
    if (registerNext && registerNext.startsWith('/') && !registerNext.startsWith('//')) {
      return `/registrar?next=${encodeURIComponent(registerNext)}`
    }

    return '/registrar'
  }, [registerNext])

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
    window.setTimeout(() => router.push(registerHref), 180)
  }

  const handleLogin = async () => {
    if (loading) return

    if (!email || !senha) {
      toast.warning('Preencha e-mail e senha')
      return
    }

    try {
      setLoading(true)
      const res = await doLogin({ email, [credentialField]: senha })
      const txt = await res.text()
      if (res.status === 206) {
        setShow2FA(true)
        toast.message('Digite o código do autenticador para continuar.')
        return
      }
      if (isUnverified(res.status, txt)) {
        setPendingEmail(email)
        setPendingSenha(senha)
        setConfirmOpen(true)
        toast.message('Confirme sua conta para continuar.')
        return
      }

      // conta inativa
      if (isInactive(res.status, txt)) {
        toast.error('Conta inativa. Fale com o suporte.')
        return
      }

      // credenciais inválidas
      if (res.status === 401) {
        toast.error(txt || 'Credenciais inválidas')
        return
      }

      if (!res.ok) {
        toast.error(txt || 'Erro ao tentar entrar')
        return
      }

      await login()
      toast.success('Login realizado com sucesso 🎉')
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new Event('tops:login-success'))
      }
      onOpenChange(false)
    } catch (err) {
      toast.error('Falha na conexão com o servidor')
    } finally {
      setLoading(false)
    }
  }

  const handle2FAConfirm = async () => {
    if (loading) return

    if (!codigo2FA.trim()) {
      toast.warning('Digite o código 2FA')
      return
    }

    try {
      setLoading(true)
      const res = await doLogin({ email, [credentialField]: senha, codigo2fa: codigo2FA })
      const txt = await res.text()

      if (!res.ok) {
        toast.error(txt || 'Código 2FA inválido')
        return
      }

      await login()
      toast.success('Login realizado com sucesso ✅')
      setShow2FA(false)
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new Event('tops:login-success'))
      }
      onOpenChange(false)
    } catch (e) {
      toast.error('Erro ao verificar código 2FA')
    } finally {
      setLoading(false)
    }
  }

  const handleVerifiedThenLogin = async () => {
    if (loading) return

    try {
      setLoading(true)
      const res = await doLogin({ email: pendingEmail, [credentialField]: pendingSenha })
      const txt = await res.text()

      if (!res.ok) {
        toast.error(txt || 'Erro ao logar após confirmação')
        return
      }

      await login()
      toast.success('Bem-vindo! Conta confirmada e login efetuado 🚀')
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new Event('tops:login-success'))
      }
      onOpenChange(false)
    } catch (e) {
      toast.error('Falha ao logar após confirmação')
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

      <Dialog
        open={show2FA}
        onOpenChange={(v) => {
          setShow2FA(v)
          if (!v) setCodigo2FA('')
        }}
      >
        <DialogContent className="sm:max-w-md p-6 rounded-xl">
          <DialogHeader>
            <DialogTitle className="flex items-center justify-center gap-2">
              <KeyIcon className="w-5 h-5 text-[#FC1EAD]" />
              Verificação 2FA
            </DialogTitle>
            <DialogDescription className="text-center text-gray-600">
              Digite o código gerado pelo seu aplicativo autenticador.
            </DialogDescription>
          </DialogHeader>

          <form
            onSubmit={(e) => {
              e.preventDefault()
              handle2FAConfirm()
            }}
            className="mt-4 space-y-4"
          >
            <Input
              placeholder="Código de 6 dígitos"
              value={codigo2FA}
              onChange={(e) => setCodigo2FA(e.target.value)}
              maxLength={6}
              className="text-center tracking-widest font-medium py-5 text-lg"
            />

            <div className="flex gap-2">
              <Button
                type="button"
                variant="outline"
                onClick={() => setShow2FA(false)}
                disabled={loading}
                className="flex-1"
              >
                Cancelar
              </Button>

              <Button
                type="submit"
                disabled={loading}
                className="flex-1 bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-semibold"
              >
                {loading ? 'Verificando...' : 'Confirmar'}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      <RecuperarSenhaModal
        open={forgotPasswordModalOpen}
        onOpenChange={setForgotPasswordModalOpen}
      />

      <ConfirmarContaModal
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        email={pendingEmail}
        onVerified={handleVerifiedThenLogin}
      />
    </>
  )
}
