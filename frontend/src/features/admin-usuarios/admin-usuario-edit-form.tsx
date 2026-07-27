'use client'

import Link from 'next/link'
import { useCallback, useEffect, useState } from 'react'
import { CheckCircle2, Loader2, Save } from 'lucide-react'

import { ContractState } from '@/components/feedback/contract-state'
import { MaskedPhoneInput } from '@/components/forms/masked-phone-input'
import { Button } from '@/components/ui/button'
import { getAdminSession } from '@/lib/admin-auth-api'
import { maskPhoneBR, phoneToE164BR } from '@/lib/phone-mask'

import { getAdminUser, updateAdminUserPhone } from './api'
import type { AdminUserDetail } from './types'

export function AdminUsuarioEditForm({ usuarioId }: { usuarioId: string }) {
  const [detail, setDetail] = useState<AdminUserDetail | null>(null)
  const [telefone, setTelefone] = useState('')
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
        throw new Error('Seu perfil não possui permissão para editar dados cadastrais.')
      }
      setDetail(user)
      setTelefone(maskPhoneBR(user.telefone || ''))
    } catch (reason) {
      setError(reason)
    } finally {
      setLoading(false)
    }
  }, [usuarioId])

  useEffect(() => { void load() }, [load])

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    if (!detail || saving) return
    const normalized = phoneToE164BR(telefone)
    if (!normalized) {
      setError(new Error('Informe DDD e número completo antes de salvar.'))
      return
    }
    setSaving(true)
    setSaved(false)
    setError(null)
    try {
      const updated = await updateAdminUserPhone(detail.id, normalized)
      setDetail(updated)
      setTelefone(maskPhoneBR(updated.telefone || normalized))
      setSaved(true)
    } catch (reason) {
      setError(reason)
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return <p className="flex items-center justify-center gap-2 py-16 text-sm text-zinc-500"><Loader2 className="h-4 w-4 animate-spin" />Carregando usuário...</p>
  }
  if (error && !detail) return <ContractState error={error} onRetry={() => void load()} />
  if (!detail) return null

  return (
    <form onSubmit={submit} className="mx-auto max-w-3xl space-y-6">
      <header className="flex flex-col gap-3 border-b border-zinc-200 pb-5 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-sm font-semibold text-pink-700">Gestão administrativa</p>
          <h1 className="mt-1 text-2xl font-bold text-zinc-950">Editar telefone do usuário</h1>
          <p className="mt-1 text-sm text-zinc-600">A API recebe somente o número normalizado; a interface mantém a máscara brasileira.</p>
        </div>
        <Button asChild type="button" variant="outline">
          <Link href={`/admin/usuarios/${encodeURIComponent(detail.id)}`}>Voltar ao usuário</Link>
        </Button>
      </header>

      {error ? <ContractState error={error} compact /> : null}
      {saved ? (
        <p className="flex items-center gap-2 rounded-md border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-800">
          <CheckCircle2 className="h-4 w-4" /> Telefone atualizado e exibido com máscara.
        </p>
      ) : null}

      <section className="grid gap-4 border-y border-zinc-200 py-5 sm:grid-cols-2">
        <div><p className="text-xs font-semibold uppercase text-zinc-500">Nome</p><p className="mt-1 text-sm text-zinc-900">{detail.nomeCivil || detail.nome || 'Não informado'}</p></div>
        <div><p className="text-xs font-semibold uppercase text-zinc-500">E-mail</p><p className="mt-1 break-all text-sm text-zinc-900">{detail.email || 'Não informado'}</p></div>
        <div><p className="text-xs font-semibold uppercase text-zinc-500">CPF</p><p className="mt-1 text-sm text-zinc-900">{detail.cpf || 'Não informado'}</p></div>
        <div><p className="text-xs font-semibold uppercase text-zinc-500">Estado da conta</p><p className="mt-1 text-sm text-zinc-900">{detail.status}</p></div>
      </section>

      <label className="block max-w-md">
        <span className="mb-2 block text-sm font-semibold text-zinc-950">Telefone</span>
        <MaskedPhoneInput
          value={telefone}
          onValueChange={(value) => {
            setSaved(false)
            setTelefone(value)
          }}
          aria-describedby="admin-user-phone-help"
          placeholder="(00) 00000-0000"
          disabled={saving}
          required
        />
        <span id="admin-user-phone-help" className="mt-2 block text-xs text-zinc-500">
          Aceita telefone fixo ou celular com DDD.
        </span>
      </label>

      <div className="flex justify-end border-t border-zinc-200 pt-5">
        <Button type="submit" disabled={saving || !phoneToE164BR(telefone)}>
          {saving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Save className="mr-2 h-4 w-4" />}
          Salvar telefone
        </Button>
      </div>
    </form>
  )
}
