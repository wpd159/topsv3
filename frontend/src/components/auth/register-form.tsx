'use client'

import { useEffect, useRef, useState } from 'react'
import Image from 'next/image'
import {
  CalendarDaysIcon,
  EnvelopeIcon,
  ExclamationTriangleIcon,
  PhoneIcon,
  UserIcon,
} from '@heroicons/react/24/outline'
import { PasswordInput } from '@/components/auth/password-input'
import {
  PasswordRequirements,
  passwordMeetsPolicy,
  pendingPasswordRequirements,
} from '@/components/auth/password-requirements'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'
import { getPublicLogoUrl } from '@/lib/public-site-assets'
import { formatPhone, validateEmail } from '@/utils/formatter'
import {
  checkDuplicidade,
  fetchLegalDocuments,
  submitRegister,
  type DuplicidadeResposta,
} from '@/lib/public-auth-api'
import { normalizeApiError } from '@/lib/api-contract'

interface RegisterFormProps {
  refId?: number | null
  onSuccess?: (email: string) => void
  onBackToLogin?: () => void
  className?: string
}

type RegisterValues = {
  username: string
  dataNascimento: string
  phone: string
  email: string
  credencial: string
  confirmacaoCredencial: string
}

type RegisterTerms = {
  uso: boolean
  privacidade: boolean
  promo: boolean
}

type DuplicateField = 'email' | 'username' | 'telefone'

const INITIAL_VALUES: RegisterValues = {
  username: '',
  dataNascimento: '',
  phone: '',
  email: '',
  credencial: '',
  confirmacaoCredencial: '',
}

const INITIAL_TERMS: RegisterTerms = {
  uso: false,
  privacidade: false,
  promo: true,
}

const REGISTER_INPUT_CLASS = cn(
  'transition-[border-color,box-shadow] duration-150',
  'focus-visible:border-[#FC1EAD] focus-visible:ring-[3px] focus-visible:ring-[#FC1EAD]/20',
  'focus-visible:ring-offset-0 focus-visible:shadow-[0_0_12px_rgba(252,30,173,0.18)]'
)

function maskBirthDate(value: string) {
  const digits = value.replace(/\D/g, '').slice(0, 8)
  const day = digits.slice(0, 2)
  const month = digits.slice(2, 4)
  const year = digits.slice(4, 8)

  if (digits.length <= 2) return day
  if (digits.length <= 4) return `${day}/${month}`
  return `${day}/${month}/${year}`
}

function parseBirthDate(value: string) {
  const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(value)
  if (!match) return null

  const day = Number(match[1])
  const month = Number(match[2])
  const year = Number(match[3])
  const leapYear = (year % 4 === 0 && year % 100 !== 0) || year % 400 === 0
  const daysInMonth = [31, leapYear ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]

  if (year < 1900 || month < 1 || month > 12 || day < 1 || day > daysInMonth[month - 1]) {
    return null
  }

  return { day, month, year }
}

function isAdultBirthDate(value: string) {
  const birth = parseBirthDate(value)
  if (!birth) return false

  const today = new Date()
  let age = today.getFullYear() - birth.year
  const currentMonth = today.getMonth() + 1

  if (currentMonth < birth.month || (currentMonth === birth.month && today.getDate() < birth.day)) {
    age -= 1
  }

  return age >= 18
}

function birthDateToIso(value: string) {
  const birth = parseBirthDate(value)
  if (!birth) return null

  return `${birth.year}-${String(birth.month).padStart(2, '0')}-${String(birth.day).padStart(2, '0')}`
}

function InlineError({ message }: { message?: string }) {
  return (
    <div
      className={cn(
        'grid transition-[grid-template-rows,opacity,margin] duration-150',
        message ? 'mt-1 grid-rows-[1fr] opacity-100' : 'mt-0 grid-rows-[0fr] opacity-0'
      )}
      aria-live="polite"
    >
      <p className="flex min-h-0 items-center gap-1 overflow-hidden text-xs text-red-600">
        <ExclamationTriangleIcon className="h-4 w-4 shrink-0" />
        <span>{message || '\u00a0'}</span>
      </p>
    </div>
  )
}

