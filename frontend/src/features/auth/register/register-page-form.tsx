'use client'

import { useRef, useState } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import Image from 'next/image'
import { toast } from 'sonner'
import { getPublicLogoUrl } from '@/lib/public-site-assets'
import styles from './register.module.css'
import {
  PASSWORD_HINT,
  birthDateBRToIso,
  getSafeNext,
  isAdultBirthDateBR,
  isPasswordValid,
  isPhoneComplete,
  isValidBirthDateBR,
  maskBirthDateBR,
  maskPhoneBR,
  passwordsMatch,
  validateEmail,
} from './register-validation'
import { checkDuplicidade, fetchLegalDocuments, submitRegister } from './register-api'

type FieldErrors = {
  email?: string
  username?: string
  telefone?: string
}

export function RegisterPageForm() {
  const router = useRouter()
  const params = useSearchParams()

  const refParam = params.get('ref')
  const refId = refParam ? Number(refParam) : undefined
  const next = getSafeNext(params.get('next'))
  const loginHref = next ? `/?login=1&next=${encodeURIComponent(next)}` : '/?login=1'

  const [currentStep, setCurrentStep] = useState<1 | 2>(1)

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')

  const [username, setUsername] = useState('')
  const [dataNascimento, setDataNascimento] = useState('')
  const [phone, setPhone] = useState('')
  const [termsUso, setTermsUso] = useState(false)
  const [termsPrivacidade, setTermsPrivacidade] = useState(false)
  const [termsPromo, setTermsPromo] = useState(true)

  const [touched, setTouched] = useState({
    email: false,
    passwordTouched: false,
    confirmPasswordTouched: false,
    username: false,
    dataNascimento: false,
    phone: false,
  })

  const [erros, setErros] = useState<FieldErrors>({})
  const [loading, setLoading] = useState(false)

  const abortRefs = useRef<{ email?: AbortController; username?: AbortController; telefone?: AbortController }>({})

  const markTouched = (field: keyof typeof touched) =>
    setTouched((prev) => ({ ...prev, [field]: true }))

  const emailValid = validateEmail(email)
  const passwordValid = isPasswordValid(password)
  const confirmValid = passwordsMatch(password, confirmPassword)
  const usernameValid = username.trim().length >= 3
  const phoneValid = isPhoneComplete(phone)
  const dataValida = isValidBirthDateBR(dataNascimento)
  const maioridadeOk = dataValida && isAdultBirthDateBR(dataNascimento)

  const step1Valid = emailValid && !erros.email && passwordValid && confirmValid
  const step2Valid =
    usernameValid && !erros.username && phoneValid && !erros.telefone && dataValida && maioridadeOk && termsUso && termsPrivacidade

  const emailErrorText = touched.email
    ? erros.email || (!emailValid && email.length > 0 ? 'E-mail inválido.' : undefined)
    : undefined
  const passwordErrorText =
    touched.passwordTouched && password.length > 0 && !passwordValid ? 'Senha não atende aos requisitos.' : undefined
  const confirmErrorText =
    touched.confirmPasswordTouched && confirmPassword.length > 0 && !confirmValid
      ? 'As senhas não coincidem.'
      : undefined
  const usernameErrorText = touched.username
    ? erros.username || (!usernameValid && username.length > 0 ? 'Use pelo menos 3 caracteres.' : undefined)
    : undefined
  const phoneErrorText = touched.phone
    ? erros.telefone || (!phoneValid && phone.length > 0 ? 'Telefone incompleto.' : undefined)
    : undefined
  const dataErrorText = touched.dataNascimento
    ? dataNascimento.length === 0
      ? undefined
      : !dataValida
        ? 'Data de nascimento inválida.'
        : !maioridadeOk
          ? 'Cadastro permitido apenas para maiores de 18 anos.'
          : undefined
    : undefined

  const runDuplicidadeCheck = async (field: 'email' | 'username' | 'telefone', value: string) => {
    abortRefs.current[field]?.abort()
    const controller = new AbortController()
    abortRefs.current[field] = controller

    const resposta = await checkDuplicidade({ [field]: value } as Record<string, string>, controller.signal)
    if (controller.signal.aborted) return

    const existente =
      field === 'email' ? resposta.emailExistente : field === 'username' ? resposta.usernameExistente : resposta.telefoneExistente

    setErros((prev) => ({
      ...prev,
      [field === 'telefone' ? 'telefone' : field]: existente
        ? field === 'email'
          ? 'Este e-mail já está em uso.'
          : field === 'username'
            ? 'Este username já está em uso.'
            : 'Este telefone já está cadastrado.'
        : undefined,
    }))
  }

  const handleBlurEmail = () => {
    markTouched('email')
    if (emailValid) void runDuplicidadeCheck('email', email)
  }

  const handleBlurUsername = () => {
    markTouched('username')
    if (usernameValid) void runDuplicidadeCheck('username', username)
  }

  const handleBlurPhone = () => {
    markTouched('phone')
    if (phoneValid) void runDuplicidadeCheck('telefone', phone)
  }

  const handleContinue = () => {
    setTouched((prev) => ({ ...prev, email: true, passwordTouched: true, confirmPasswordTouched: true }))

    if (!step1Valid) {
      toast.warning('Preencha e-mail, senha e confirmação corretamente.')
      return
    }

    setCurrentStep(2)
  }

  const handleBack = () => {
    setCurrentStep(1)
  }

  const handleRegister = async () => {
    setTouched((prev) => ({ ...prev, username: true, phone: true, dataNascimento: true }))

    if (!step1Valid || !step2Valid) {
      toast.warning('Preencha todos os campos corretamente.')
      return
    }

    const isoDataNascimento = birthDateBRToIso(dataNascimento)
    if (!isoDataNascimento) {
      toast.error('Data de nascimento inválida.')
      return
    }

    try {
      setLoading(true)

      const dup = await checkDuplicidade({ email, username, telefone: phone })
      const novosErros: FieldErrors = {}
      if (dup.emailExistente) novosErros.email = 'Este e-mail já está em uso.'
      if (dup.usernameExistente) novosErros.username = 'Este username já está em uso.'
      if (dup.telefoneExistente) novosErros.telefone = 'Este telefone já está cadastrado.'

      if (Object.keys(novosErros).length > 0) {
        setErros((prev) => ({ ...prev, ...novosErros }))
        toast.error('Há dados já cadastrados. Corrija para continuar.')
        return
      }

      const documentos = await fetchLegalDocuments()

      const originPath =
        typeof window !== 'undefined' ? `${window.location.pathname}${window.location.search}` : '/registrar'

      const resultado = await submitRegister({
        username: username.trim(),
        email: email.trim().toLowerCase(),
        telefone: phone.replace(/\D/g, ''),
        dataNascimento: isoDataNascimento,
        credencial: password,
        credencialConfirmacao: confirmPassword,
        refId,
        acceptedTermsOfUse: termsUso,
        acceptedPrivacyPolicy: termsPrivacidade,
        acceptedPromotionalEmails: termsPromo,
        documentos,
        submitSource: 'CADASTRO_PAGINA',
        originPath,
      })

      if (!resultado.ok) {
        toast.error(resultado.message)
        return
      }

      toast.success('Conta criada com sucesso!')
      router.push(next || '/')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className={styles.page}>
      <div className={styles.container}>
        <Image
          src={getPublicLogoUrl()}
          alt="Logo"
          width={150}
          height={50}
          priority
          unoptimized
          className={styles.logo}
        />
        <p className={styles.title}>Criar conta</p>
        <p className={styles.subtitle}>Crie sua conta para começar</p>
        <p className={styles.stepLabel}>{currentStep === 1 ? 'Etapa 1 de 2' : 'Etapa 2 de 2'}</p>

        {currentStep === 1 && (
          <div>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="register-email">
                E-mail
              </label>
              <input
                id="register-email"
                type="email"
                className={styles.registerField}
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                onBlur={handleBlurEmail}
              />
              <div className={styles.fieldErrorArea}>{emailErrorText}</div>
            </div>

            <div className={styles.field}>
              <label className={styles.label} htmlFor="register-password">
                Senha
              </label>
              <input
                id="register-password"
                type="password"
                className={styles.registerField}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                onBlur={() => markTouched('passwordTouched')}
              />
              <p className={styles.hint}>{PASSWORD_HINT}</p>
              <div className={styles.fieldErrorArea}>{passwordErrorText}</div>
            </div>

            <div className={styles.field}>
              <label className={styles.label} htmlFor="register-confirm-password">
                Confirmar senha
              </label>
              <input
                id="register-confirm-password"
                type="password"
                className={styles.registerField}
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                onBlur={() => markTouched('confirmPasswordTouched')}
              />
              <div className={styles.fieldErrorArea}>{confirmErrorText}</div>
            </div>

            <div className={styles.actions}>
              <button type="button" className={styles.buttonPrimary} onClick={handleContinue}>
                Continuar
              </button>
            </div>

            <p className={styles.footerText}>
              Já tem uma conta?{' '}
              <button
                type="button"
                className={styles.link}
                style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}
                onClick={() => router.push(loginHref)}
              >
                Entrar
              </button>
            </p>
          </div>
        )}

        {currentStep === 2 && (
          <div>
            <button type="button" className={styles.buttonSecondary} onClick={handleBack}>
              Voltar
            </button>

            <div className={styles.field}>
              <label className={styles.label} htmlFor="register-username">
                Nome de usuário
              </label>
              <input
                id="register-username"
                type="text"
                className={styles.registerField}
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                onBlur={handleBlurUsername}
              />
              <div className={styles.fieldErrorArea}>{usernameErrorText}</div>
            </div>

            <div className={styles.field}>
              <label className={styles.label} htmlFor="register-birth-date">
                Data de nascimento
              </label>
              <input
                id="register-birth-date"
                type="text"
                inputMode="numeric"
                placeholder="DD/MM/AAAA"
                maxLength={10}
                autoComplete="bday"
                className={styles.registerField}
                value={dataNascimento}
                onChange={(e) => setDataNascimento(maskBirthDateBR(e.target.value))}
                onBlur={() => markTouched('dataNascimento')}
              />
              <div className={styles.fieldErrorArea}>{dataErrorText}</div>
            </div>

            <div className={styles.field}>
              <label className={styles.label} htmlFor="register-phone">
                Telefone
              </label>
              <input
                id="register-phone"
                type="tel"
                maxLength={15}
                className={styles.registerField}
                value={phone}
                onChange={(e) => setPhone(maskPhoneBR(e.target.value))}
                onBlur={handleBlurPhone}
              />
              <div className={styles.fieldErrorArea}>{phoneErrorText}</div>
            </div>

            <label className={styles.checkboxRow}>
              <input type="checkbox" checked={termsUso} onChange={(e) => setTermsUso(e.target.checked)} />
              <span>
                Estou de acordo com os{' '}
                <a href="/termos-de-uso" target="_blank" rel="noreferrer" className={styles.link}>
                  Termos de Uso
                </a>
              </span>
            </label>

            <label className={styles.checkboxRow}>
              <input
                type="checkbox"
                checked={termsPrivacidade}
                onChange={(e) => setTermsPrivacidade(e.target.checked)}
              />
              <span>
                Estou de acordo com a{' '}
                <a href="/politica-de-privacidade" target="_blank" rel="noreferrer" className={styles.link}>
                  Política de Privacidade
                </a>
              </span>
            </label>

            <label className={styles.checkboxRow}>
              <input type="checkbox" checked={termsPromo} onChange={(e) => setTermsPromo(e.target.checked)} />
              <span>Receber e-mails promocionais.</span>
            </label>

            <div className={styles.actions}>
              <button
                type="button"
                className={styles.buttonPrimary}
                disabled={loading}
                onClick={() => void handleRegister()}
              >
                {loading ? 'Criando conta...' : 'Criar conta'}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
