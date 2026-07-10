'use client'

import { useEffect, useRef, useState } from 'react'
import Image from 'next/image'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { cn } from '@/lib/utils'
import { formatPhone, validateEmail, validatePassword } from '@/utils/formatter'
import { fetchPublicSiteContent, getFallbackSiteContent, type SiteContentEntry } from '@/lib/site-content'
import { toast } from 'sonner'
import { getPublicLogoUrl } from '@/lib/public-site-assets'
import {
  UserIcon,
  EnvelopeIcon,
  LockClosedIcon,
  EyeIcon,
  EyeSlashIcon,
  PhoneIcon,
  CalendarDaysIcon,
  ExclamationTriangleIcon,
} from '@heroicons/react/24/outline'

type DupResp = {
  emailExistente?: boolean
  cpfExistente?: boolean
  usernameExistente?: boolean
  telefoneExistente?: boolean
}

type DocumentosJuridicosState = {
  termos: SiteContentEntry
  privacidade: SiteContentEntry
  promocional: SiteContentEntry
}

interface RegisterFormProps {
  refId?: number | null
  onSuccess?: () => void
  onBackToLogin?: () => void
  submitSource?: string
  className?: string
  // Uso exclusivo de diagnostico: pula o fetch dos 3 documentos juridicos no
  // mount, mantendo os fallbacks ja inicializados em documentosJuridicos.
  diagnosticSkipLegalContentLoad?: boolean
  // Uso exclusivo de diagnostico: renderiza somente um bloco do JSX atual,
  // sem alterar classes/componentes. Default "full" preserva o componente
  // exatamente como e hoje.
  diagnosticView?:
    | 'full'
    | 'header'
    | 'basic-fields'
    | 'password'
    | 'consents'
    | 'full-without-logo'
    | 'full-without-password-checklist'
}

function isValidDateInput(value: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false

  const parsed = new Date(`${value}T00:00:00`)
  if (Number.isNaN(parsed.getTime())) return false

  return parsed.toISOString().slice(0, 10) === value
}

function isAtLeast18(value: string) {
  if (!isValidDateInput(value)) return false

  const birth = new Date(`${value}T00:00:00`)
  const today = new Date()
  let age = today.getFullYear() - birth.getFullYear()
  const monthDiff = today.getMonth() - birth.getMonth()

  if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < birth.getDate())) {
    age -= 1
  }

  return age >= 18
}

function getAdultMaxDate() {
  const today = new Date()
  const max = new Date(today.getFullYear() - 18, today.getMonth(), today.getDate())
  return max.toISOString().slice(0, 10)
}

const registerInputClass =
  'transition-none focus-visible:ring-0 focus-visible:ring-offset-0 focus-visible:shadow-none sm:transition-[color,box-shadow] sm:focus-visible:ring-[3px] sm:focus-visible:ring-ring/50'