export function RegisterForm({ refId, onSuccess, onBackToLogin, className }: RegisterFormProps) {
  const [values, setValues] = useState<RegisterValues>(INITIAL_VALUES)
  const [terms, setTerms] = useState<RegisterTerms>(INITIAL_TERMS)
  const [duplicateErrors, setDuplicateErrors] = useState<Partial<Record<DuplicateField, string>>>({})
  const [credencialEmFoco, setCredencialEmFoco] = useState(false)
  const [credencialTocada, setCredencialTocada] = useState(false)
  const [loading, setLoading] = useState(false)
  const credencialInputRef = useRef<HTMLInputElement>(null)
  const duplicateRequests = useRef<Partial<Record<DuplicateField, AbortController>>>({})

  useEffect(() => {
    const requests = duplicateRequests.current
    return () => Object.values(requests).forEach((controller) => controller?.abort())
  }, [])

  const credencialOk = passwordMeetsPolicy(values.credencial)
  const emailValid = validateEmail(values.email)
  const phoneClean = values.phone.replace(/\D/g, '')
  const birthDateComplete = values.dataNascimento.length === 10
  const birthDateValid = parseBirthDate(values.dataNascimento) !== null
  const adultBirthDate = birthDateValid && isAdultBirthDate(values.dataNascimento)
  const credenciaisDiferentes =
    values.confirmacaoCredencial.length > 0 && values.credencial !== values.confirmacaoCredencial
  const missingTerms = !terms.uso || !terms.privacidade

  const allFieldsValid =
    values.username.trim().length >= 3 &&
    phoneClean.length >= 10 &&
    emailValid &&
    birthDateComplete &&
    birthDateValid &&
    adultBirthDate &&
    credencialOk &&
    values.credencial === values.confirmacaoCredencial &&
    terms.uso &&
    terms.privacidade &&
    !duplicateErrors.email &&
    !duplicateErrors.username &&
    !duplicateErrors.telefone

  const regrasCredencialPendentes = pendingPasswordRequirements(values.credencial)
  const mostrarAvisoCredencial =
    credencialTocada && !credencialEmFoco && values.credencial.trim().length > 0 && !credencialOk

  const updateValue = (field: keyof RegisterValues, value: string) => {
    setValues((current) => ({ ...current, [field]: value }))

    const duplicateField = field === 'phone' ? 'telefone' : field
    if (duplicateField === 'email' || duplicateField === 'username' || duplicateField === 'telefone') {
      setDuplicateErrors((current) => ({ ...current, [duplicateField]: undefined }))
    }
  }

  const duplicateMessage = (field: DuplicateField) => {
    if (field === 'email') return 'Este e-mail ja esta em uso.'
    if (field === 'username') return 'Este username ja esta em uso.'
    return 'Este telefone ja esta cadastrado.'
  }

  const isDuplicate = (field: DuplicateField, response: DuplicidadeResposta) => {
    if (field === 'email') return response.emailExistente
    if (field === 'username') return response.usernameExistente
    return response.telefoneExistente
  }

  const verifyDuplicate = async (field: DuplicateField, value: string) => {
    duplicateRequests.current[field]?.abort()
    const controller = new AbortController()
    duplicateRequests.current[field] = controller

    try {
      const response = await checkDuplicidade({ [field]: value }, controller.signal)
      if (controller.signal.aborted) return
      setDuplicateErrors((current) => ({
        ...current,
        [field]: isDuplicate(field, response) ? duplicateMessage(field) : undefined,
      }))
    } catch (error) {
      if (controller.signal.aborted || (error instanceof DOMException && error.name === 'AbortError')) return
      toast.error('Não foi possível verificar os dados informados.')
    }
  }

  const getButtonLabel = () => {
    if (loading) return 'Criando conta...'
    if (credencialTocada && values.credencial.trim().length > 0 && !credencialOk) return 'Ajuste sua senha para continuar'
    if (!values.username.trim() || values.username.trim().length < 3) return 'Preencha o nome de usuario'
    if (phoneClean.length < 10) return 'Preencha o telefone'
    if (!values.email.trim()) return 'Preencha o e-mail'
    if (duplicateErrors.email || (values.email && !emailValid)) return 'Corrija seu e-mail'
    if (duplicateErrors.username) return 'Troque o nome de usuario'
    if (duplicateErrors.telefone) return 'Troque o telefone'
    if (!birthDateComplete) return 'Preencha a data de nascimento'
    if (!birthDateValid) return 'Corrija a data de nascimento'
    if (!adultBirthDate) return 'Cadastro permitido apenas para maiores de 18 anos'
    if (!values.credencial.trim()) return 'Preencha a senha'
    if (!values.confirmacaoCredencial.trim()) return 'Confirme a senha'
    if (credenciaisDiferentes) return 'Confirme a senha corretamente'
    if (missingTerms) return 'Aceite os termos para continuar'
    return 'Criar conta'
  }

  const handleRegister = async () => {
    if (loading) return
    if (!allFieldsValid) {
      toast.warning('Preencha todos os campos corretamente')
      return
    }

    try {
      setLoading(true)
      const duplicateResponse = await checkDuplicidade({
        email: values.email,
        username: values.username,
        telefone: values.phone,
      })
      const nextDuplicateErrors: Partial<Record<DuplicateField, string>> = {}

      for (const field of ['email', 'username', 'telefone'] as const) {
        if (isDuplicate(field, duplicateResponse)) nextDuplicateErrors[field] = duplicateMessage(field)
      }

      if (Object.keys(nextDuplicateErrors).length > 0) {
        setDuplicateErrors(nextDuplicateErrors)
        toast.error('Ha dados ja cadastrados. Corrija para continuar.')
        return
      }

      const dataNascimento = birthDateToIso(values.dataNascimento)
      if (!dataNascimento) {
        toast.error('Data de nascimento invalida.')
        return
      }

      const documentos = await fetchLegalDocuments()
      const originPath =
        typeof window === 'undefined' ? '/' : `${window.location.pathname}${window.location.search}`
      const result = await submitRegister({
        username: values.username.trim(),
        email: values.email.trim().toLowerCase(),
        telefone: phoneClean,
        dataNascimento,
        credencial: values.credencial,
        credencialConfirmacao: values.confirmacaoCredencial,
        refId: refId || undefined,
        acceptedTermsOfUse: terms.uso,
        acceptedPrivacyPolicy: terms.privacidade,
        acceptedPromotionalEmails: terms.promo,
        documentos,
        submitSource: 'CADASTRO_MODAL',
        originPath,
      })

      if (!result.ok) {
        toast.error(result.message)
        return
      }

      toast.success('Conta criada com sucesso!')
      onSuccess?.(values.email.trim().toLowerCase())
    } catch (error) {
      toast.error(normalizeApiError(error).message)
    } finally {
      setLoading(false)
    }
  }

  const birthDateError =
    values.dataNascimento.length > 0 && !birthDateValid
      ? 'Data de nascimento invalida.'
      : birthDateValid && !adultBirthDate
        ? 'Cadastro permitido apenas para maiores de 18 anos.'
        : undefined
  const emailError = duplicateErrors.email || (values.email && !emailValid ? 'E-mail invalido.' : undefined)

  return (
    <div className={cn('w-full', className)}>
      <div className="flex flex-col items-center gap-2 text-center">
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
        <p className="text-sm text-gray-600">Crie sua conta para comecar</p>
      </div>

      <div className="mt-4 space-y-4">
        <div className="relative">
          <UserIcon className="absolute left-3 top-2.5 h-5 w-5 text-gray-400" />
          <Input
            type="text"
            autoComplete="username"
            placeholder="Nome de usuario (NOME VISIVEL NA PLATAFORMA)"
            value={values.username}
            onChange={(event) => updateValue('username', event.target.value)}
            onBlur={() => {
              if (values.username.trim().length >= 3) void verifyDuplicate('username', values.username)
            }}
            className={cn(REGISTER_INPUT_CLASS, 'pl-10 py-5', duplicateErrors.username && 'border-red-400')}
          />
          <InlineError message={duplicateErrors.username} />
        </div>

        <div className="relative">
          <CalendarDaysIcon className="absolute left-3 top-2.5 h-5 w-5 text-gray-400" />
          <Input
            type="text"
            inputMode="numeric"
            autoComplete="bday"
            maxLength={10}
            placeholder="DD/MM/AAAA"
            value={values.dataNascimento}
            onChange={(event) => updateValue('dataNascimento', maskBirthDate(event.target.value))}
            className={cn(
              REGISTER_INPUT_CLASS,
              'register-date-input h-9 min-h-9 pl-10 py-1 leading-5',
              birthDateError && 'border-red-400'
            )}
          />
          <InlineError message={birthDateError} />
        </div>

        <div className="relative">
          <PhoneIcon className="absolute left-3 top-2.5 h-5 w-5 text-gray-400" />
          <Input
            type="tel"
            inputMode="tel"
            autoComplete="tel"
            placeholder="Telefone"
            value={values.phone}
            onChange={(event) => updateValue('phone', formatPhone(event.target.value))}
            onBlur={() => {
              if (phoneClean.length >= 10) void verifyDuplicate('telefone', values.phone)
            }}
            maxLength={15}
            className={cn(REGISTER_INPUT_CLASS, 'pl-10 py-5', duplicateErrors.telefone && 'border-red-400')}
          />
          <InlineError message={duplicateErrors.telefone} />
        </div>

        <div className="relative">
          <EnvelopeIcon className="absolute left-3 top-2.5 h-5 w-5 text-gray-400" />
          <Input
            type="email"
            inputMode="email"
            autoComplete="email"
            placeholder="Seu e-mail"
            value={values.email}
            onChange={(event) => updateValue('email', event.target.value)}
            onBlur={() => {
              if (emailValid) void verifyDuplicate('email', values.email)
            }}
            className={cn(REGISTER_INPUT_CLASS, 'pl-10 py-5', emailError && 'border-red-400')}
          />
          <InlineError message={emailError} />
        </div>

        <PasswordInput
          inputRef={credencialInputRef}
          value={values.credencial}
          onChange={(value) => updateValue('credencial', value)}
          placeholder="Senha"
          visibilityContext="senha"
          onFocus={() => {
            setCredencialEmFoco(true)
            setCredencialTocada(true)
          }}
          onBlur={() => setCredencialEmFoco(false)}
          className={REGISTER_INPUT_CLASS}
        />

        <div
          className={cn(
            'grid transition-[grid-template-rows,opacity] duration-150',
            mostrarAvisoCredencial ? 'grid-rows-[1fr] opacity-100' : 'grid-rows-[0fr] opacity-0'
          )}
          aria-hidden={!mostrarAvisoCredencial}
        >
          <div className="min-h-0 overflow-hidden">
            <div className="flex gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900">
              <ExclamationTriangleIcon className="mt-0.5 h-4 w-4 shrink-0" />
              <div className="flex-1">
                <p className="font-semibold">Senha ainda nao atende aos requisitos.</p>
                <p className="mt-0.5 text-amber-800">
                  Falta:{' '}
                  <span className="font-medium">
                    {regrasCredencialPendentes.slice(0, 3).join(', ')}
                    {regrasCredencialPendentes.length > 3 ? '...' : ''}
                  </span>
                </p>
                <button
                  type="button"
                  onClick={() => credencialInputRef.current?.focus()}
                  className="mt-1 font-medium underline hover:opacity-80"
                >
                  Ver requisitos
                </button>
              </div>
            </div>
          </div>
        </div>

        <div
          className={cn(
            'grid transition-[grid-template-rows,opacity] duration-150',
            credencialEmFoco ? 'grid-rows-[1fr] opacity-100' : 'grid-rows-[0fr] opacity-0'
          )}
          aria-hidden={!credencialEmFoco}
        >
          <PasswordRequirements value={values.credencial} className="min-h-0 overflow-hidden" />
        </div>

        <div className="mt-2">
          <PasswordInput
            value={values.confirmacaoCredencial}
            onChange={(value) => updateValue('confirmacaoCredencial', value)}
            placeholder="Confirmar senha"
            visibilityContext="confirmação da senha"
            invalid={credenciaisDiferentes}
            className={REGISTER_INPUT_CLASS}
          />
          <InlineError message={credenciaisDiferentes ? 'As senhas não coincidem.' : undefined} />
        </div>

        <div className="mt-4 space-y-2 text-xs">
          <label className="flex items-center gap-1">
            <Checkbox checked={terms.uso} onCheckedChange={(checked) => setTerms((current) => ({ ...current, uso: !!checked }))} />
            Estou de acordo com os
            <a href="/termos-de-uso" target="_blank" rel="noreferrer" className="cursor-pointer font-semibold text-[#FC1EAD] underline">
              {' '}Termos de Uso
            </a>
          </label>
          <label className="flex items-center gap-1">
            <Checkbox checked={terms.privacidade} onCheckedChange={(checked) => setTerms((current) => ({ ...current, privacidade: !!checked }))} />
            Estou de acordo com as
            <a href="/politica-de-privacidade" target="_blank" rel="noreferrer" className="cursor-pointer font-semibold text-[#FC1EAD] underline">
              {' '}Politicas de Privacidade
            </a>
          </label>
          <label className="flex items-center gap-2">
            <Checkbox checked={terms.promo} onCheckedChange={(checked) => setTerms((current) => ({ ...current, promo: !!checked }))} />
            Receber e-mails promocionais.
          </label>
        </div>
      </div>

      <Button
        type="button"
        onClick={() => void handleRegister()}
        disabled={!allFieldsValid || loading}
        className={cn(
          'mt-6 w-full py-5 font-semibold text-white',
          allFieldsValid ? 'bg-[#FC1EAD] hover:bg-[#e01a9a]' : 'cursor-not-allowed bg-gray-300'
        )}
      >
        {getButtonLabel()}
      </Button>

      {onBackToLogin && (
        <p className="mt-4 text-center text-sm text-gray-600">
          Ja tem uma conta?{' '}
          <button onClick={onBackToLogin} className="font-medium text-[#FC1EAD] transition hover:underline">
            Entrar
          </button>
        </p>
      )}
    </div>
  )
}
