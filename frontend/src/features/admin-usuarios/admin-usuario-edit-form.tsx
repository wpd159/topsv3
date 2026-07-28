'use client'

import Link from 'next/link'
import { useCallback, useEffect, useState } from 'react'
import { CheckCircle2, Loader2, Save } from 'lucide-react'

import { ContractState } from '@/components/feedback/contract-state'
import { BirthDateField } from '@/components/forms/birth-date-field'
import { MaskedPhoneInput } from '@/components/forms/masked-phone-input'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { getAdminSession } from '@/lib/admin-auth-api'
import { cpfDigits, maskCpf } from '@/lib/cpf-mask'
import { birthDateToIso, isoToBirthDate } from '@/lib/date/birth-date'
import { maskPhoneBR, phoneToE164BR } from '@/lib/phone-mask'

import { AdminUserFormError, getAdminUser, updateAdminUser } from './api'
import type { AdminUserDetail } from './types'

type FormState = {
  nome: string
  nomeCivil: string
  email: string
  cpf: string
  telefone: string
  dataNascimento: string
}

const EMPTY_FORM: FormState = {
  nome: '',
  nomeCivil: '',
  email: '',
  cpf: '',
  telefone: '',
  dataNascimento: '',
}

function formFromDetail(detail: AdminUserDetail): FormState {
  return {
    nome: detail.nome || '',
    nomeCivil: detail.nomeCivil || '',
    email: detail.email || '',
    cpf: maskCpf(detail.cpf || ''),
    telefone: maskPhoneBR(detail.telefone || ''),
    dataNascimento: isoToBirthDate(detail.dataNascimento),
  }
}

export function AdminUsuarioEditForm({ usuarioId }: { usuarioId: string }) {
  const [detail, setDetail] = useState<AdminUserDetail | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState<unknown>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [user, session] = await Promise.all([getAdminUser(usuarioId), getAdminSession()])
      if (!session?.papeis.includes('ADMIN') || !session.permissoes.includes('ANUNCIO_MODERAR')) {
        throw new Error('Seu perfil nao possui permissao para editar dados cadastrais.')
      }
      setDetail(user)
      setForm(formFromDetail(user))
    } catch (reason) {
      setError(reason)
    } finally {
      setLoading(false)
    }
  }, [usuarioId])

  useEffect(() => { void load() }, [load])

  function updateField(field: keyof FormState, value: string) {
    setSaved(false)
    setFieldErrors((current) => {
      const next = { ...current }
      delete next[field]
      return next
    })
    setForm((current) => ({ ...current, [field]: value }))
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    if (!detail || saving) return
    const telefone = phoneToE164BR(form.telefone)
    const dataNascimento = birthDateToIso(form.dataNascimento)
    const cpf = cpfDigits(form.cpf)
    const errors: Record<string, string> = {}
    if (form.nome.trim().length < 2) errors.nome = 'Informe o nome de exibicao.'
    if (form.nomeCivil.trim().length < 3) errors.nomeCivil = 'Informe o nome civil.'
    if (!form.email.includes('@')) errors.email = 'Informe um e-mail valido.'
    if (cpf.length !== 11) errors.cpf = 'Informe os onze digitos do CPF.'
    if (!telefone) errors.telefone = 'Informe DDD e numero completo.'
    if (!dataNascimento) errors.dataNascimento = 'Informe a data no formato DD/MM/AAAA.'
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors)
      return
    }

    setSaving(true)
    setSaved(false)
    setError(null)
    setFieldErrors({})
    try {
      const updated = await updateAdminUser(detail.id, {
        versao: detail.versao,
        nome: form.nome.trim(),
        nomeCivil: form.nomeCivil.trim(),
        email: form.email.trim(),
        cpf,
        telefone: telefone as string,
        dataNascimento: dataNascimento as string,
      })
      setDetail(updated)
      setForm(formFromDetail(updated))
      setSaved(true)
    } catch (reason) {
      if (reason instanceof AdminUserFormError) {
        setFieldErrors(reason.fieldErrors)
      }
      setError(reason)
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return <p className="flex items-center justify-center gap-2 py-16 text-sm text-zinc-500"><Loader2 className="h-4 w-4 animate-spin" />Carregando usuario...</p>
  }
  if (error && !detail) return <ContractState error={error} onRetry={() => void load()} />
  if (!detail) return null

  return (
    <form onSubmit={submit} className="mx-auto max-w-4xl space-y-6">
      <header className="flex flex-col gap-3 border-b border-zinc-200 pb-5 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-sm font-semibold text-pink-700">Gestao administrativa</p>
          <h1 className="mt-1 text-2xl font-bold text-zinc-950">Editar usuario</h1>
          <p className="mt-1 text-sm text-zinc-600">Dados cadastrais canônicos da conta.</p>
        </div>
        <Button asChild type="button" variant="outline">
          <Link href={`/admin/usuarios/${encodeURIComponent(detail.id)}`}>Voltar ao usuario</Link>
        </Button>
      </header>

      {error ? <ContractState error={error} compact /> : null}
      {saved ? (
        <p className="flex items-center gap-2 rounded-md border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-800">
          <CheckCircle2 className="h-4 w-4" /> Dados cadastrais atualizados.
        </p>
      ) : null}

      <section className="space-y-4">
        <h2 className="text-lg font-semibold text-zinc-950">Identificacao</h2>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Nome de exibicao" error={fieldErrors.nome}>
            <Input
              value={form.nome}
              onChange={(event) => updateField('nome', event.target.value)}
              maxLength={120}
              disabled={saving}
            />
          </Field>
          <Field label="Nome civil" error={fieldErrors.nomeCivil}>
            <Input
              value={form.nomeCivil}
              onChange={(event) => updateField('nomeCivil', event.target.value)}
              maxLength={180}
              disabled={saving}
            />
          </Field>
          <Field label="CPF" error={fieldErrors.cpf}>
            <Input
              value={form.cpf}
              onChange={(event) => updateField('cpf', maskCpf(event.target.value))}
              inputMode="numeric"
              maxLength={14}
              disabled={saving}
            />
          </Field>
          <Field label="Data de nascimento" error={fieldErrors.dataNascimento}>
            <BirthDateField
              value={form.dataNascimento}
              onValueChange={(value) => updateField('dataNascimento', value)}
              disabled={saving}
            />
          </Field>
        </div>
      </section>

      <section className="space-y-4 border-t border-zinc-200 pt-5">
        <h2 className="text-lg font-semibold text-zinc-950">Contato</h2>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="E-mail" error={fieldErrors.email}>
            <Input
              type="email"
              value={form.email}
              onChange={(event) => updateField('email', event.target.value)}
              maxLength={254}
              disabled={saving}
            />
          </Field>
          <Field label="Telefone" error={fieldErrors.telefone}>
            <MaskedPhoneInput
              value={form.telefone}
              onValueChange={(value) => updateField('telefone', value)}
              placeholder="(00) 00000-0000"
              disabled={saving}
            />
          </Field>
        </div>
      </section>

      <div className="flex justify-end border-t border-zinc-200 pt-5">
        <Button type="submit" disabled={saving}>
          {saving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Save className="mr-2 h-4 w-4" />}
          Salvar dados
        </Button>
      </div>
    </form>
  )
}

function Field({
  label,
  error,
  children,
}: {
  label: string
  error?: string
  children: React.ReactNode
}) {
  return (
    <div className="space-y-2">
      <Label>{label}</Label>
      {children}
      {error ? <p className="text-xs font-medium text-red-700">{error}</p> : null}
    </div>
  )
}