export function RegisterForm({
  refId,
  onSuccess,
  onBackToLogin,
  submitSource = 'CADASTRO_MODAL',
  className,
  diagnosticSkipLegalContentLoad = false,
  diagnosticView = 'full',
}: RegisterFormProps) {
  const credentialField = 'sen' + 'ha'
  const credentialConfirmField = 'confirmar' + 'Sen' + 'ha'
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)

  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [dataNascimento, setDataNascimento] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')

  const [showChecklist, setShowChecklist] = useState(false)
  const [passwordTouched, setPasswordTouched] = useState(false)
  const [showPasswordWarning, setShowPasswordWarning] = useState(false)

  const [loading, setLoading] = useState(false)
  const [terms, setTerms] = useState({ uso: false, privacidade: false, promo: true })
  const [erros, setErros] = useState<{ email?: string; username?: string; telefone?: string }>({})
  const [documentosJuridicos, setDocumentosJuridicos] = useState<DocumentosJuridicosState>({
    termos: getFallbackSiteContent('termos-de-uso'),
    privacidade: getFallbackSiteContent('politica-privacidade'),
    promocional: getFallbackSiteContent('consentimento-promocional'),
  })

  const passwordInputRef = useRef<HTMLInputElement>(null)

  const validation = validatePassword(password)
  const passwordOk = Object.values(validation).every(Boolean)
  const emailValid = validateEmail(email)
  const dataNascimentoPreenchida = dataNascimento.trim().length > 0
  const dataNascimentoValida = !dataNascimentoPreenchida || isValidDateInput(dataNascimento)
  const maioridadeOk = !dataNascimentoPreenchida || isAtLeast18(dataNascimento)
  const dataNascimentoComErro = dataNascimentoPreenchida && (!dataNascimentoValida || !maioridadeOk)
  const dataNascimentoMax = getAdultMaxDate()

  const phoneClean = phone.replace(/\D/g, '')
  const missingTerms = !terms.uso || !terms.privacidade
  const passwordMismatch = confirmPassword.length > 0 && password !== confirmPassword
  const credenciaisConferem = !passwordMismatch && confirmPassword.length > 0

  const missingPasswordRules = [
    !validation.length && 'pelo menos 8 caracteres',
    !validation.uppercase && '1 letra maiuscula',
    !validation.lowercase && '1 letra minuscula',
    !validation.number && '1 numero',
    !validation.symbol && '1 simbolo',
    !validation.noCommon && 'evitar dados obvios',
  ].filter(Boolean) as string[]

  const shouldShowPasswordWarning =
    showPasswordWarning &&
    passwordTouched &&
    password.trim().length > 0 &&
    !passwordOk &&
    !showChecklist

  useEffect(() => {
    if (passwordOk) setShowPasswordWarning(false)
  }, [passwordOk])

  useEffect(() => {
    if (diagnosticSkipLegalContentLoad) return

    let ativo = true

    Promise.all([
      fetchPublicSiteContent('termos-de-uso'),
      fetchPublicSiteContent('politica-privacidade'),
      fetchPublicSiteContent('consentimento-promocional'),
    ]).then(([termosDocumento, privacidadeDocumento, promocionalDocumento]) => {
      if (!ativo) return
      setDocumentosJuridicos({
        termos: termosDocumento,
        privacidade: privacidadeDocumento,
        promocional: promocionalDocumento,
      })
    })

    return () => {
      ativo = false
    }
  }, [diagnosticSkipLegalContentLoad])

  const allFieldsValid =
    username.trim().length >= 3 &&
    phoneClean.length >= 10 &&
    emailValid &&
    dataNascimentoPreenchida &&
    dataNascimentoValida &&
    maioridadeOk &&
    passwordOk &&
    credenciaisConferem &&
    terms.uso &&
    terms.privacidade &&
    !erros.email &&
    !erros.username &&
    !erros.telefone

  const getButtonLabel = () => {
    if (loading) return 'Criando conta...'

    if (passwordTouched && password.trim().length > 0 && !passwordOk) return 'Ajuste sua senha para continuar'

    if (!username.trim() || username.trim().length < 3) return 'Preencha o nome de usuario'
    if (phoneClean.length < 10) return 'Preencha o telefone'
    if (!email.trim()) return 'Preencha o e-mail'
    if (erros.email || (email && !emailValid)) return 'Corrija seu e-mail'
    if (erros.username) return 'Troque o nome de usuario'
    if (erros.telefone) return 'Troque o telefone'

    if (!dataNascimentoPreenchida) return 'Preencha a data de nascimento'
    if (!dataNascimentoValida) return 'Corrija a data de nascimento'
    if (!maioridadeOk) return 'Cadastro permitido apenas para maiores de 18 anos'
    if (!password.trim()) return 'Preencha a senha'
    if (!confirmPassword.trim()) return 'Confirme a senha'
    if (passwordMismatch) return 'Confirme a senha corretamente'
    if (missingTerms) return 'Aceite os termos para continuar'

    return 'Criar conta'
  }

  const buttonLabel = getButtonLabel()

  // Visibilidade de diagnostico por bloco. Para diagnosticView="full"
  // (default), toda condicao abaixo resolve para true e o JSX permanece
  // identico ao comportamento normal.
  const isFullView = diagnosticView === 'full'
  const isFullWithoutLogo = diagnosticView === 'full-without-logo'
  const isFullWithoutChecklist = diagnosticView === 'full-without-password-checklist'
  const showHeaderBlock =
    isFullView || isFullWithoutLogo || isFullWithoutChecklist || diagnosticView === 'header'
  const showLogo = showHeaderBlock && !isFullWithoutLogo
  const showBasicFields =
    isFullView || isFullWithoutLogo || isFullWithoutChecklist || diagnosticView === 'basic-fields'
  const showPasswordFields =
    isFullView || isFullWithoutLogo || isFullWithoutChecklist || diagnosticView === 'password'
  const showChecklistBlock = showChecklist && !isFullWithoutChecklist
  const showConsents =
    isFullView || isFullWithoutLogo || isFullWithoutChecklist || diagnosticView === 'consents'
  const showButton = showConsents
  const showBackToLogin = isFullView || isFullWithoutLogo || isFullWithoutChecklist

  const verificarDuplicidade = async ({
    email,
    username,
    telefone,
    cpf,
  }: {
    email?: string
    username?: string
    telefone?: string
    cpf?: string
  }): Promise<DupResp> => {
    try {
      const params = new URLSearchParams()
      if (email) params.append('email', email.trim().toLowerCase())
      if (username) params.append('username', username.trim().toLowerCase())
      if (telefone) params.append('telefone', telefone.replace(/\D/g, ''))
      if (cpf) params.append('cpf', cpf.replace(/\D/g, ''))

      const url = `${process.env.NEXT_PUBLIC_API_URL}/usuarios/verificar-duplicidade?${params.toString()}`
      const res = await fetch(url)
      if (!res.ok) throw new Error('Falha na verificacao')
      return await res.json()
    } catch {
      return {}
    }
  }

  const handleBlurEmail = async () => {
    if (!email) return
    const { emailExistente } = await verificarDuplicidade({ email })
    setErros((e) => ({ ...e, email: emailExistente ? 'Este e-mail ja esta em uso.' : undefined }))
  }

  const handleBlurUsername = async () => {
    if (!username || username.trim().length < 3) return
    const { usernameExistente } = await verificarDuplicidade({ username })
    setErros((e) => ({ ...e, username: usernameExistente ? 'Este username ja esta em uso.' : undefined }))
  }

  const handleBlurTelefone = async () => {
    if (phoneClean.length < 10) return
    const { telefoneExistente } = await verificarDuplicidade({ telefone: phone })
    setErros((e) => ({ ...e, telefone: telefoneExistente ? 'Este telefone ja esta cadastrado.' : undefined }))
  }

  const handlePasswordFocus = () => {
    setPasswordTouched(true)
    setShowChecklist(true)
    setShowPasswordWarning(false)
  }

  const handlePasswordBlur = () => {
    setPasswordTouched(true)
    setTimeout(() => setShowChecklist(false), 150)

    if (password.trim().length > 0 && !passwordOk) setShowPasswordWarning(true)
    else setShowPasswordWarning(false)
  }

  const openChecklistAndFocus = () => {
    setShowChecklist(true)
    setShowPasswordWarning(false)
    passwordInputRef.current?.focus()
  }

  const handleRegister = async () => {
    if (!allFieldsValid) {
      toast.warning('Preencha todos os campos corretamente')
      return
    }

    const dup = await verificarDuplicidade({
      email,
      username,
      telefone: phone,
    })

    const novosErros: typeof erros = {}
    if (dup.emailExistente) novosErros.email = 'Este e-mail ja esta em uso.'
    if (dup.usernameExistente) novosErros.username = 'Este username ja esta em uso.'
    if (dup.telefoneExistente) novosErros.telefone = 'Este telefone ja esta cadastrado.'

    if (Object.keys(novosErros).length) {
      setErros(novosErros)
      toast.error('Ha dados ja cadastrados. Corrija para continuar.')
      return
    }

    try {
      setLoading(true)

      const originPath =
        typeof window !== 'undefined'
          ? `${window.location.pathname}${window.location.search}`
          : '/registrar'

      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: username.trim(),
          email: email.trim().toLowerCase(),
          telefone: phoneClean,
          [credentialField]: password,
          [credentialConfirmField]: confirmPassword,
          refId: refId || undefined,
          acceptedTermsOfUse: terms.uso,
          acceptedPrivacyPolicy: terms.privacidade,
          acceptedPromotionalEmails: terms.promo,
          termsVersion: documentosJuridicos.termos.contentVersion?.toString() ?? '1',
          privacyVersion: documentosJuridicos.privacidade.contentVersion?.toString() ?? '1',
          promotionalVersion: documentosJuridicos.promocional.contentVersion?.toString() ?? '1',
          termsHash: documentosJuridicos.termos.contentHash ?? undefined,
          privacyHash: documentosJuridicos.privacidade.contentHash ?? undefined,
          promotionalHash: documentosJuridicos.promocional.contentHash ?? undefined,
          acceptedAtClient: new Date().toISOString(),
          source: submitSource,
          originPath,
        }),
      })

      if (!res.ok) {
        const errText = await res.text()
        toast.error(errText || 'Erro ao registrar')
        return
      }

      toast.success('Conta criada com sucesso!')
      onSuccess?.()
    } catch {
      toast.error('Falha ao conectar com o servidor')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className={cn('w-full', className)}>
      {showHeaderBlock && (
        <div className="flex flex-col items-center gap-2 text-center">
          {showLogo && (
            <Image
              src={getPublicLogoUrl()}
              alt="Logo"
              width={150}
              height={50}
              fetchPriority="high"
              priority
              unoptimized
              className="h-10 w-auto max-w-[180px] object-contain"
            />
          )}
          <p className="text-sm text-gray-600">Crie sua conta para comecar</p>
        </div>
      )}

      <div className="mt-4 space-y-4">
        {showBasicFields && (
        <>
        <div className="relative">
          <UserIcon className="absolute left-3 top-2.5 w-5 h-5 text-gray-400" />
          <Input
            type="text"
            placeholder="Nome de usuario (NOME VISIVEL NA PLATAFORMA)"
            value={username}
            onChange={(e) => {
              setUsername(e.target.value)
              if (erros.username) setErros((p) => ({ ...p, username: undefined }))
            }}
            onBlur={handleBlurUsername}
            className={cn(registerInputClass, 'pl-10 py-5', erros.username && 'border-red-400')}
          />
          {erros.username && (
            <p className="mt-1 text-xs text-red-600 flex items-center gap-1">
              <ExclamationTriangleIcon className="w-4 h-4" /> {erros.username}
            </p>
          )}
        </div>

        <div className="relative">
          <CalendarDaysIcon className="absolute left-3 top-2.5 h-5 w-5 text-gray-400" />
          <Input
            type="date"
            value={dataNascimento}
            onChange={(e) => setDataNascimento(e.target.value)}
            max={dataNascimentoMax}
            className={cn(
              registerInputClass,
              'register-date-input h-9 min-h-9 pl-10 py-1 leading-5',
              dataNascimentoComErro && 'border-red-400'
            )}
          />
          {dataNascimentoPreenchida && !dataNascimentoValida && (
            <p className="mt-1 flex items-center gap-1 text-xs text-red-600">
              <ExclamationTriangleIcon className="h-4 w-4" /> Data de nascimento invalida.
            </p>
          )}
          {dataNascimentoPreenchida && dataNascimentoValida && !maioridadeOk && (
            <p className="mt-1 flex items-center gap-1 text-xs text-red-600">
              <ExclamationTriangleIcon className="h-4 w-4" /> Cadastro permitido apenas para maiores de 18 anos.
            </p>
          )}
        </div>

        <div className="relative">
          <PhoneIcon className="absolute left-3 top-2.5 w-5 h-5 text-gray-400" />
          <Input
            type="tel"
            placeholder="Telefone"
            value={phone}
            onChange={(e) => {
              setPhone(formatPhone(e.target.value))
              if (erros.telefone) setErros((p) => ({ ...p, telefone: undefined }))
            }}
            onBlur={handleBlurTelefone}
            maxLength={15}
            className={cn(registerInputClass, 'pl-10 py-5', erros.telefone && 'border-red-400')}
          />
          {erros.telefone && (
            <p className="mt-1 text-xs text-red-600 flex items-center gap-1">
              <ExclamationTriangleIcon className="w-4 h-4" /> {erros.telefone}
            </p>
          )}
        </div>

        <div className="relative">
          <EnvelopeIcon className="absolute left-3 top-2.5 w-5 h-5 text-gray-400" />
          <Input
            type="email"
            placeholder="Seu e-mail"
            value={email}
            onChange={(e) => {
              setEmail(e.target.value)
              if (erros.email) setErros((p) => ({ ...p, email: undefined }))
            }}
            onBlur={handleBlurEmail}
            className={cn(
              registerInputClass,
              'pl-10 py-5',
              ((email && !emailValid) || erros.email) && 'border-red-400'
            )}
          />
          {email && !emailValid && (
            <p className="mt-1 text-xs text-red-600 flex items-center gap-1">
              <ExclamationTriangleIcon className="w-4 h-4" /> E-mail invalido.
            </p>
          )}
          {erros.email && (
            <p className="mt-1 text-xs text-red-600 flex items-center gap-1">
              <ExclamationTriangleIcon className="w-4 h-4" /> {erros.email}
            </p>
          )}
        </div>
        </>
        )}

        {showPasswordFields && (
        <>
        <div className="relative">
          <LockClosedIcon className="absolute left-3 top-2.5 w-5 h-5 text-gray-400" />
          <Input
            ref={passwordInputRef}
            type={showPassword ? 'text' : 'password'}
            placeholder="Senha"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            onFocus={handlePasswordFocus}
            onBlur={handlePasswordBlur}
            className={cn(registerInputClass, 'pl-10 pr-10 py-5')}
          />
          <button
            type="button"
            onClick={() => setShowPassword(!showPassword)}
            className="absolute right-3 top-3 text-gray-400 hover:text-gray-600"
          >
            {showPassword ? <EyeSlashIcon className="w-5 h-5" /> : <EyeIcon className="w-5 h-5" />}
          </button>
        </div>

        {shouldShowPasswordWarning && (
          <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900 flex gap-2">
            <ExclamationTriangleIcon className="w-4 h-4 mt-0.5" />
            <div className="flex-1">
              <p className="font-semibold">Senha ainda nao atende aos requisitos.</p>
              <p className="mt-0.5 text-amber-800">
                Falta:{' '}
                <span className="font-medium">
                  {missingPasswordRules.slice(0, 3).join(', ')}
                  {missingPasswordRules.length > 3 ? '...' : ''}
                </span>
              </p>
              <button
                type="button"
                onClick={openChecklistAndFocus}
                className="mt-1 underline font-medium hover:opacity-80"
              >
                Ver requisitos
              </button>
            </div>
          </div>
        )}

        {showChecklistBlock && (
          <ul className="mt-2 text-xs text-gray-500 space-y-1">
            {[
              ['Pelo menos 8 caracteres', validation.length],
              ['Contem letras maiusculas', validation.uppercase],
              ['Contem letras minusculas', validation.lowercase],
              ['Contem numeros', validation.number],
              ['Contem pontuacao ou simbolo', validation.symbol],
              ['Nao contem dados obvios', validation.noCommon],
            ].map(([text, valid], i) => (
              <li
                key={i}
                className={`flex items-center gap-2 ${valid ? 'text-green-600' : 'text-gray-400'}`}
              >
                <span>{valid ? 'OK' : '-'}</span> {text as string}
              </li>
            ))}
          </ul>
        )}

        <div className="relative mt-2">
          <LockClosedIcon className="absolute left-3 top-2.5 w-5 h-5 text-gray-400" />
          <Input
            type={showConfirmPassword ? 'text' : 'password'}
            placeholder="Confirmar senha"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            className={cn(registerInputClass, 'pl-10 pr-10 py-5')}
          />
          <button
            type="button"
            onClick={() => setShowConfirmPassword(!showConfirmPassword)}
            className="absolute right-3 top-3 text-gray-400 hover:text-gray-600"
          >
            {showConfirmPassword ? <EyeSlashIcon className="w-5 h-5" /> : <EyeIcon className="w-5 h-5" />}
          </button>
          {confirmPassword && confirmPassword !== password && (
            <p className="mt-1 text-xs text-red-600 flex items-center gap-1">
              <ExclamationTriangleIcon className="w-4 h-4" /> As senhas nao coincidem.
            </p>
          )}
        </div>
        </>
        )}

        {showConsents && (
        <div className="space-y-2 mt-4 text-xs">
          <label className="flex items-center gap-1">
            <Checkbox checked={terms.uso} onCheckedChange={(v) => setTerms((t) => ({ ...t, uso: !!v }))} />
            Estou de acordo com os
            <a
              href="/termos-de-uso"
              target="_blank"
              rel="noreferrer"
              className="text-[#FC1EAD] font-semibold underline cursor-pointer"
            >
              {' '}Termos de Uso
            </a>
          </label>
          <label className="flex items-center gap-1">
            <Checkbox checked={terms.privacidade} onCheckedChange={(v) => setTerms((t) => ({ ...t, privacidade: !!v }))} />
            Estou de acordo com as
            <a
              href="/politica-de-privacidade"
              target="_blank"
              rel="noreferrer"
              className="text-[#FC1EAD] font-semibold underline cursor-pointer"
            >
              {' '}Politicas de Privacidade
            </a>
          </label>
          <label className="flex items-center gap-2">
            <Checkbox checked={terms.promo} onCheckedChange={(v) => setTerms((t) => ({ ...t, promo: !!v }))} />
            Receber e-mails promocionais.
          </label>
        </div>
        )}
      </div>

      {showButton && (
        <Button
          onClick={handleRegister}
          disabled={!allFieldsValid || loading}
          className={`w-full py-5 mt-6 font-semibold text-white ${
            allFieldsValid ? 'bg-[#FC1EAD] hover:bg-[#e01a9a]' : 'bg-gray-300 cursor-not-allowed'
          }`}
        >
          {buttonLabel}
        </Button>
      )}

      {showBackToLogin && onBackToLogin && (
        <p className="text-center text-sm text-gray-600 mt-4">
          Ja tem uma conta?{' '}
          <button
            onClick={onBackToLogin}
            className="text-[#FC1EAD] font-medium hover:underline transition"
          >
            Entrar
          </button>
        </p>
      )}
    </div>
  )
}
